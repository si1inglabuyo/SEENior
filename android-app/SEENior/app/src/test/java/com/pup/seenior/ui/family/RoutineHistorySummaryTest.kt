package com.pup.seenior.ui.family

import com.pup.seenior.network.dto.RoutineHistoryRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineHistorySummaryTest {

    private fun row(
        day: String, seconds: Double, estimate: Boolean, block: String = "morning",
        feature: String = "inactivity_duration", samples: Int = 14
    ) = RoutineHistoryRowDto(day, feature, block, seconds, 60.0, samples, false, estimate)

    @Test
    fun `previous day is the latest day before today and never today itself`() {
        val today = java.time.LocalDate.parse("2026-11-15")
        val rows = listOf(
            row("2026-11-13", 2700.0, false), row("2026-11-14", 2760.0, false), row("2026-11-15", 2880.0, false),
        )
        val point = RoutineHistorySummary.previousDay(rows, "morning", today)!!
        assertEquals("2026-11-14", point.date)
        assertEquals(2760.0, point.seconds, 0.0)
        assertEquals("Yesterday, the usual was still for up to 46 min", RoutineHistorySummary.previousDayLabel(point, today, false))
    }

    @Test
    fun `previous day names the date when yesterday is missing`() {
        val today = java.time.LocalDate.parse("2026-11-15")
        val point = RoutineHistorySummary.previousDay(listOf(row("2026-11-12", 2700.0, false)), "morning", today)!!
        assertEquals("Nov 12, the usual was still for up to 45 min", RoutineHistorySummary.previousDayLabel(point, today, false))
    }

    @Test
    fun `previous day is hidden when it was only the setup answer or does not exist`() {
        val today = java.time.LocalDate.parse("2026-11-15")
        assertNull(RoutineHistorySummary.previousDay(listOf(row("2026-11-14", 2700.0, false, samples = 0)), "morning", today))
        assertNull(RoutineHistorySummary.previousDay(listOf(row("2026-11-15", 2700.0, false)), "morning", today))
        assertNull(RoutineHistorySummary.previousDay(listOf(row("2026-11-14", 2700.0, false, block = "night")), "morning", today))
    }

    @Test
    fun `a block that is only the setup answer is not drawn`() {
        // Seed cells carry a sample count of 0: nothing real has moved them, so a line would be flat at the guess.
        val trend = RoutineHistorySummary.summarize(
            listOf(
                row("2026-11-10", 2700.0, true, samples = 0), row("2026-11-11", 2700.0, true, samples = 0),
                row("2026-11-14", 2700.0, false, samples = 0),
            )
        )!!
        assertFalse(trend.anyLearned)
        assertFalse("the notes describe drawn lines, and none is drawn", trend.hasEstimate)
        assertNull(trend.firstSavedDate)
        assertNull("nothing to draw inside the Daily routine card", trend.forBlock("morning"))
    }

    @Test
    fun `one learned block is drawn while an unlearned one is not`() {
        val trend = RoutineHistorySummary.summarize(
            listOf(
                row("2026-11-10", 2700.0, true, "morning", samples = 0),
                row("2026-11-11", 3300.0, true, "morning", samples = 3),
                row("2026-11-10", 2700.0, true, "evening", samples = 0),
                row("2026-11-11", 2700.0, true, "evening", samples = 0),
            )
        )!!
        assertTrue(trend.anyLearned)
        assertTrue(trend.blocks.first { it.block == "morning" }.learned)
        assertFalse(trend.blocks.first { it.block == "evening" }.learned)
        assertEquals("morning", trend.forBlock("morning")?.block)
        assertNull(trend.forBlock("evening"))
    }

    @Test
    fun `one day is not a trend`() {
        assertNull(RoutineHistorySummary.summarize(listOf(row("2026-11-10", 1800.0, true))))
    }

    @Test
    fun `only the stillness figure is drawn, in block order`() {
        val rows = listOf(
            row("2026-11-10", 1800.0, true, "evening"), row("2026-11-11", 2400.0, true, "evening"),
            row("2026-11-10", 1200.0, true, "morning"), row("2026-11-11", 1500.0, true, "morning"),
            row("2026-11-10", 0.3, true, "morning", "movement_score"),
        )
        val trend = RoutineHistorySummary.summarize(rows)!!

        assertEquals(listOf("morning", "evening"), trend.blocks.map { it.block })
        assertEquals(2, trend.blocks.first().points.size)
    }

    @Test
    fun `start and latest are the earliest and the newest day`() {
        val trend = RoutineHistorySummary.summarize(
            listOf(row("2026-11-12", 3000.0, false), row("2026-11-10", 1800.0, true), row("2026-11-11", 2400.0, true))
        )!!
        val block = trend.blocks.single()

        assertEquals(1800.0, block.start.seconds, 0.0)
        assertEquals(3000.0, block.latest.seconds, 0.0)
        assertEquals("Started at a guess of 30 min, now 50 min", RoutineHistorySummary.startedAt(block, false))
    }

    @Test
    fun `the notes appear only for the kinds of day that exist`() {
        val onlyEstimates = RoutineHistorySummary.summarize(listOf(row("2026-11-10", 1.0, true), row("2026-11-11", 2.0, true)))!!
        assertTrue(onlyEstimates.hasEstimate)
        assertNull(onlyEstimates.firstSavedDate)

        val mixed = RoutineHistorySummary.summarize(
            listOf(row("2026-11-10", 1.0, true), row("2026-11-11", 2.0, false), row("2026-11-12", 3.0, false))
        )!!
        assertEquals("2026-11-11", mixed.firstSavedDate)

        val onlySaved = RoutineHistorySummary.summarize(listOf(row("2026-11-11", 2.0, false), row("2026-11-12", 3.0, false)))!!
        assertFalse(onlySaved.hasEstimate)
    }

    @Test
    fun `the wording is the plain sentences agreed with the team`() {
        assertEquals(
            "Earlier days are an estimate worked out from your parent's saved daily activity.",
            RoutineHistorySummary.estimateNote(false)
        )
        assertEquals("From Nov 14, the app saved this each night.", RoutineHistorySummary.savedNote("2026-11-14", false))
        assertEquals("Mula Nob 14, ito ay naitala ng app tuwing gabi.", RoutineHistorySummary.savedNote("2026-11-14", true))
        assertEquals(
            "Ang mga naunang araw ay tantiya lamang, batay sa naitalang pang-araw-araw na kilos.",
            RoutineHistorySummary.estimateNote(true)
        )
    }
}
