package com.pup.seenior.aggregation

import com.pup.seenior.database.entities.SensorData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ActiveTimesTest {

    private val zone = ZoneId.of("Asia/Manila")

    private fun at(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, 14, hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** A reading that is active (screen used or moved within 5 min) or still (an hour idle). */
    private fun reading(hour: Int, minute: Int, active: Boolean) = SensorData(
        seniorId = 1, timestamp = at(hour, minute), timeBlock = "morning",
        movementScore = 0.0, inactivityDuration = if (active) 60 else 3600,
        screenIdleDuration = if (active) 60 else 3600, screenUnlockCount = 0,
        isCharging = false, stepCount = 0
    )

    @Test
    fun `first and last are the ends of the sustained activity`() {
        val rows = listOf(
            reading(6, 0, false), reading(7, 0, false),
            reading(8, 10, true), reading(8, 15, true), reading(9, 30, true), reading(9, 35, true),
            reading(10, 0, false),
        )
        val (first, last) = ActiveTimes.firstAndLastMinute(rows, zone)
        assertEquals(8 * 60 + 10, first)
        assertEquals(9 * 60 + 35, last)
    }

    @Test
    fun `one lone active reading is a bathroom trip, not waking up`() {
        val rows = listOf(reading(6, 5, true), reading(6, 10, false), reading(8, 10, true), reading(8, 15, true))
        val (first, _) = ActiveTimes.firstAndLastMinute(rows, zone)
        assertEquals("the lone 6:05 reading must not count", 8 * 60 + 10, first)
    }

    @Test
    fun `a block with no sustained activity records nothing`() {
        assertEquals(null to null, ActiveTimes.firstAndLastMinute(listOf(reading(6, 0, false), reading(7, 0, false)), zone))
        assertEquals(null to null, ActiveTimes.firstAndLastMinute(listOf(reading(6, 0, true)), zone))
        assertEquals(null to null, ActiveTimes.firstAndLastMinute(emptyList(), zone))
    }

    @Test
    fun `two active readings more than half an hour apart are not sustained`() {
        val (first, last) = ActiveTimes.firstAndLastMinute(listOf(reading(6, 0, true), reading(8, 0, true)), zone)
        assertNull(first)
        assertNull(last)
    }

    @Test
    fun `either the screen or movement within five minutes counts as active`() {
        val screenOnly = reading(8, 0, false).copy(screenIdleDuration = 120)
        val movedOnly = reading(8, 5, false).copy(inactivityDuration = 200)
        val (first, _) = ActiveTimes.firstAndLastMinute(listOf(screenOnly, movedOnly), zone)
        assertEquals(8 * 60, first)
    }

    @Test
    fun `median ignores one odd morning`() {
        // 120 (2:00 AM) is the odd one; the middle of the five is 485 whether or not it is there.
        assertEquals(485, ActiveTimes.median(listOf(480, 490, 500, 485, 120)))
        assertEquals(495, ActiveTimes.median(listOf(490, 500)))
        assertNull(ActiveTimes.median(emptyList()))
    }
}
