package com.pup.seenior.ui.family

import com.pup.seenior.network.dto.RoutineRowDto
import com.pup.seenior.network.dto.RoutineTimingDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyRoutinePartsTest {

    private val timing = RoutineTimingDto(
        morningStart = 390, afternoonStart = 690, eveningStart = 990, nightStart = 1290,
        wakeMinute = 405, wakeDays = 12, eveningLastMinute = 1130, eveningLastDays = 12,
        afternoonLastMinute = 940, afternoonLastDays = 12
    )

    private fun row(feature: String, block: String, median: Double, days: Int = 14, seed: Boolean = false) =
        RoutineRowDto(
            featureName = feature, timeBlock = block, medianValue = median, madValue = 1.0,
            sampleCount = days, isSeed = seed
        )

    private fun fingerprint(days: Int = 14, seed: Boolean = false) =
        listOf("morning", "afternoon", "evening", "night").flatMap { b ->
            listOf(
                row("inactivity_duration", b, 1740.0, days, seed),
                row("step_count", b, 1972.0, days, seed),
                row("screen_unlock_count", b, 15.0, days, seed),
                row("screen_idle_duration", b, 2580.0, days, seed),
                row("movement_score", b, 0.1, days, seed)
            )
        }

    @Test
    fun `four parts in order, each with hours and the four figures`() {
        val parts = FamilyRoutineParts.parts(fingerprint(), timing, fil = false)
        assertEquals(listOf("morning", "afternoon", "evening", "night"), parts.map { it.block })
        assertEquals("6:30 AM – 11:30 AM", parts[0].hours)
        assertEquals(
            listOf("Still for up to", "Steps", "Phone unlocks", "Longest time phone untouched"),
            parts[0].lines.map { it.label }
        )
        assertEquals(listOf("29 min", "1,972", "15 times", "43 min"), parts[0].lines.map { it.value })
    }

    @Test
    fun `usual lines say only what the cloud holds`() {
        val parts = FamilyRoutineParts.parts(fingerprint(), timing, fil = false).associateBy { it.block }
        assertEquals("Usually up by about 6:45 AM", parts.getValue("morning").headline)
        assertEquals("Usually active until about 3:40 PM", parts.getValue("afternoon").headline)
        assertEquals("Usually last active about 6:50 PM", parts.getValue("evening").headline)
        assertEquals("Time for sleep", parts.getValue("night").headline)
    }

    @Test
    fun `an afternoon the phone has not sent gets no line, not an invented time`() {
        val old = timing.copy(afternoonLastMinute = null, afternoonLastDays = 0)
        val afternoon = FamilyRoutineParts.parts(fingerprint(), old, fil = false).first { it.block == "afternoon" }
        assertNull(afternoon.headline)
    }

    @Test
    fun `filipino wording and no hours before the phone sends block times`() {
        val parts = FamilyRoutineParts.parts(fingerprint(), null, fil = true)
        assertNull(parts[0].hours)
        assertEquals("Hakbang", parts[0].lines[1].label)
        assertEquals("15 beses", parts[0].lines[2].value)
        assertEquals("Oras ng pagtulog", parts[3].headline)
        assertEquals("Tulog", parts[3].name)
    }

    @Test
    fun `a part still on the setup guess is tagged and says it is still learning`() {
        val parts = FamilyRoutineParts.parts(fingerprint(days = 3, seed = true), timing, fil = false)
        assertFalse(parts[0].learned)
        assertEquals("Still learning this part of the day.", parts[0].headline)
        assertEquals("○ Setup answer", FamilyRoutineParts.learnedTag(false, false))
        assertEquals("✓ Learned", FamilyRoutineParts.learnedTag(true, false))
    }

    @Test
    fun `the bar is full once learned and counts days before that`() {
        val done = RoutineSummary.summarize(fingerprint())
        assertTrue(done.fullyLearned)
        assertEquals(1f, FamilyRoutineParts.learnedFraction(done), 0.001f)
        val early = RoutineSummary.summarize(fingerprint(days = 7, seed = true))
        assertEquals(0.5f, FamilyRoutineParts.learnedFraction(early), 0.001f)
    }
}
