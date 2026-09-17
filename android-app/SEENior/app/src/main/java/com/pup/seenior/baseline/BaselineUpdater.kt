package com.pup.seenior.baseline

import com.pup.seenior.database.dao.BaselineDao
import com.pup.seenior.database.dao.DailyAggregateDao
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.SeniorOnboarding
import com.pup.seenior.detection.AggregateFeatures
import com.pup.seenior.detection.MedianMad

/**
 * Rolls the last 14 days of [com.pup.seenior.database.entities.DailyAggregate] rows into the
 * Routine Fingerprint, blending them against the onboarding seed values as they accumulate.
 *
 * **Blend, not swap.** CLAUDE.md §6 describes real data *progressively* replacing the seed from
 * Day 1 and fully replacing it by Day 14, and that word is doing real work. The previous version
 * swapped outright at three days, which handed detection medians and MADs computed from three
 * partial days of a senior who happened to be holding her phone the whole time. One block came
 * out `median = 0, MAD = 0` — "her evenings never vary, and her screen is never idle" — after
 * which any idle screen at all was infinitely far from normal. A blend cannot produce that,
 * because the seed's deliberately wide MAD is still most of the answer until the real data has
 * earned its place.
 */
object BaselineUpdater {
    private const val ROLLING_WINDOWS_DAYS = 14

    /**
     * Below this many daily samples the real median/MAD is not merely uncertain but arbitrary —
     * two days can only ever produce a MAD of "half the gap between them". The blend weight
     * would be small enough for it to barely matter, but there is no reason to write the row.
     */
    private const val MIN_SAMPLES_TO_BLEND = 3

    /**
     * How much of the trailing window must be trustworthy before it is allowed to move the
     * fingerprint at all.
     *
     * The hand-over weight is taken from days lived through, so without this a window where
     * thirteen of fourteen days were thin would hand over almost fully on the strength of the
     * one that was not -- the flat-baseline bug this object's KDoc exists to describe, arriving
     * by a new road. Half is where the surviving days are still the majority of what is claimed.
     */
    private const val MIN_USABLE_FRACTION = 0.5

    private val TIME_BLOCKS = listOf("morning", "afternoon", "evening", "night")

    suspend fun updateForSenior(
        seniorId: Int,
        onboarding: SeniorOnboarding,
        baselineDao: BaselineDao,
        dailyAggregateDao: DailyAggregateDao
    ) {
        // Regenerated rather than read back from the table: the stored rows are already blended,
        // so blending against them again would compound and let the seed decay geometrically
        // instead of linearly. The seeds are a pure function of the onboarding answers, so
        // rebuilding them here gives the same values every night.
        val seeds = SeedBaselineGenerator.generate(seniorId, onboarding)
            .associateBy { it.featureName to it.timeBlock }

        for (timeBlock in TIME_BLOCKS) {
            /*
             * Two different counts, and keeping them apart is the whole of this block.
             *
             * `window` is how many days of this block the app has actually lived through -- how
             * far along the §6 hand-over it is. `rows` is what it can honestly say about them,
             * with the thin blocks dropped by the same rule Layer 2 already applies (see
             * [AggregateFeatures.isUsable] for the measurement that motivated sharing it).
             *
             * Filtered here rather than in the query because "the trailing 14 days" and "the last
             * 14 trustworthy days" are different windows, and the first is the one this baseline
             * describes. Reaching further back to make up the numbers would quietly widen the
             * window past a fortnight on exactly the devices that can least afford it.
             */
            val window = dailyAggregateDao.getRecentByTimeBlock(seniorId, timeBlock, ROLLING_WINDOWS_DAYS)
            val rows = window.filter { AggregateFeatures.isUsable(it, onboarding) }

            /*
             * Below either guard the stored baseline is left exactly as it is, rather than
             * rewritten from what little survived the filter. That is what makes the filter safe
             * to add at all: it can withhold an update, but it can never replace a good
             * fingerprint with a thin one. A handset that goes dark for a week keeps yesterday's
             * until it has enough real days to earn a new one.
             */
            if (rows.size < MIN_SAMPLES_TO_BLEND) continue
            if (rows.size < window.size * MIN_USABLE_FRACTION) continue

            suspend fun update(featureName: String, values: List<Double>) =
                updateFeature(seniorId, timeBlock, featureName, values, window.size, seeds, baselineDao)

            update("movement_score", rows.map { it.avgMovementScore })
            update("inactivity_duration", rows.map { it.totalInactivityDuration.toDouble() })
            update("screen_idle_duration", rows.map { it.avgScreenIdleDuration.toDouble() })
            update("screen_unlock_count", rows.map { it.totalScreenUnlocks.toDouble() })
            update("step_count", rows.map { it.totalSteps.toDouble() })
        }
    }

