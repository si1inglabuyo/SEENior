package com.pup.seenior.detection

import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.DailyAggregate
import java.time.LocalDate
import kotlin.math.round
import kotlin.random.Random

/**
 * Phase 3 of the Isolation Forest build (CLAUDE.md §5, Layer 2; §10's simulated-data mandate) —
 * the [FallSimulator] counterpart for this layer.
 *
 * Generates real [DailyAggregate] rows — not pre-computed feature vectors — scaled against a real
 * [Baseline] set by named factors: `movementFactor = 0.05` means "5% of her normal movement for
 * this block," not a raw z-shift. Producing actual aggregate rows rather than skipping straight to
 * z-scores means Phase 4's test suite exercises [AggregateFeatures] as well as [IsolationForest] —
 * the whole real pipeline on fabricated input, which is also the only way to exercise
 * [AggregateFeatures.isUsable] at all, since a thin or null `sample_count` has nowhere to live on
 * a bare [DoubleArray].
 *
 * **Every entry point takes a [Random] instance; none default to [Random.Default].** Unseeded
 * randomness in a simulator is one of the three implementation traps the plan calls out by name:
 * it passes four test runs and fails the fifth, on a run nobody is sitting at to explain why.
 */
object AggregateSimulator {

    /** How many 5-minute samples a "full" simulated block-day carries, unless a test overrides it
     *  to exercise [AggregateFeatures.isUsable]'s thin-block exclusion (test case 9). Sixty is not
     *  a universal truth about every senior's blocks — see [AggregateFeatures.isUsable]'s own KDoc
     *  — it is simply what these *invented* rows carry unless told otherwise. */
    const val DEFAULT_SAMPLE_COUNT = 60

    /** Day-to-day wobble applied on top of every factor, so 40 "normal" rows aren't 40 identical
     *  copies of the same number — a forest trained on truly identical rows never sees the spread
     *  a real routine has, and the "ignores a normal night" case would look artificially easy. */
    private const val JITTER = 0.05

    /**
     * One block-day, scaled against [baselines]' medians for [seniorId] and [block].
     *
     * A factor of `1.0` (the default for all three) means "at her normal median" — an ordinary
     * day. `movementFactor = 0.05, stepsFactor = 0.0` is the "total stillness" case; `stepsFactor =
     * 3.0` alone is the "energetic day" case; the plan's own names for both are in
     * [IsolationForest]'s doc.
     *
     * [stillnessFactor] scales inactivity and screen-idle **together**, off their own separate
     * baselines — physically the same fact (how disengaged she was) measured two ways, and a real
     * quiet block moves both at once. [unlocks] is a direct override rather than a factor: nothing
     * in the plan's nine cases needs it scaled, only occasionally pinned to a specific count.
     *
     * @param sampleCount Defaults to [DEFAULT_SAMPLE_COUNT] (a full block). Pass a low number or
     *   `null` to build the rows test case 9 needs.
     * @param random Shared across a whole scenario's calls (e.g. all of [normalDays]) so the run
     *   stays reproducible together, not merely individually reproducible in isolation.
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
     * [count] ordinary block-days for [block], standing in for the rows a real fortnight
     * accumulates for that one block (14 by default — one per day, since [day] already covers a
     * single block; a full simulated 56-row fortnight is four calls to this, one per block).
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
