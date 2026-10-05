package com.pup.seenior.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo's explanation must agree with the detector's own arithmetic and leave no number unsourced. */
class ToleranceExplanationTest {

    // The pilot phone's morning inactivity baseline: median 2,753 s (45.9 min), MAD 1,108 s (18.5 min).
    private val baseline = ToleranceExplanation.BaselineFacts(
        medianSeconds = 2753.0, rawMadSeconds = 1108.0, madFloorSeconds = 300.0, sampleCount = 9, isSeed = false
    )

    private fun steps(real: Int = 0, scores: List<Double> = listOf(3.0, 3.0)) = ToleranceExplanation.steps(
        "inactivity", "morning", FalseAlarmTolerance.explain(scores, real), baseline
    )

    @Test
    fun `two false alarms walk through to plus 13 point 9 minutes`() {
        val text = steps().joinToString("\n") { it.formula }
        assertTrue(text, "3.25" in text)
        assertTrue(text, "+13.9 min" in text)
        assertTrue(text, "before 92.1 min  ->  now 105.9 min" in text)
    }

    @Test
    fun `a real alert halves the minutes and the explanation says so`() {
        val text = steps(real = 1).joinToString("\n") { it.formula }
        assertTrue(text, "(2 - 1) / 2 = 0.50" in text)
        assertTrue(text, "+6.9 min" in text)
    }

    @Test
    fun `every number is sourced and every step says why`() {
        for (step in steps()) {
            assertTrue(step.title, step.why.isNotBlank())
            assertTrue(step.title, step.facts.isNotEmpty())
            step.facts.forEach { assertTrue("${step.title}: ${it.value}", it.meaning.isNotBlank()) }
        }
    }

    @Test
    fun `the margin is labelled as a chosen constant and not a calculation`() {
        val margin = steps().flatMap { it.facts }.first { it.value == "0.25" }
        assertEquals(ToleranceExplanation.Origin.CHOSEN, margin.origin)
    }

    @Test
    fun `without enough false alarms the explanation stops and says nothing is added`() {
        val short = steps(scores = listOf(3.0))
        assertEquals("3. Not enough false alarms yet", short.last().title)
    }

    @Test
    fun `the explained gate is the detector's gate`() {
        val gate = FalseAlarmTolerance.thresholdFor(listOf(3.0, 3.0), 1)
        assertTrue(steps(real = 1).joinToString("\n") { it.formula }.contains("%.2f".format(gate)))
    }
}
