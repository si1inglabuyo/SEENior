package com.pup.seenior.detection

import com.pup.seenior.database.entities.Baseline
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * The nine cases from the plan, validating [IsolationForest] and [AggregateFeatures] against
 * [AggregateSimulator]'s fabricated block-days. No real senior, Room or Android import.
 *
 * As in [FallDetectorTest], the negative cases outnumber the positive on purpose.
 *
 * Tuning note (revised 2026-10-14; the 2026-09-15 note chose 0.58 from one draw per case
 * and is superseded). Two things changed. Features are one-sided (more movement, steps or
 * unlocks than usual, or less stillness, is clipped to 0), because on 10-10..12 the detector
 * flagged busy afternoons. And the simulator's ordinary days now wobble like the pilot's (about
 * 1.5 z), where before they sat within 0.1 z of the median. [THRESHOLD] is 0.64, read off
 * 200 independent draws per scenario (ThresholdCalibrationTest). Share flagged at 0.58 / 0.64:
 *
 *     long stillness            99% / 90%   (clear positive)
 *     combination               70% / 36%   (partly detectable by nature)
 *     ordinary day              16% /  1%
 *     lazy Sunday               27% /  4%
 *     energetic day (3x steps)  10% /  1%
 *
 * At 0.58 about one block in six on an ordinary day would flag; with four blocks a day that is
 * most nights. 0.64 keeps the clear positive at 90% and the false rate near 1%. The mild
 * "total stillness" morning (steps -2.2 z, inactivity normal) flags only 11% and is Layer 1's
 * case, not Layer 2's. These are synthetic rates; there is no labelled real-emergency set.
 */
class IsolationForestTest {

    /** Score at/above this is "flag"; below is "quiet". See the class doc's tuning note. */
    private val THRESHOLD = 0.64

    private val RUNS = 100

    private val TREES = IsolationForest.DEFAULT_TREES
    private val PSI = IsolationForest.DEFAULT_PSI

    /** A "normal" senior's morning and night baselines, real numbers from the pilot phone (2026-09-15), so the factors mean the same thing as against real data. */
    private val baselines = listOf(
        Baseline(seniorId = 1, featureName = "movement_score", timeBlock = "morning", medianValue = 0.0346271005188309, madValue = 0.05, sampleCount = 9),
        Baseline(seniorId = 1, featureName = "inactivity_duration", timeBlock = "morning", medianValue = 2753.35714285714, madValue = 1108.28571428571, sampleCount = 9),
        Baseline(seniorId = 1, featureName = "screen_idle_duration", timeBlock = "morning", medianValue = 2335.28571428571, madValue = 412.5, sampleCount = 9),
        Baseline(seniorId = 1, featureName = "screen_unlock_count", timeBlock = "morning", medianValue = 6.35714285714286, madValue = 1.64285714285714, sampleCount = 9),
        Baseline(seniorId = 1, featureName = "step_count", timeBlock = "morning", medianValue = 414.0, madValue = 184.5, sampleCount = 9),
        Baseline(seniorId = 1, featureName = "movement_score", timeBlock = "night", medianValue = 0.0149292731419558, madValue = 0.05, sampleCount = 10),
        Baseline(seniorId = 1, featureName = "inactivity_duration", timeBlock = "night", medianValue = 11545.7142857143, madValue = 5334.64285714286, sampleCount = 10),
        Baseline(seniorId = 1, featureName = "screen_idle_duration", timeBlock = "night", medianValue = 13203.9285714286, madValue = 3991.78571428571, sampleCount = 10),
        Baseline(seniorId = 1, featureName = "screen_unlock_count", timeBlock = "night", medianValue = 0.857142857142857, madValue = 1.0, sampleCount = 10),
        Baseline(seniorId = 1, featureName = "step_count", timeBlock = "night", medianValue = 5.71428571428571, madValue = 50.0, sampleCount = 10),
    )

    private fun forestFor(block: String, seed: Long, count: Int = 56): IsolationForest {
        val days = AggregateSimulator.normalDays(count, seniorId = 1, block = block, baselines = baselines, seed = seed)
        val vectors = days.map { AggregateFeatures.vector(it, baselines) }
        return IsolationForest.train(vectors, trees = TREES, psi = PSI, seed = seed + 1)
    }

    private fun scenario(
        block: String,
        movementFactor: Double = 1.0,
        stepsFactor: Double = 1.0,
        stillnessFactor: Double = 1.0,
        sampleCount: Int? = AggregateSimulator.DEFAULT_SAMPLE_COUNT,
        seed: Long
    ): DoubleArray {
        val day = AggregateSimulator.day(
            seniorId = 1, date = "2099-01-01", block = block, baselines = baselines,
            movementFactor = movementFactor, stepsFactor = stepsFactor, stillnessFactor = stillnessFactor,
            sampleCount = sampleCount, spread = AggregateSimulator.ORDINARY_SPREAD, random = Random(seed)
        )
        return AggregateFeatures.vector(day, baselines)
    }

    /**
     * Share of [RUNS] independent (fortnight, day) draws that score at or above [THRESHOLD].
     * One draw proves nothing once ordinary days wobble like real ones (spread ~1.5 z, measured
     * on the pilot): the same scenario scores anywhere in a band. The claim is about the rate.
     */
    private fun flagRate(
        movementFactor: Double = 1.0,
        stepsFactor: Double = 1.0,
        stillnessFactor: Double = 1.0
    ): Double {
        val flagged = (1..RUNS).count { i ->
            val forest = forestFor("morning", seed = i * 100L)
            val point = scenario(
                "morning", movementFactor, stepsFactor, stillnessFactor, seed = i * 100L + 2
            )
            forest.score(point) >= THRESHOLD
        }
        return flagged.toDouble() / RUNS
    }

