package com.pup.seenior.ui.family

import com.pup.seenior.ui.family.FallContextSummary.Motion
import com.pup.seenior.ui.wellness.WellnessMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallContextSummaryTest {

    private val raised = "impact_min=27.5;base_impact=25.0;still_ms=8800;base_still=8000;false=2;real=0"
    private val notRaised = "impact_min=25.0;base_impact=25.0;still_ms=8000;base_still=8000;false=0;real=0"

    @Test
    fun `a fall while moving is summarised as moving`() {
        val s = FallContextSummary.from("pre_dev=4.7;active=1;night=0", null)!!
        assertEquals(Motion.MOVING, s.motion)
        assertFalse(s.sleeping)
        assertFalse(s.lessSensitive)
    }

    @Test
    fun `a drop from rest in sleep hours is summarised as at rest and sleeping`() {
        val s = FallContextSummary.from("pre_dev=0.0;active=0;night=1", null)!!
        assertEquals(Motion.AT_REST, s.motion)
        assertTrue(s.sleeping)
    }

    @Test
    fun `unknown motion stays unknown`() {
        assertEquals(Motion.UNKNOWN, FallContextSummary.from("night=0", null)!!.motion)
    }

    @Test
    fun `a raised detector is flagged and a normal one is not`() {
        assertTrue(FallContextSummary.from("pre_dev=0.1;active=0;night=1", raised)!!.lessSensitive)
        assertFalse(FallContextSummary.from("pre_dev=0.1;active=0;night=1", notRaised)!!.lessSensitive)
    }

    @Test
    fun `no note or an unreadable note gives nothing`() {
        assertNull(FallContextSummary.from(null, null))
        assertNull(FallContextSummary.from("garbage", null))
    }

    @Test
    fun `the sentences carry no figures in either language`() {
        val summary = FallContextSummary.from("pre_dev=4.7;active=1;night=0", raised)!!
        for (language in listOf(WellnessMessages.ENGLISH, WellnessMessages.FILIPINO)) {
            val lines = FamilyStrings.forLanguage(language).fallContextLines(summary, "Maria")
            assertTrue(lines.isNotEmpty())
            lines.forEach { line ->
                assertFalse("no digits expected in: $line", line.any { it.isDigit() })
                assertTrue(line.contains("Maria"))
            }
        }
    }

    @Test
    fun `a rested drop says it could have been dropped and asks the family to check`() {
        val summary = FallContextSummary.from("pre_dev=0.0;active=0;night=1", null)!!
        val lines = FamilyStrings.forLanguage(WellnessMessages.ENGLISH).fallContextLines(summary, "Maria")
        assertTrue(lines.first().contains("could simply have been dropped"))
        assertTrue(lines.last().contains("Please check on Maria"))
    }
}
