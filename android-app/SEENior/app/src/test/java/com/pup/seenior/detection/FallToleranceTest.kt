package com.pup.seenior.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates how cancelled false fall alarms raise Layer 0's bar. What matters is what must not
 * move: one cancellation changes nothing, the raise has a ceiling a real fall still clears, and
 * a real alert takes the slack back.
 */
class FallToleranceTest {

    private val base = FallDetector.Config()
    private val delta = 1e-6f

    private fun resolve(false_: Int, real: Int = 0) = FallTolerance.resolve(base, false_, real)

    // ------------------------------------------------------------ nothing changes without a pattern

    @Test
    fun `no false alarms leaves the detector alone`() {
        val r = resolve(0)
        assertFalse(r.raised)
        assertEquals(base, r.config)
    }

    @Test
    fun `one false alarm is not a pattern`() {
        val r = resolve(1)
        assertFalse(r.raised)
        assertEquals(base.impactMin, r.config.impactMin, delta)
        assertEquals(base.stillnessMs, r.config.stillnessMs)
    }

    // ------------------------------------------------------------ the raise

    @Test
    fun `two false alarms raise impact and stillness by ten percent`() {
        val r = resolve(2)
        assertTrue(r.raised)
        assertEquals(27.5f, r.config.impactMin, delta)
        assertEquals(8_800L, r.config.stillnessMs)
    }

    @Test
    fun `more false alarms raise it further`() {
        assertTrue(resolve(4).config.impactMin > resolve(2).config.impactMin)
    }

    @Test
    fun `only impact and stillness move`() {
        val c = resolve(5).config
        assertEquals(base.freeFallMax, c.freeFallMax, delta)
        assertEquals(base.freeFallMinMs, c.freeFallMinMs)
        assertEquals(base.rotationMin, c.rotationMin, delta)
        assertEquals(base.requireRotation, c.requireRotation)
    }

    // ------------------------------------------------------------ the ceiling

    @Test
    fun `the raise is capped at thirty percent however many false alarms`() {
        val r = resolve(50)
        assertEquals(32.5f, r.config.impactMin, delta)
        assertEquals(10_400L, r.config.stillnessMs)
    }

    @Test
    fun `a textbook fall still confirms at the maximum raise`() {
        val worst = resolve(50).config
        assertTrue(FallSimulator.replay(FallSimulator.stream(), FallDetector(worst)))
    }

    // ------------------------------------------------------------ real falls take the slack back

    @Test
    fun `each real alert cancels its share of the raise`() {
        // 4 false alarms earn 20%; 2 real ones keep half of it.
        val r = resolve(4, real = 2)
        assertEquals(0.10, r.raise, 1e-9)
        assertEquals(27.5f, r.config.impactMin, delta)
    }

    @Test
    fun `the raise is gone once real alerts match false alarms`() {
        assertFalse(resolve(3, real = 3).raised)
        assertFalse(resolve(3, real = 9).raised)
        assertEquals(base, resolve(3, real = 3).config)
    }

    // ------------------------------------------------------------ the detector honours it

    private val marginalDrop = FallSimulator.stream(impactMagnitude = 27f, stillMs = 9_300)

    @Test
    fun `a marginal drop confirms at the base thresholds`() {
        assertTrue(FallSimulator.replay(marginalDrop, FallDetector(base)))
    }

    @Test
    fun `the same marginal drop is filtered once the bar is raised`() {
        assertFalse(FallSimulator.replay(marginalDrop, FallDetector(resolve(2).config)))
    }

    @Test
    fun `applyConfig moves a running detector`() {
        val detector = FallDetector(base)
        detector.applyConfig(resolve(2).config)
        assertFalse(FallSimulator.replay(marginalDrop, detector))
    }

    // ------------------------------------------------------------ what is stored and synced

    @Test
    fun `the note round trips and says what earned it`() {
        val r = resolve(2)
        val note = r.encode()
        assertEquals("impact_min=27.5;base_impact=25.0;still_ms=8800;base_still=8000;false=2;real=0", note)
        val back = FallTolerance.decodeNote(note)!!
        assertEquals(r.config.impactMin, back.config.impactMin, delta)
        assertEquals(r.config.stillnessMs, back.config.stillnessMs)
        assertEquals(2, back.falseAlarms)
        assertEquals(0, back.realAlerts)
        assertTrue(back.raised)
    }

    @Test
    fun `the note carries settings and counts, never a sensor reading`() {
        val keys = resolve(3).encode().split(';').map { it.substringBefore('=') }.toSet()
        assertEquals(setOf("impact_min", "base_impact", "still_ms", "base_still", "false", "real"), keys)
    }

    @Test
    fun `an unraised note says so`() {
        val back = FallTolerance.decodeNote(resolve(0).encode())!!
        assertFalse(back.raised)
    }

    @Test
    fun `unreadable notes decode to null`() {
        assertNull(FallTolerance.decodeNote(null))
        assertNull(FallTolerance.decodeNote("garbage"))
        assertNull(FallTolerance.decode(null))
    }
}
