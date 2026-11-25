package com.pup.seenior.baseline

import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SeniorOnboarding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RoutineTimingBuilderTest {

    private val today = LocalDate.of(2026, 11, 15)

    private fun onboarding() = SeniorOnboarding(
        seniorId = 1, wakeTime = "06:00", sleepTime = "21:00", hasNap = false,
        activityLevel = "light", languagePreference = "en"
    )

    private fun agg(date: String, block: String, first: Int? = null, last: Int? = null) = DailyAggregate(
        seniorId = 1, date = date, timeBlock = block, avgMovementScore = 0.0, totalInactivityDuration = 0,
        avgScreenIdleDuration = 0, totalScreenUnlocks = 0, totalSteps = 0, isChargingMajority = false,
        firstActiveMinute = first, lastActiveMinute = last
    )

    @Test
    fun `block starts come from the typed wake and sleep times`() {
        val t = RoutineTimingBuilder.build(onboarding(), emptyList(), today)
        assertEquals(6 * 60, t.morningStart)
        assertEquals(11 * 60, t.afternoonStart)
        assertEquals(16 * 60, t.eveningStart)
        assertEquals(21 * 60, t.nightStart)
    }

    @Test
    fun `three days make a usual time and the median is used`() {
        val rows = listOf(
            agg("2026-11-12", "morning", first = 480), agg("2026-11-13", "morning", first = 500),
            agg("2026-11-14", "morning", first = 490),
            agg("2026-11-12", "evening", last = 1100), agg("2026-11-13", "evening", last = 1120),
            agg("2026-11-14", "evening", last = 1110),
        )
        val t = RoutineTimingBuilder.build(onboarding(), rows, today)
        assertEquals(490, t.wakeMinute)
        assertEquals(3, t.wakeDays)
        assertEquals(1110, t.eveningLastMinute)
    }

    @Test
    fun `afternoon last active follows the same three day rule`() {
        val rows = listOf(
            agg("2026-11-12", "afternoon", last = 880), agg("2026-11-13", "afternoon", last = 900),
            agg("2026-11-14", "afternoon", last = 890),
        )
        val t = RoutineTimingBuilder.build(onboarding(), rows, today)
        assertEquals(890, t.afternoonLastMinute)
        assertEquals(3, t.afternoonLastDays)
        val two = RoutineTimingBuilder.build(onboarding(), rows.take(2), today)
        assertNull(two.afternoonLastMinute)
        assertEquals(2, two.afternoonLastDays)
    }

    @Test
    fun `two days are not enough to call a time usual`() {
        val rows = listOf(agg("2026-11-13", "morning", first = 500), agg("2026-11-14", "morning", first = 490))
        val t = RoutineTimingBuilder.build(onboarding(), rows, today)
        assertNull(t.wakeMinute)
        assertEquals("the count is still reported so the card can say how many days", 2, t.wakeDays)
    }

    @Test
    fun `days older than fourteen are ignored and unrecorded rows do not count`() {
        val rows = listOf(
            agg("2026-10-01", "morning", first = 300), agg("2026-10-02", "morning", first = 300),
            agg("2026-10-03", "morning", first = 300), agg("2026-11-13", "morning", first = null),
        )
        assertNull(RoutineTimingBuilder.build(onboarding(), rows, today).wakeMinute)
    }
}
