package com.pup.seenior.baseline

import com.pup.seenior.database.dao.BaselineDao
import com.pup.seenior.database.dao.DailyAggregateDao
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.SeniorOnboarding
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
            val rows = dailyAggregateDao.getRecentByTimeBlock(seniorId, timeBlock, ROLLING_WINDOWS_DAYS)

            if (rows.size < MIN_SAMPLES_TO_BLEND) continue

            updateFeature(seniorId, timeBlock, "movement_score", rows.map { it.avgMovementScore }, seeds, baselineDao)
            updateFeature(seniorId, timeBlock, "inactivity_duration", rows.map { it.totalInactivityDuration.toDouble() }, seeds, baselineDao)
            updateFeature(seniorId, timeBlock, "screen_idle_duration", rows.map { it.avgScreenIdleDuration.toDouble() }, seeds, baselineDao)
            updateFeature(seniorId, timeBlock, "screen_unlock_count", rows.map { it.totalScreenUnlocks.toDouble() }, seeds, baselineDao)
            updateFeature(seniorId, timeBlock, "step_count", rows.map { it.totalSteps.toDouble() }, seeds, baselineDao)
        }
    }

    private suspend fun updateFeature(
        seniorId: Int,
        timeBlock: String,
        featureName: String,
        values: List<Double>,
        seeds: Map<Pair<String, String>, Baseline>,
        baselineDao: BaselineDao
    ) {
        val seed = seeds[featureName to timeBlock] ?: return

        val realMedian = MedianMad.median(values)
        val realMad = MedianMad.mad(values, realMedian)

        // Linear hand-over: one day of data is worth 1/14th of the answer, fourteen days is worth
        // all of it. This is the "Day 1 to Day 14" transition of §6, made literal.
        val weight = (values.size.toDouble() / ROLLING_WINDOWS_DAYS).coerceIn(0.0, 1.0)
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
                sampleCount = values.size,
                // Still partly the questionnaire's answer until the hand-over completes, and
                // `is_seed` is what Senior_Onboarding.seed_baseline_generated is tracking.
                isSeed = values.size < ROLLING_WINDOWS_DAYS
            )
        )
    }
}
