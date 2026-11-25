package com.pup.seenior.ui.wellness

import com.pup.seenior.database.entities.Alert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessWhyTest {

    private fun alert(trigger: String, reading: Double?, usual: Double?, spread: Double?, block: String = "afternoon") = Alert(
        seniorId = 1, syncId = "x", triggerType = trigger, riskLevel = "medium", timeBlock = block,
        deviationScore = 2.8, readingValue = reading, usualValue = usual, usualSpread = spread
    )

    // 45 min usual, 6 min spread, 70 min reading: the same figures AlertFiguresTest uses.
    private val idle = alert("screen_idle", 70 * 60.0, 45 * 60.0, 6 * 60.0)

    @Test fun `the asking point is the usual plus 2_5 over 0_6745 spreads`() {
        val why = WellnessWhy.of(idle, false)!!
        // 45 + 3.7064 * 6 = 67.2 min
        assertEquals(45, why.usualMinutes)
        assertEquals(67, why.askMinutes)
        assertEquals(22, why.bufferMinutes)
        assertEquals("1 hr 7 min", why.askValue)
        assertEquals("+ 22 min", why.bufferValue)
    }

    @Test fun `the gap line carries the real difference`() {
        val why = WellnessWhy.of(idle, false)!!
        assertEquals("25 minutes", why.gapAmount)
        assertEquals("longer than usual.", why.gapLine)
        assertEquals("This afternoon", why.readingLabel)
        assertEquals("1 hr 10 min", why.readingValue)
    }

    @Test fun `Filipino names the block and keeps the Tagalog buffer word`() {
        val why = WellnessWhy.of(idle, true)!!
        assertEquals("Ngayong hapon", why.readingLabel)
        assertEquals("Palugit", why.bufferName)
        assertTrue(why.reasonLine, why.reasonLine.contains("hapon"))
        assertTrue(why.gapLine, why.gapLine.contains("mas matagal"))
    }

    @Test fun `movement, falls and old alerts keep the single sentence`() {
        assertNull(WellnessWhy.of(alert("movement", 0.1, 0.4, 0.05), false))
        assertNull(WellnessWhy.of(alert("fall_pattern", 1.0, 1.0, 1.0), false))
        assertNull(WellnessWhy.of(alert("inactivity", null, null, null), false))
    }

    @Test fun `a reading that is not past the usual has no card`() {
        assertNull(WellnessWhy.of(alert("inactivity", 30 * 60.0, 45 * 60.0, 6 * 60.0), false))
    }

    @Test fun `a tiny spread still leaves a buffer of at least one minute`() {
        val why = WellnessWhy.of(alert("inactivity", 50 * 60.0, 45 * 60.0, 1.0), false)
        assertNotNull(why)
        assertTrue(why!!.bufferMinutes >= 1)
    }
}
