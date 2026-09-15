package com.pup.seenior.detection

import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.DailyAggregate

/**
 * Phase 2 of the Isolation Forest build (CLAUDE.md §5, Layer 2) — turns one real block-day into
 * the six-number [DoubleArray] that [IsolationForest] trains and scores on, and decides whether a
 * block-day is trustworthy enough to be used at all.
 *
 * This is the seam between "real data with meaning" and the plain numbers [IsolationForest]
 * works with. It is the only class in this pair that is allowed to know what a `DailyAggregate`
 * or a `Baseline` row is; [IsolationForest] and [IsolationTree] stay ignorant of seniors, blocks
 * and baselines on purpose, so they can be driven from invented arrays in a JUnit test with
 * nothing about Room involved.
 */
object AggregateFeatures {

    /**
     * The order every [DoubleArray] this object produces follows. [IsolationForest] itself does
     * not care what a dimension means — only [AggregateFeatures] and whatever later reads a score
     * back (Phase 5/6, or a debugging session) need to agree on it, so the order lives in exactly
     * one place instead of being an assumption repeated at every call site.
     */
    val FEATURE_NAMES = listOf(
        "movement_score",
        "inactivity_duration",
        "screen_idle_duration",
        "screen_unlock_count",
        "step_count",
        "is_charging"
    )

    /**
     * Builds the feature vector for [aggregate] from whichever rows of [allBaselines] belong to
     * the same senior and the same time block.
     *
     * Taking the *whole* baseline set rather than requiring the caller to pre-filter to one block
     * is deliberate: a senior's full `Baseline` table (20 rows — 5 features × 4 blocks) can be
     * fetched once per night and reused across every aggregate row being scored, with the
     * senior/block filtering done in one place, in here, rather than trusted to whichever call
     * site scores the next row. Filtering to the wrong block by accident is exactly the class of
     * mistake the earlier night-grouping and block-clipping pipeline bugs were.
     *
     * Each of the first five numbers is a **signed** Modified Z-Score — `(value - median) /
     * effectiveMad`, the same formula [MedianMad] uses for Layer 1, but *not* run through
     * [MedianMad.deviationsScore], which takes the absolute value. Losing the sign here would
     * collapse "three times her normal step count" and "barely moving at all" onto the same
     * number, and telling those two apart — an energetic day is not an emergency — is the hardest
     * of the Phase 4 test cases. The sixth number, `is_charging`, is plain 0.0/1.0; it is not a
     * z-score because there is no meaningful "how unusual" for a boolean.
     *
     * @throws IllegalArgumentException if a baseline row for one of the five features is missing
     *   for this senior and block. Onboarding seeds all five, for every block, before any real
     *   sensor data exists at all — a gap here means something upstream is broken, not that this
     *   row should quietly be skipped or scored against a stand-in of zero.
     */
    fun vector(aggregate: DailyAggregate, allBaselines: List<Baseline>): DoubleArray {
        val byFeature = allBaselines
            .filter { it.seniorId == aggregate.seniorId && it.timeBlock == aggregate.timeBlock }
            .associateBy { it.featureName }

        fun signedZ(featureName: String, value: Double): Double {
            val baseline = byFeature[featureName]
                ?: throw IllegalArgumentException(
                    "No '$featureName' baseline for senior ${aggregate.seniorId}, " +
                        "block '${aggregate.timeBlock}'"
                )
            val floor = SeedBaselineGenerator.MIN_MAD_FLOOR.getValue(featureName)
            val effectiveMad = baseline.madValue.coerceAtLeast(floor)
            return (value - baseline.medianValue) / effectiveMad
        }

        return doubleArrayOf(
            signedZ("movement_score", aggregate.avgMovementScore),
            signedZ("inactivity_duration", aggregate.totalInactivityDuration.toDouble()),
            signedZ("screen_idle_duration", aggregate.avgScreenIdleDuration.toDouble()),
            signedZ("screen_unlock_count", aggregate.totalScreenUnlocks.toDouble()),
            signedZ("step_count", aggregate.totalSteps.toDouble()),
            if (aggregate.isChargingMajority) 1.0 else 0.0
        )
    }

    /**
     * Whether [aggregate] was summarised from enough raw readings to trust, per the plan's fourth
     * design decision: a block-day is excluded from both training and scoring, never included and
     * merely down-weighted, once the raw rows behind it are already gone and there is no way back.
     *
     * A summary of four real readings is indistinguishable from a summary of fifty once
     * `Sensor_Data` is purged — see [DailyAggregate.sampleCount]'s own KDoc for why that column
     * exists at all. Feeding either kind of thin block to an unsupervised model teaches it that
     * near-empty is ordinary, which is precisely the shape a senior lying motionless on the floor
     * produces.
     *
     * @param expectedSampleCount How many 5-minute samples a *full* block of this length should
     *   hold. Deliberately not a constant in this file: how long "morning" or "night" lasts is the
     *   senior's own declared schedule ([SeedBaselineGenerator.computeTimeBlocks]'s
     *   `durationMinutes`, divided by 5), not a fixed number shared by every senior. A 5-hour night
     *   and an 11-hour night are both completely normal depending on whose schedule it is, and
     *   judging one senior's full night against another's expected count would reject good data
     *   for the wrong reason.
     * @param minimumFraction Defaults to 0.8, the plan's ~80% cutoff.
     */
    fun isUsable(
        aggregate: DailyAggregate,
        expectedSampleCount: Int,
        minimumFraction: Double = 0.8
    ): Boolean {
        // Null predates the sample_count column entirely: unknown, not zero, and CLAUDE.md's own
        // caution about that column applies — null must not be trusted as "empty enough to skip"
        // any more than as "full enough to use".
        val count = aggregate.sampleCount ?: return false
        return count >= expectedSampleCount * minimumFraction
    }
}