    private suspend fun updateFeature(
        seniorId: Int,
        timeBlock: String,
        featureName: String,
        values: List<Double>,
        windowDays: Int,
        seeds: Map<Pair<String, String>, Baseline>,
        baselineDao: BaselineDao
    ) {
        val seed = seeds[featureName to timeBlock] ?: return

        val realMedian = MedianMad.median(values)
        val realMad = MedianMad.mad(values, realMedian)

        /*
         * Linear hand-over: one day of data is worth 1/14th of the answer, fourteen days is worth
         * all of it. This is the "Day 1 to Day 14" transition of §6, made literal.
         *
         * Weighted on [windowDays] -- days lived through -- and NOT on `values.size`, the days
         * that survived the usability filter. The distinction looks pedantic and is not.
         *
         * Weighting on the filtered count was tried first and is actively unsafe. Excluding a
         * block lowers the weight, which hands the seed *more* of the answer, and the night seed
         * is deliberately enormous: for this senior, median 18,000 s with MAD 7,200, because a
         * night genuinely is one long stretch of stillness. Measured against the pilot's real
         * data on 2026-09-17: dropping her five frozen nights moved night inactivity to median
         * 10,859 / MAD 3,761, and since `clipToBlock` caps any reading at the block's own length,
         * the *most extreme night physically possible* then scores z = 1.90 -- under the 2.5
         * threshold. Layer 1 could never have raised a night inactivity alert for her again.
         * Excluding bad data must not be able to switch a detector off.
         *
         * Weighting on days lived through keeps the seed retreating on schedule while the medians
         * below are computed only from days worth believing: z = 9.38 on that same data.
         */
        val weight = (windowDays.toDouble() / ROLLING_WINDOWS_DAYS).coerceIn(0.0, 1.0)
        val median = seed.medianValue * (1.0 - weight) + realMedian * weight
        val blendedMad = seed.madValue * (1.0 - weight) + realMad * weight

        // Second guard, for the case the blend cannot cover: a senior whose readings genuinely
        // never vary still produces MAD 0 at day 14, and MAD is the divisor of the z-score.
        // The detector floors it at read time too; flooring it here as well means the stored
        // fingerprint says what detection will actually use.
        val madFloor = SeedBaselineGenerator.MIN_MAD_FLOOR[featureName] ?: 1.0

        baselineDao.replaceFeatureBaseline(
            Baseline(
                seniorId = seniorId,
                featureName = featureName,
                timeBlock = timeBlock,
                medianValue = median,
                madValue = maxOf(blendedMad, madFloor),
                // How many days this row's numbers were actually computed from, which after the
                // filter is no longer the same as how far the hand-over has got. The count that
                // describes the data is the useful one to keep.
                sampleCount = values.size,
                // Still partly the questionnaire's answer until the hand-over completes, and
                // `is_seed` is what Senior_Onboarding.seed_baseline_generated is tracking. Read
                // off the weight, so it always agrees with how much seed is really in the row.
                isSeed = weight < 1.0
            )
        )
    }
}
