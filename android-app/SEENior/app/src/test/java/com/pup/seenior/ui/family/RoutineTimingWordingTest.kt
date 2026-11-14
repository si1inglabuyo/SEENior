package com.pup.seenior.ui.family

import com.pup.seenior.network.dto.RoutineTimingDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineTimingWordingTest {

    private val t = RoutineTimingDto(
        morningStart = 360, afternoonStart = 660, eveningStart = 960, nightStart = 1260,
        wakeMinute = 490, wakeDays = 4, eveningLastMinute = 1120, eveningLastDays = 4
    )

    @Test
    fun `clock reads as a person says it`() {
        assertEquals("6:00 AM", RoutineTimingWording.clock(360))
        assertEquals("12:00 AM", RoutineTimingWording.clock(0))
        assertEquals("12:30 PM", RoutineTimingWording.clock(750))
        assertEquals("9:05 PM", RoutineTimingWording.clock(21 * 60 + 5))
        assertEquals("11:59 PM", RoutineTimingWording.clock(1439))
    }

    @Test
    fun `each block ends where the next begins and night wraps to morning`() {
        assertEquals("6:00 AM – 11:00 AM", RoutineTimingWording.range("morning", t))
        assertEquals("11:00 AM – 4:00 PM", RoutineTimingWording.range("afternoon", t))
        assertEquals("4:00 PM – 9:00 PM", RoutineTimingWording.range("evening", t))
        assertEquals("9:00 PM – 6:00 AM", RoutineTimingWording.range("night", t))
        assertNull(RoutineTimingWording.range("noon", t))
    }

    @Test
    fun `usually active is said only for morning and evening and only with a time`() {
        assertEquals("Usually first active about 8:10 AM (based on 4 days)", RoutineTimingWording.usuallyActive("morning", t, false))
        assertEquals("Usually last active about 6:40 PM (based on 4 days)", RoutineTimingWording.usuallyActive("evening", t, false))
        assertEquals(
            "Karaniwang unang gumagalaw bandang 8:10 AM (batay sa 4 araw)",
            RoutineTimingWording.usuallyActive("morning", t, true)
        )
        assertNull(RoutineTimingWording.usuallyActive("afternoon", t, false))
        assertNull(RoutineTimingWording.usuallyActive("night", t, false))
        assertNull(RoutineTimingWording.usuallyActive("morning", t.copy(wakeMinute = null, wakeDays = 2), false))
    }
}
