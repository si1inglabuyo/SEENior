package com.pup.seenior.afterhours

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

class AfterHoursConfigTest {

    private val manila = ZoneId.of("Asia/Manila")
    private val config = AfterHoursConfig.DEFAULT

    /** Epoch millis for a wall-clock time in Manila. 2026-11-04 is a Wednesday, 2026-11-07 a Saturday. */
    private fun manilaMs(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(manila).toInstant().toEpochMilli()

    @Test
    fun `open on a weekday between 8 and 5`() {
        assertTrue(config.isHallOpen(manilaMs(2026, 11, 4, 8, 0)))
        assertTrue(config.isHallOpen(manilaMs(2026, 11, 4, 12, 30)))
        assertTrue(config.isHallOpen(manilaMs(2026, 11, 4, 16, 59)))
    }

    @Test
    fun `closed before 8, from 5 pm, and overnight`() {
        assertFalse(config.isHallOpen(manilaMs(2026, 11, 4, 7, 59)))
        assertFalse(config.isHallOpen(manilaMs(2026, 11, 4, 17, 0)))
        assertFalse(config.isHallOpen(manilaMs(2026, 11, 4, 23, 30)))
        assertFalse(config.isHallOpen(manilaMs(2026, 11, 5, 3, 0)))
    }

    @Test
    fun `closed all weekend`() {
        assertFalse(config.isHallOpen(manilaMs(2026, 11, 7, 10, 0)))
        assertFalse(config.isHallOpen(manilaMs(2026, 11, 8, 10, 0)))
    }

    @Test
    fun `judged in Manila time whatever the phone's own zone`() {
        // 01:00 UTC on Wednesday is 09:00 in Manila: open. 10:00 UTC is 18:00 in Manila: closed.
        val utc = ZoneId.of("UTC")
        val nineInManila = LocalDateTime.of(2026, 11, 4, 1, 0).atZone(utc).toInstant().toEpochMilli()
        val sixInManila = LocalDateTime.of(2026, 11, 4, 10, 0).atZone(utc).toInstant().toEpochMilli()
        assertTrue(config.isHallOpen(nineInManila))
        assertFalse(config.isHallOpen(sixInManila))
    }

    @Test
    fun `a closed day shuts the hall even in office hours`() {
        val closedToday = config.copy(closedDays = listOf("2026-11-04"))
        assertFalse(closedToday.isHallOpen(manilaMs(2026, 11, 4, 10, 0)))
        assertTrue(closedToday.isHallOpen(manilaMs(2026, 11, 5, 10, 0)))
    }

    @Test
    fun `days and hours read as words`() {
        assertEquals("Monday to Friday", AfterHoursConfig.daysText(listOf(0, 1, 2, 3, 4), filipino = false))
        assertEquals("Lunes hanggang Biyernes", AfterHoursConfig.daysText(listOf(0, 1, 2, 3, 4), filipino = true))
        assertEquals("Monday, Wednesday", AfterHoursConfig.daysText(listOf(0, 2), filipino = false))
        assertEquals("Saturday", AfterHoursConfig.daysText(listOf(5), filipino = false))
        assertEquals("8:00 AM", AfterHoursConfig.hourText(8))
        assertEquals("5:00 PM", AfterHoursConfig.hourText(17))
        assertEquals("12:00 PM", AfterHoursConfig.hourText(12))
        assertEquals("12:00 AM", AfterHoursConfig.hourText(0))
    }

    @Test
    fun `the defaults are usable and start with the national hotline`() {
        assertTrue(config.isUsable())
        assertEquals(6, config.hotlines.size)
        assertEquals("911", config.hotlines.first().numbers.first().dial)
    }

    @Test
    fun `a half-filled or broken config is not usable`() {
        assertFalse(AfterHoursConfig().isUsable()) // no hotlines
        assertFalse(config.copy(hotlines = listOf(AfterHoursHotline("X", emptyList()))).isUsable())
        assertFalse(config.copy(hall = HallHours(openHour = 17, closeHour = 8)).isUsable())
        assertFalse(config.copy(hall = HallHours(timezone = "Not/AZone")).isUsable())
        assertFalse(config.copy(hall = HallHours(openWeekdays = listOf(9))).isUsable())
    }

    @Test
    fun `server json parses, and a missing field keeps its default instead of null`() {
        val json = """{"version":3,"hall":{"timezone":"Asia/Manila","open_hour":9,"close_hour":16,"open_weekdays":[0,1,2,3,4]},
            "hotlines":[{"name":"Test line","numbers":[{"display":"123","dial":"123"}]}]}"""
        val parsed = AfterHoursStore.parse(json)
        assertNotNull(parsed)
        assertEquals(3, parsed!!.version)
        assertEquals(9, parsed.hall.openHour)
        assertEquals(emptyList<String>(), parsed.closedDays) // not sent: default, not null
    }

    @Test
    fun `garbage and unusable json fall back to null so the defaults are used`() {
        assertNull(AfterHoursStore.parse(null))
        assertNull(AfterHoursStore.parse("not json"))
        assertNull(AfterHoursStore.parse("""{"version":1,"hotlines":[]}"""))
    }

    @Test
    fun `the bundled defaults carry the same numbers as the server list`() {
        val source = listOf(
            "../../../backend/app/core/after_hours.py",
            "../../backend/app/core/after_hours.py",
            "../backend/app/core/after_hours.py",
            "backend/app/core/after_hours.py"
        ).map { File(it) }.firstOrNull { it.exists() }
        assumeTrue("backend source not reachable from here", source != null)
        val serverDials = Regex("""Number\("[^"]*",\s*"(\d+)"\)""").findAll(source!!.readText()).map { it.groupValues[1] }.toList()
        val phoneDials = config.hotlines.flatMap { h -> h.numbers.map { it.dial } }
        assertEquals(serverDials, phoneDials)
    }
}
