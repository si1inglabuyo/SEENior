package com.pup.seenior.ui.wellness

import com.pup.seenior.database.entities.Alert
import com.pup.seenior.detection.FallEvidence
import com.pup.seenior.ui.alerts.AlertFigures
import com.pup.seenior.ui.routine.SeniorRoutineModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WellnessWhyTest {

    private val zone = ZoneId.of("Asia/Manila")

    /** 9:30 AM on a morning in November, the moment the phone asked. */
    private val askedAt = LocalDateTime.of(2026, 11, 25, 9, 30).atZone(zone).toInstant().toEpochMilli()

    private fun alert(
        trigger: String, reading: Double?, usual: Double?, spread: Double?,
        block: String = "morning", at: Long = askedAt, evidence: String? = null
    ) = Alert(
        seniorId = 1, syncId = "x", triggerType = trigger, riskLevel = "medium", timeBlock = block,
        deviationScore = 2.8, readingValue = reading, usualValue = usual, usualSpread = spread,
        triggeredAt = at, fallEvidence = evidence
    )

    // Usual 80 min, spread 16 min, reading 140 min: asked at 9:30, so last moved at 7:10.
    private val still = alert("inactivity", 140 * 60.0, 80 * 60.0, 16 * 60.0)

    @Test fun `a routine anomaly shows the usual, the last movement and when it asked`() {
        val why = WellnessWhy.of(still, false, zone = zone)!!
        assertEquals(WellnessWhy.Kind.STILLNESS, why.kind)
        assertEquals(listOf("Your usual", "Last moved", "Phone asked"), why.rows.map { it.label })
        assertEquals("up to 1 hr 20 min", why.rows[0].value)
        assertEquals("7:10 AM", why.rows[1].value)
        assertEquals("9:30 AM", why.rows[2].value)
    }

    @Test fun `the line under asked is the usual plus the extra that follows the senior's own days`() {
        // 80 + 3.7064 * 16 = 139.3 min, so the extra is 59 min.
        val why = WellnessWhy.of(still, false, zone = zone)!!
        assertEquals("Your usual 1 hr 20 min + 59 min extra", why.rows[2].small)
        assertTrue(why.rows[2].highlight)
    }

    @Test fun `a phone-untouched alert says last used phone`() {
        val idle = alert("screen_idle", 140 * 60.0, 80 * 60.0, 16 * 60.0)
        val why = WellnessWhy.of(idle, false, zone = zone)!!
        assertEquals("Last used phone", why.rows[1].label)
        assertEquals("7:10 AM", why.rows[1].value)
    }

    @Test fun `Filipino uses oras and the Filipino labels`() {
        val why = WellnessWhy.of(still, true, zone = zone)!!
        assertEquals("Huling gumalaw", why.rows[1].label)
        assertEquals("Nagtanong ang phone", why.rows[2].label)
        assertEquals("hanggang 1 oras 20 min", why.rows[0].value)
        assertTrue(why.rows[2].small!!, why.rows[2].small!!.startsWith("Karaniwan na 1 oras 20 min"))
    }

    @Test fun `movement, old alerts and a reading inside the usual keep the single sentence`() {
        assertNull(WellnessWhy.of(alert("movement", 0.1, 0.4, 0.05), false, zone = zone))
        assertNull(WellnessWhy.of(alert("inactivity", null, null, null), false, zone = zone))
        assertNull(WellnessWhy.of(alert("inactivity", 30 * 60.0, 45 * 60.0, 6 * 60.0), false, zone = zone))
        assertNull(WellnessWhy.of(alert("charging", 1.0, 1.0, 1.0), false, zone = zone))
    }

    @Test fun `a tiny spread still leaves an extra of at least one minute`() {
        val why = WellnessWhy.of(alert("inactivity", 50 * 60.0, 45 * 60.0, 1.0), false, zone = zone)
        assertNotNull(why)
        assertTrue(why!!.rows[2].small!!.contains("1 min extra"))
    }

    @Test fun `a fall shows its four checks with the English names in both languages`() {
        val evidence = FallEvidence(freeFallMs = 400, impactPeak = 35.0f, stillnessMs = 10_500, rotationPeak = 4.5f).encode()
        val fall = alert("fall_pattern", null, null, null, evidence = evidence)
        val en = WellnessWhy.of(fall, false, zone = zone)!!
        assertEquals(WellnessWhy.Kind.FALL, en.kind)
        assertEquals(listOf("Free fall", "Impact", "Rotation", "Stillness"), en.rows.map { it.label })
        assertEquals(listOf("Free fall", "Impact", "Rotation", "Stillness"), WellnessWhy.of(fall, true, zone = zone)!!.rows.map { it.label })
        assertTrue(en.rows[1].small!!, en.rows[1].small!!.contains("needed 25.0 or more"))
    }

    @Test fun `a fall without a turn sensor shows three checks, and one without evidence has no card`() {
        val noGyro = FallEvidence(freeFallMs = 400, impactPeak = 35.0f, stillnessMs = 10_500, rotationPeak = null).encode()
        assertEquals(3, WellnessWhy.of(alert("fall_pattern", null, null, null, evidence = noGyro), false, zone = zone)!!.rows.size)
        assertNull(WellnessWhy.of(alert("fall_pattern", null, null, null), false, zone = zone))
    }

    @Test fun `the daily pattern lists the signals that leaned the worrying way`() {
        val items = listOf(
            AlertFigures.Item(SeniorRoutineModel.STEPS, 610.0, 1970.0, true),
            AlertFigures.Item(SeniorRoutineModel.UNLOCKS, 4.0, 15.0, true)
        )
        val why = WellnessWhy.of(alert("ml_flag", null, null, null), false, items, zone)!!
        assertEquals(WellnessWhy.Kind.DAILY, why.kind)
        assertEquals(2, why.rows.size)
        assertEquals("610", why.rows[0].value)
        assertTrue(why.rows[0].small!!, why.rows[0].small!!.startsWith("usual"))
        assertTrue(why.footnote!!, why.footnote!!.contains("A busier day than usual never raises this."))
    }

    @Test fun `a daily-pattern alert with nothing to show keeps the single sentence`() {
        assertNull(WellnessWhy.of(alert("ml_flag", null, null, null), false, emptyList(), zone))
    }
}