    @Test
    fun `catches a long stillness`() {
        // Inactivity about +3.5 z and screen idle about +8 z: Layer 2's clear-cut positive.
        val rate = flagRate(movementFactor = 0.2, stepsFactor = 0.2, stillnessFactor = 2.5)
        assertTrue("expected >= 85% flagged, got ${"%.0f".format(rate * 100)}%", rate >= 0.85)
    }

    @Test
    fun `sometimes catches the combination even when no single feature is extreme`() {
        // Every factor is a mild move, none near Layer 1's 2.5 cutoff alone, but all drift the
        // same way. A forest can only be partly sure of this, so the bar is a share, not most.
        val rate = flagRate(movementFactor = 0.0, stepsFactor = 0.15, stillnessFactor = 1.38)
        assertTrue("expected >= 25% flagged, got ${"%.0f".format(rate * 100)}%", rate >= 0.25)
    }

    @Test
    fun `ignores an ordinary day`() {
        val rate = flagRate()
        assertTrue("expected <= 5% flagged, got ${"%.0f".format(rate * 100)}%", rate <= 0.05)
    }

    @Test
    fun `ignores a lazy Sunday`() {
        val rate = flagRate(movementFactor = 0.6, stepsFactor = 0.5)
        assertTrue("expected <= 10% flagged, got ${"%.0f".format(rate * 100)}%", rate <= 0.10)
    }

    @Test
    fun `ignores an energetic day`() {
        // The hardest case in the plan: unusual is not the same as unsafe. Features are
        // one-sided, so doing more than usual cannot count against her.
        val rate = flagRate(stepsFactor = 3.0)
        assertTrue("expected <= 5% flagged, got ${"%.0f".format(rate * 100)}%", rate <= 0.05)
        val veryEnergetic = flagRate(movementFactor = 10.0, stepsFactor = 10.0)
        assertTrue("expected <= 5% flagged, got ${"%.0f".format(veryEnergetic * 100)}%", veryEnergetic <= 0.05)
    }

    @Test
    fun `ignores a normal night`() {
        // A night's raw stillness (~11,546 s) dwarfs a morning's (~2,753 s). Training and scoring
        // use z-scores so a normal night isn't flagged for looking unlike a morning.
        val forest = forestFor("night", seed = 9L)
        val normalNight = scenario("night", seed = 10L)
        assertFalse("expected quiet, scored ${forest.score(normalNight)}", forest.score(normalNight) >= THRESHOLD)
    }

    @Test
    fun `scores are bounded between 0 and 1`() {
        val forest = forestFor("morning", seed = 11L)
        val samples = listOf(
            scenario("morning", seed = 12L),
            scenario("morning", movementFactor = 0.0, stepsFactor = 0.0, stillnessFactor = 5.0, seed = 13L),
            scenario("morning", movementFactor = 10.0, stepsFactor = 10.0, seed = 14L),
        )
        for (point in samples) {
            val score = forest.score(point)
            assertTrue("score $score out of bounds", score in 0.0..1.0)
        }
    }

    @Test
    fun `training is repeatable from the same seed`() {
        val days = AggregateSimulator.normalDays(56, seniorId = 1, block = "morning", baselines = baselines, seed = 15L)
        val vectors = days.map { AggregateFeatures.vector(it, baselines) }
        val point = scenario("morning", movementFactor = 0.05, stepsFactor = 0.0, seed = 16L)

        val forestA = IsolationForest.train(vectors, trees = TREES, psi = PSI, seed = 42L)
        val forestB = IsolationForest.train(vectors, trees = TREES, psi = PSI, seed = 42L)

        assertEquals(forestA.score(point), forestB.score(point), 0.0)
    }

    /**
     * Belongs to [IsolationForestDetector], not [IsolationForest]. [IsolationForest.train]
     * only needs 2 rows; the "~20 usable rows" floor is a product decision in
     * [IsolationForestDetector.MIN_TRAINING_ROWS]. The guard exists but is untested: proving
     * "returns NotEnoughData and writes nothing" needs fakes for the four DAOs that throw on
     * any call. `BaselineUpdaterTest.FakeBaselineDao` is the precedent.
     */
    @Ignore("needs fake DAOs for IsolationForestDetector; the guard itself is built")
    @Test
    fun `refuses too little data`() {
    }

    @Test
    fun `excludes thin and null sample counts`() {
        val thin = AggregateSimulator.day(
            seniorId = 1, date = "2099-01-02", block = "morning", baselines = baselines,
            sampleCount = 8, random = Random(17L)
        )
        val nullCount = thin.copy(sampleCount = null)
        val full = AggregateSimulator.day(
            seniorId = 1, date = "2099-01-03", block = "morning", baselines = baselines,
            random = Random(18L)
        )

        assertFalse(AggregateFeatures.isUsable(thin, expectedSampleCount = AggregateSimulator.DEFAULT_SAMPLE_COUNT))
        assertFalse(AggregateFeatures.isUsable(nullCount, expectedSampleCount = AggregateSimulator.DEFAULT_SAMPLE_COUNT))
        assertTrue(AggregateFeatures.isUsable(full, expectedSampleCount = AggregateSimulator.DEFAULT_SAMPLE_COUNT))
    }
}
