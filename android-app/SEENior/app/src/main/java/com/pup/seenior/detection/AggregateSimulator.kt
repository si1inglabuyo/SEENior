package com.pup.seenior.detection

import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.DailyAggregate
import java.time.LocalDate
import kotlin.math.round
import kotlin.random.Random

/**
 * Test-data generator for Layer 2, the [FallSimulator] counterpart (the spec's simulated-data
 * approach to validation).
 *
 * It generates real [DailyAggregate] rows, scaled against a real [Baseline] set by named
 * factors (`movementFactor = 0.05` means 5% of normal movement for that block), so tests
 * exercise [AggregateFeatures] (including [AggregateFeatures.isUsable]) as well as
 * [IsolationForest].
 *
 * Every entry point takes a [Random]; none default to [Random.Default], since unseeded
 * randomness makes tests fail intermittently.
 */
object AggregateSimulator {

    /** How many 5-minute samples a "full" simulated block-day carries, unless a test overrides it to exercise the thin-block exclusion. */
    const val DEFAULT_SAMPLE_COUNT = 60

    /** Day-to-day wobble on top of every factor, so "normal" rows aren't identical copies. */
    private const val JITTER = 0.05

    /**
     * One block-day, scaled against [baselines]' medians for [seniorId] and [block]. A factor
     * of `1.0` means her normal median; `movementFactor = 0.05, stepsFactor = 0.0` is total
     * stillness and `stepsFactor = 3.0` alone is an energetic day.
     *
     * [stillnessFactor] scales inactivity and screen-idle together, since a quiet block moves
     * both. [unlocks] is a direct override.
     *
     * @param sampleCount defaults to [DEFAULT_SAMPLE_COUNT]; pass a low number or `null` to
     *   build thin-block rows.
     * @param random shared across a scenario's calls so the run is reproducible together.
     */
    fun day(
        seniorId: Int,
        date: String,
        block: String,
        baselines: List<Baseline>,
        movementFactor: Double = 1.0,
        stepsFactor: Double = 1.0,
        stillnessFactor: Double = 1.0,
        unlocks: Int? = null,
        charging: Boolean = false,
        sampleCount: Int? = DEFAULT_SAMPLE_COUNT,
        random: Random
    ): DailyAggregate {
        val byFeature = baselines
            .filter { it.seniorId == seniorId && it.timeBlock == block }
            .associateBy { it.featureName }

        fun median(featureName: String): Double =
            byFeature[featureName]?.medianValue ?: throw IllegalArgumentException(
                "AggregateSimulator needs a '$featureName' baseline for senior $seniorId, " +
                    "block '$block' to scale against"
            )

        fun scaled(featureName: String, factor: Double): Double {
            val wobble = 1.0 + random.nextDouble(-JITTER, JITTER)
            return (median(featureName) * factor * wobble).coerceAtLeast(0.0)
        }

        val unlockCount = unlocks ?: round(scaled("screen_unlock_count", 1.0)).toInt()

        return DailyAggregate(
            seniorId = seniorId,
            date = date,
            timeBlock = block,
            avgMovementScore = scaled("movement_score", movementFactor),
            totalInactivityDuration = scaled("inactivity_duration", stillnessFactor).toLong(),
            avgScreenIdleDuration = scaled("screen_idle_duration", stillnessFactor).toLong(),
            totalScreenUnlocks = unlockCount,
            totalSteps = scaled("step_count", stepsFactor).toInt(),
            isChargingMajority = charging,
            sampleCount = sampleCount
        )
    }

    /**
     * [count] ordinary block-days for [block] (14 by default, one per day). A full simulated
     * fortnight is four calls, one per block.
     */
    fun normalDays(
        count: Int,
        seniorId: Int,
        block: String,
        baselines: List<Baseline>,
        seed: Long
    ): List<DailyAggregate> {
        val random = Random(seed)
        return List(count) { i ->
            day(
                seniorId = seniorId,
                date = LocalDate.ofEpochDay(i.toLong()).toString(),
                block = block,
                baselines = baselines,
                random = random
            )
        }
    }
}
