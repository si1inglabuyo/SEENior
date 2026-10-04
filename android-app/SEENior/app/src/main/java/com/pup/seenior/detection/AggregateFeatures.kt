package com.pup.seenior.detection

import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SeniorOnboarding

/**
 * Layer 2 feature builder: turns one block-day into the six-number [DoubleArray] that
 * [IsolationForest] trains and scores on, and decides whether a block-day is trustworthy.
 *
 * It is the only class that knows what a `DailyAggregate` or `Baseline` row is;
 * [IsolationForest] and [IsolationTree] work on plain arrays so they can be unit tested.
 */
object AggregateFeatures {

    /** The order of every [DoubleArray] this object produces, kept in one place. */
    val FEATURE_NAMES = listOf(
        "movement_score",
        "inactivity_duration",
        "screen_idle_duration",
        "screen_unlock_count",
        "step_count",
        "is_charging"
    )

    /**
     * Builds the feature vector for [aggregate] from the baseline rows of the same senior and
     * time block. Takes the whole baseline set so filtering by block happens in one place.
     *
     * The first five numbers are signed Modified Z-Scores, `(value - median) / effectiveMad`,
     * not run through [MedianMad.deviationsScore] (which takes the absolute value): the sign
     * separates an energetic day from barely moving. The sixth, `is_charging`, is 0.0/1.0.
     *
     * @throws IllegalArgumentException if a baseline row for one of the five features is
     *   missing for this senior and block; onboarding seeds all of them, so a gap is a bug.
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
     * Whether [aggregate] was summarised from enough raw readings to trust. Thin block-days are
     * excluded from training and scoring, since once `Sensor_Data` is purged a thin summary
     * looks like a full one, and a near-empty day would teach the model that empty is normal.
     *
     * @param expectedSampleCount how many 5-minute samples a full block should hold. It comes
     *   from the senior's own schedule ([SeedBaselineGenerator.computeTimeBlocks]), since a
     *   night can be five or eleven hours.
     * @param minimumFraction defaults to 0.8.
     */
    fun isUsable(
        aggregate: DailyAggregate,
        expectedSampleCount: Int,
        minimumFraction: Double = 0.8
    ): Boolean {
        // Null means the row predates the sample_count column: unknown, so not trusted.
        val count = aggregate.sampleCount ?: return false
        return count >= expectedSampleCount * minimumFraction
    }

    /**
     * The same judgement as above, but working out the expected count from [onboarding]. Both
     * detection layers call this, so a block Layer 2 refuses is not folded into the Layer 1
     * baseline either. (Frozen-phone nights with as few as 10 of 60 readings once inflated the
     * night inactivity median by 62%.)
     *
     * @return false if the block matches none of this senior's windows.
     */
    fun isUsable(aggregate: DailyAggregate, onboarding: SeniorOnboarding): Boolean {
        val expected = expectedSampleCount(onboarding, aggregate.timeBlock) ?: return false
        return isUsable(aggregate, expected)
    }

    /**
     * How many 5-minute readings a full block of this name should hold for this senior,
     * derived from their own declared hours by [SeedBaselineGenerator.computeTimeBlocks].
     */
    fun expectedSampleCount(onboarding: SeniorOnboarding, timeBlock: String): Int? =
        SeedBaselineGenerator.computeTimeBlocks(onboarding.wakeTime, onboarding.sleepTime)
            .firstOrNull { it.block.name.lowercase() == timeBlock }
            ?.let { it.durationMinutes / POLL_INTERVAL_MINUTES }

    /** The spec §4's sampling cadence, used to turn a block's length into an expected row count. */
    private const val POLL_INTERVAL_MINUTES = 5
}
