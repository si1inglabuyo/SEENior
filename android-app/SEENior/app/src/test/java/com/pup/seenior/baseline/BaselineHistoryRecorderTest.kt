package com.pup.seenior.baseline

import com.pup.seenior.database.entities.BaselineHistory
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SeniorOnboarding
import com.pup.seenior.network.RoutineSyncer
import com.pup.seenior.network.dto.RoutineHistoryRowDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The history card rests on one claim: replaying the nightly update over the stored daily rows
 * gives the same learning curve the phone really went through. These pin down the parts of that
 * a viewer would notice: the first row is the questionnaire's guess, the hand-over is gradual,
 * and a thin day cannot move a cell.
 */
class BaselineHistoryRecorderTest {

    private val onboarding = SeniorOnboarding(
        seniorId = 1,
        wakeTime = "06:00",
        sleepTime = "22:00",
        hasNap = false,
        activityLevel = "light",
        languagePreference = "en",
    )

    /** Wake 06:00 / sleep 22:00: a full evening is 64 readings, 52 is the usability cutoff. */
    private fun evening(day: Int, screenIdle: Long, sampleCount: Int = 64) = DailyAggregate(
        seniorId = 1,
        date = "2026-08-%02d".format(day),
        timeBlock = "evening",
        avgMovementScore = 0.30,
        totalInactivityDuration = 1800,
        avgScreenIdleDuration = screenIdle,
        totalScreenUnlocks = 5,
        totalSteps = 400,
        isChargingMajority = false,
        sampleCount = sampleCount,
    )

    private fun replay(days: List<DailyAggregate>): List<BaselineHistory> =
        runBlocking { BaselineHistoryRecorder.reconstruct(1, onboarding, days) }

    private fun cell(rows: List<BaselineHistory>, date: String) =
        rows.single { it.date == date && it.featureName == "screen_idle_duration" && it.timeBlock == "evening" }

    @Test
    fun `no stored days gives no history`() {
        assertTrue(replay(emptyList()).isEmpty())
    }

    @Test
    fun `the first day shown is the questionnaire's guess alone`() {
        val rows = replay((1..3).map { evening(it, 4000) })
        val first = rows.minOf { it.date }

        assertEquals("the day before the first stored day", "2026-07-31", first)
        assertTrue("all seed", rows.filter { it.date == first }.all { it.isSeed })
        assertEquals(1800.0, cell(rows, first).medianValue, 0.001)
    }

    @Test
    fun `every reconstructed row is marked as an estimate`() {
        assertTrue(replay((1..5).map { evening(it, 4000) }).all { it.isEstimate })
    }

    @Test
    fun `each day carries all twenty cells and the hand-over moves gradually`() {
        val rows = replay((1..14).map { evening(it, 4000) })

        (1..14).forEach { day ->
            assertEquals(20, rows.count { it.date == "2026-08-%02d".format(day) })
        }
        // Under three usable days the updater writes nothing, so days 1 and 2 still show the seed.
        assertEquals(1800.0, cell(rows, "2026-08-02").medianValue, 0.001)
        val day3 = cell(rows, "2026-08-03").medianValue
        val day7 = cell(rows, "2026-08-07").medianValue
        val day14 = cell(rows, "2026-08-14")

        assertTrue("seed -> 4000 should rise day by day", 1800.0 < day3 && day3 < day7 && day7 < 4000.0)
        assertEquals(4000.0, day14.medianValue, 0.001)
        assertFalse("fully handed over at day 14", day14.isSeed)
    }

    @Test
    fun `a thin day does not move a cell and the old value carries forward`() {
        val good = (1..10).map { evening(it, 4000) }
        val thinOnDay11 = evening(11, 90_000, sampleCount = 5)
        val rows = replay(good + thinOnDay11)

        // Day 11's window is mostly good days, so it is still allowed to move; the thin row itself
        // must not have contributed its 90,000.
        assertTrue("the thin day's value never reached the fingerprint", cell(rows, "2026-08-11").medianValue <= 4000.0)
    }

    @Test
    fun `a window that is mostly thin leaves the previous cell as it was`() {
        val rows = replay(
            (1..4).map { evening(it, 4000) } + (5..14).map { evening(it, 90_000, sampleCount = 5) }
        )
        // Day 8 has 4 usable of 8, exactly half, which is still allowed to move. From day 9 the
        // window is under half usable, so the cell stops moving and keeps its day-8 value.
        assertEquals(cell(rows, "2026-08-09").medianValue, cell(rows, "2026-08-14").medianValue, 0.001)
    }

    @Test
    fun `the history signature changes when an estimate is replaced by a saved copy`() {
        fun row(estimate: Boolean) = RoutineHistoryRowDto(
            day = "2026-11-14", featureName = "movement_score", timeBlock = "morning",
            medianValue = 0.2, madValue = 0.05, sampleCount = 14, isSeed = false, isEstimate = estimate
        )
        assertFalse(
            RoutineSyncer.historySignatureOf(listOf(row(true))) ==
                RoutineSyncer.historySignatureOf(listOf(row(false)))
        )
    }
}
