package com.pup.seenior.baseline

import com.pup.seenior.database.dao.BaselineDao
import com.pup.seenior.database.dao.DailyAggregateDao
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.SeniorOnboarding
import com.pup.seenior.detection.AggregateFeatures
import com.pup.seenior.detection.MedianMad

/**
 * Rolls the last 14 days of [com.pup.seenior.database.entities.DailyAggregate] rows into the
 * Routine Fingerprint, blended with the onboarding seed values as real data accumulates.
 *
 * Blend, not swap: real data replaces the seed gradually, reaching full weight at day 14. An
 * earlier outright swap at three days once produced `median = 0, MAD = 0` for a block, which
 * made any idle screen infinitely far from normal. The seed's wide MAD keeps most of the
 * answer until real data has earned its place.
 */
object BaselineUpdater {
    private const val ROLLING_WINDOWS_DAYS = 14

    /** Below this many daily samples the real median/MAD is arbitrary, so the row isn't written. */
    private const val MIN_SAMPLES_TO_BLEND = 3

    /**
     * How much of the trailing window must be trustworthy before it may move the fingerprint.
     * The hand-over weight comes from days lived through, so without this a window where 13
     * of 14 days were thin would hand over almost fully on the strength of one good day.
     */
    private const val MIN_USABLE_FRACTION = 0.5

    private val TIME_BLOCKS = listOf("morning", "afternoon", "evening", "night")

    suspend fun updateForSenior(
        seniorId: Int,
        onboarding: SeniorOnboarding,
        baselineDao: BaselineDao,
        dailyAggregateDao: DailyAggregateDao
    ) {
        // Regenerated, not read back: stored rows are already blended, so blending against
        // them again would make the seed decay geometrically instead of linearly.
        val seeds = SeedBaselineGenerator.generate(seniorId, onboarding)
            .associateBy { it.featureName to it.timeBlock }

        for (timeBlock in TIME_BLOCKS) {
            /*
             * Two different counts: `window` is how many days of this block the app has lived
             * through (how far along the hand-over is), and `rows` is what it can honestly say
             * about them, with thin blocks dropped by the same rule Layer 2 uses (see
             * [AggregateFeatures.isUsable]). Filtered here, not in the query, because the
             * baseline describes the trailing 14 days, and reaching further back to make up
             * numbers would widen the window.
             */
            val window = dailyAggregateDao.getRecentByTimeBlock(seniorId, timeBlock, ROLLING_WINDOWS_DAYS)
            val rows = window.filter { AggregateFeatures.isUsable(it, onboarding) }

            /*
             * Below either guard the stored baseline is left as it is. The filter can withhold
             * an update but never replace a good fingerprint with a thin one.
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
         * Linear hand-over: one day of data is worth 1/14th of the answer, fourteen days all of it.
         *
         * Weighted on [windowDays] (days lived through), not `values.size` (days that survived
         * the filter). Weighting on the filtered count hands the seed more of the answer when a
         * block is excluded, and the night seed is very wide (median 18,000 s, MAD 7,200). On
         * the pilot's data, dropping five frozen nights moved night inactivity to median 10,859
         * / MAD 3,761, and since `clipToBlock` caps readings at the block length, the most
         * extreme possible night scored z = 1.90, under the 2.5 threshold. Excluding bad data
         * must not be able to switch a detector off. With days-lived weighting that same case
         * scores z = 9.38.
         */
        val weight = (windowDays.toDouble() / ROLLING_WINDOWS_DAYS).coerceIn(0.0, 1.0)
        val median = seed.medianValue * (1.0 - weight) + realMedian * weight
        val blendedMad = seed.madValue * (1.0 - weight) + realMad * weight

        // Second guard: readings that never vary still give MAD 0 at day 14, and MAD divides
        // the z-score. The detector floors it at read time too; flooring here makes the stored
        // fingerprint match what detection uses.
        val madFloor = SeedBaselineGenerator.MIN_MAD_FLOOR[featureName] ?: 1.0

        baselineDao.replaceFeatureBaseline(
            Baseline(
                seniorId = seniorId,
                featureName = featureName,
                timeBlock = timeBlock,
                medianValue = median,
                madValue = maxOf(blendedMad, madFloor),
                // How many days these numbers were computed from, which after filtering can
                // differ from how far the hand-over has got.
                sampleCount = values.size,
                // Still partly the questionnaire's answer until the hand-over completes.
                // Read off the weight so it matches how much seed is in the row.
                isSeed = weight < 1.0
            )
        )
    }
}
