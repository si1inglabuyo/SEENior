package com.pup.seenior.aggregation

import com.pup.seenior.database.entities.SensorData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The readings in [creditsNothingForTheVivoReBaseline] and
 * [rejectsTheBlockTotalThePilotHandsetsNeverProduced] are copied from the vivo V2317 tester
 * handset's own database, pulled 2026-09-23. They are the reason this file exists.
 */
class StepTotalsTest {

    private val fiveMinutes = 5 * 60_000L

    private fun reading(atMillis: Long, steps: Int) = SensorData(
        seniorId = 1,
        timestamp = atMillis,
        timeBlock = "morning",
        movementScore = 0.0,
        inactivityDuration = 0,
        screenIdleDuration = 0,
        screenUnlockCount = 0,
        isCharging = false,
        stepCount = steps
    )

    @Test
    fun sumsOrdinaryRises() {
        val rows = listOf(
            reading(0, 1_000),
            reading(fiveMinutes, 1_120),
            reading(2 * fiveMinutes, 1_200)
        )
        assertEquals(200, StepTotals.forBlock(rows))
    }

    @Test
    fun ordersByTimestampBeforeDiffing() {
        val rows = listOf(
            reading(2 * fiveMinutes, 1_200),
            reading(0, 1_000),
            reading(fiveMinutes, 1_120)
        )
        assertEquals(200, StepTotals.forBlock(rows))
    }

    @Test
    fun countsNothingWhileTheSeniorSitsStill() {
        val rows = (0..5).map { reading(it * fiveMinutes, 9_182) }
        assertEquals(0, StepTotals.forBlock(rows))
    }

    /**
     * The measured failure. Funtouch re-based the counter down by 49 with no reboot; the old rule
     * read the fall as a restart-from-zero and credited the whole reading to a seven-minute gap.
     */
    @Test
    fun creditsNothingForTheVivoReBaseline() {
        val sevenMinutes = 7 * 60_000L + 10_000L
        assertEquals(0, StepTotals.creditFor(0, 9_231, sevenMinutes, 9_182))
    }

    /**
     * A reboot is still handled, which is what the old branch was for: the counter restarts at
     * zero, so whatever it holds one poll later is small enough to have been walked.
     */
    @Test
    fun stillCreditsAPlausibleReadingAfterARealReboot() {
        assertEquals(130, StepTotals.creditFor(0, 12_400, fiveMinutes, 130))
    }

    /** A rise of 9,000 inside one poll is as impossible as a fall of the same size. */
    @Test
    fun rejectsAnImpossibleRiseToo() {
        assertEquals(0, StepTotals.creditFor(0, 1_000, fiveMinutes, 10_000))
    }

    /** Over a long enough gap the same number of steps is an ordinary walk, and is kept. */
    @Test
    fun keepsALargeCountWhenThereWasTimeToWalkIt() {
        val twoHours = 120 * 60_000L
        assertEquals(9_000, StepTotals.creditFor(0, 1_000, twoHours, 10_000))
    }

    /** Two readings in the same instant must not reject a handful of steps for having no time. */
    @Test
    fun toleratesAZeroLengthGap() {
        assertEquals(12, StepTotals.creditFor(0, 1_000, 0, 1_012))
    }

    @Test
    fun neverReturnsANegativeTotal() {
        val rows = listOf(reading(0, 500), reading(fiveMinutes, 400))
        assertEquals(400, StepTotals.forBlock(rows))
    }

    /**
     * The shape of the eight poisoned blocks in that handset's history: one bad pair inside an
     * otherwise ordinary block used to carry the whole block's total on its own.
     */
    @Test
    fun rejectsTheBlockTotalThePilotHandsetsNeverProduced() {
        val rows = listOf(
            reading(0, 9_200),
            reading(fiveMinutes, 9_231),
            reading(2 * fiveMinutes, 9_182),
            reading(3 * fiveMinutes, 9_190)
        )
        // 31 real steps, then the re-baseline contributing nothing, then 8 more.
        assertEquals(39, StepTotals.forBlock(rows))
    }
}
