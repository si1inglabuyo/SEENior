package com.pup.seenior.ui.family

import com.pup.seenior.network.dto.RoutineRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSummaryTest {

    private fun row(feature: String, block: String, median: Double, days: Int = 14, seed: Boolean = false) =
        RoutineRowDto(feature, block, median, 1.0, days, seed)

    /** movement, steps, unlocks and stillness for each block. */
    private fun fingerprint(
        movement: List<Double>,
        steps: List<Double> = listOf(100.0, 800.0, 400.0, 10.0),
        unlocks: List<Double> = listOf(5.0, 9.0, 3.0, 0.0),
        still: List<Double> = listOf(1200.0, 600.0, 1800.0, 25000.0),
        days: Int = 14,
        seed: Boolean = false
    ): List<RoutineRowDto> {
        val blocks = listOf("morning", "afternoon", "evening", "night")
        return blocks.indices.flatMap { i ->
            listOf(
                row("movement_score", blocks[i], movement[i], days, seed),
                row("step_count", blocks[i], steps[i], days, seed),
                row("screen_unlock_count", blocks[i], unlocks[i], days, seed),
                row("inactivity_duration", blocks[i], still[i], days, seed)
            )
        }
    }

    private val typical = fingerprint(movement = listOf(0.04, 0.10, 0.06, 0.01))

    @Test
    fun theBusiestPartOfTheDayIsRankedAgainstTheSameSeniorsOthers() {
        val s = RoutineSummary.summarize(typical)
        assertEquals("afternoon", s.busiest)
        assertEquals("busiest", s.blocks.first { it.block == "afternoon" }.level)
        assertEquals("quiet", s.blocks.first { it.block == "night" }.level)
        assertEquals("night", s.quietest)
    }

    @Test
    fun aQuietSeniorIsNotLabelledLowOnlyQuieterAtNight() {
        // Every number is tiny, yet the shape of the day is still described.
        val s = RoutineSummary.summarize(
            fingerprint(movement = listOf(0.004, 0.010, 0.006, 0.001), steps = listOf(10.0, 80.0, 40.0, 1.0))
        )
        assertEquals("afternoon", s.busiest)
        assertEquals("night", s.quietest)
    }

    @Test
    fun noActivityMeansNoHeadline() {
        val s = RoutineSummary.summarize(
            fingerprint(movement = listOf(0.0, 0.0, 0.0, 0.0), steps = listOf(0.0, 0.0, 0.0, 0.0))
        )
        assertNull(s.busiest)
        assertNull(RoutineSummary.headline(s, false))
        assertTrue(s.blocks.all { it.level == "quiet" })
    }

    @Test
    fun headlineStatesAFactInBothLanguages() {
        val s = RoutineSummary.summarize(typical)
        assertEquals("Busiest in the afternoon; quietest at night.", RoutineSummary.headline(s, false))
        assertEquals("Pinakaaktibo sa hapon; pinakatahimik sa oras ng tulog.", RoutineSummary.headline(s, true))
    }

    @Test
    fun phoneUseUsesFixedCutOffs() {
        val blocks = RoutineSummary.summarize(typical).blocks.associateBy { it.block }
        assertEquals("sometimes", blocks.getValue("morning").phone)   // 5 unlocks
        assertEquals("often", blocks.getValue("afternoon").phone)     // 9
        assertEquals("rarely", blocks.getValue("night").phone)        // 0
    }

    @Test
    fun progressCountsDaysNotValues() {
        val learning = RoutineSummary.summarize(fingerprint(listOf(0.04, 0.1, 0.06, 0.01), days = 5, seed = true))
        assertEquals(5, learning.days)
        assertEquals("Still learning: day 5 of 14.", RoutineSummary.progress(learning, false))

        val fresh = RoutineSummary.summarize(fingerprint(listOf(0.04, 0.1, 0.06, 0.01), days = 0, seed = true))
        assertEquals("Just started: based on the setup answers for now.", RoutineSummary.progress(fresh, false))

        val done = RoutineSummary.summarize(typical)
        assertEquals("Learned from the last 14 days.", RoutineSummary.progress(done, false))
    }

    @Test
    fun missingBlocksAreSkippedNotInvented() {
        val onlyMorning = typical.filter { it.timeBlock == "morning" }
        val s = RoutineSummary.summarize(onlyMorning)
        assertEquals(1, s.blocks.size)
        assertNull(s.quietest)
    }
}
