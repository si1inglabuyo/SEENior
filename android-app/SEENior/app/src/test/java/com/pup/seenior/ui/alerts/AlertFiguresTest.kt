package com.pup.seenior.ui.alerts

import com.pup.seenior.database.entities.Alert
import com.pup.seenior.ui.routine.SeniorRoutineModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertFiguresTest {

    private fun alert(trigger: String, reading: Double?, usual: Double?, spread: Double?) = Alert(
        seniorId = 1, syncId = "x", triggerType = trigger, riskLevel = "medium", timeBlock = "afternoon",
        deviationScore = 2.8, readingValue = reading, usualValue = usual, usualSpread = spread
    )

    // 45 min usual, 6 min buffer, 70 min reading (the mock-up's example).
    private val idle = AlertFigures.of(alert("screen_idle", 70 * 60.0, 45 * 60.0, 6 * 60.0))!!
    private val still = AlertFigures.of(alert("inactivity", 140 * 60.0, 80 * 60.0, 12 * 60.0))!!

    @Test fun `an alert without stored figures has no comparison`() {
        assertNull(AlertFigures.of(alert("inactivity", null, null, null)))
        assertNull(AlertFigures.of(alert("inactivity", 100.0, 50.0, null)))
    }

    @Test fun `only the three Median-MAD triggers get a comparison`() {
        assertNull(AlertFigures.of(alert("fall_pattern", 1.0, 1.0, 1.0)))
        assertNull(AlertFigures.of(alert("ml_flag", 1.0, 1.0, 1.0)))
        assertNull(AlertFigures.of(alert("sos", 1.0, 1.0, 1.0)))
        assertEquals(AlertFigures.Kind.MOVEMENT, AlertFigures.of(alert("movement", 0.1, 0.4, 0.05))!!.kind)
    }

    @Test fun `the boxes and the reason carry the real figures`() {
        assertEquals("45 min", AlertFigures.usualValue(idle, false))
        assertEquals("1 hr 10 min", AlertFigures.readingValue(idle, false))
        assertEquals("± 6 min buffer", AlertFigures.bufferLabel(idle, false))
        assertEquals("25 min past usual", AlertFigures.pastLabel(idle, false))
        val en = AlertFigures.sentence(idle, false)
        assertTrue(en, en.contains("45 minutes") && en.contains("1 hour and 10 minutes") &&
            en.contains("25 minutes past your usual") && en.contains("6 minutes buffer"))
    }

    @Test fun `the stillness reason reads in hours and minutes`() {
        val en = AlertFigures.sentence(still, false)
        assertTrue(en, en.contains("1 hour and 20 minutes") && en.contains("2 hours and 20 minutes") &&
            en.contains("1 hour past your usual") && en.contains("12 minutes buffer"))
    }

    @Test fun `Filipino differs from English and uses the same figures`() {
        val fil = AlertFigures.sentence(idle, true)
        assertNotEquals(AlertFigures.sentence(idle, false), fil)
        assertTrue(fil, fil.contains("45 minuto") && fil.contains("1 oras at 10 minuto") && fil.contains("25 minuto"))
        assertEquals("Noong hapon".lowercase(), AlertFigures.thatLabel("afternoon", true).lowercase().let { it })
    }

    @Test fun `movement is shown as a percentage below the usual`() {
        val c = AlertFigures.of(alert("movement", 0.08, 0.42, 0.06))!!
        assertEquals("42%", AlertFigures.usualValue(c, false))
        assertEquals("8%", AlertFigures.readingValue(c, false))
        assertEquals("34% below usual", AlertFigures.pastLabel(c, false))
    }

    @Test fun `the working shows the sum the detector did`() {
        val text = AlertFigures.working(idle, "afternoon", 2.81, false)
        assertTrue(text, text.contains("usual (afternoon) = 45 min") && text.contains("buffer = 6 min") &&
            text.contains("reading = 1 hr 10 min") && text.contains("0.6745 × |70 − 45| ÷ 6 = 2.81"))
    }

    @Test fun `a gap under a minute never reads as zero minutes`() {
        val c = AlertFigures.of(alert("screen_idle", 3000.0, 2990.0, 10.0))!!
        assertTrue(AlertFigures.pastLabel(c, false).startsWith("1 min"))
    }

    @Test fun `daily pattern lists only the worrying side by a clear margin`() {
        val usual = SeniorRoutineModel.Figures(steps = 4800, unlocks = 38, stillSeconds = 7200, idleSeconds = 3600)
        val day = SeniorRoutineModel.Figures(steps = 1240, unlocks = 9, stillSeconds = 22800, idleSeconds = 3700)
        val items = AlertFigures.dailyItems(usual, day)
        assertEquals(listOf(SeniorRoutineModel.STEPS, SeniorRoutineModel.UNLOCKS, SeniorRoutineModel.STILL), items.map { it.feature })
        // A busier day than usual never appears.
        val busy = SeniorRoutineModel.Figures(steps = 9000, unlocks = 80, stillSeconds = 600, idleSeconds = 100)
        assertTrue(AlertFigures.dailyItems(usual, busy).isEmpty())
    }
}
