package com.pup.seenior.detection

import com.pup.seenior.detection.FallSimulator.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Everyday scenarios injected into the real [FallDetector] with production thresholds.
 *
 * What this proves: the four-gate logic does what the design says, case by case. What it does
 * not prove: accuracy on real falls. Every stream below is built by hand from textbook shapes
 * (free fall near 0 g, a spike, then rest), not recorded from people, so a pass means "the logic
 * is consistent with the design", not "N% of real falls are caught". Accuracy needs a recorded
 * dataset (SisFall); see docs/fall-detection-references.md.
 */
class FallScenarioTest {

    private val stepMs = 20L
    private val upright = 9.81f

    /** Builds one 50 Hz stream phase by phase, same sample format as [FallSimulator]. */
    private class Builder {
        val samples = mutableListOf<Sample>()
        var nowMs = 0L
        private val upright = 9.81f

        fun emit(magnitude: Float, rotation: Float) {
            samples += Sample(nowMs * 1_000_000L, magnitude, rotation)
            nowMs += 20L
        }

        fun hold(durationMs: Long, magnitude: Float, rotation: Float) =
            repeat((durationMs / 20L).toInt()) { emit(magnitude, rotation) }

        /** Rhythmic motion: [peak] above one g, [hz] steps a second, [rotation] constant. */
        fun gait(durationMs: Long, peak: Float, hz: Float, rotation: Float) {
            val n = (durationMs / 20L).toInt()
            repeat(n) { i ->
                val t = i * 0.02f
                val wave = sin(2 * PI * hz * t).toFloat()
                emit(upright + peak * wave, rotation)
            }
        }
    }

    private fun confirms(samples: List<Sample>, config: FallDetector.Config = FallDetector.Config()) =
        FallSimulator.replay(samples, FallDetector(config))

    // ---- Falls that must be caught --------------------------------------------------------

    @Test
    fun `textbook fall is confirmed`() {
        assertTrue(confirms(FallSimulator.stream()))
    }

    @Test
    fun `a fall while walking fast is confirmed`() {
        // Brisk gait, then the trip: free fall, hard impact with tumbling, then lying still.
        val b = Builder()
        b.gait(5_000, peak = 6f, hz = 2.2f, rotation = 1.2f)
        b.hold(300, 1.5f, 3.5f)
        b.emit(38f, 4.0f)
        b.hold(300, 14f, 2.0f)
        b.hold(12_000, upright, 0.02f)
        assertTrue(confirms(b.samples))
    }

    @Test
    fun `a fall is still confirmed at the tolerance cap`() {
        // After six cancelled falls the gates are at their ceiling (impact 32.5, stillness 10.4 s).
        val capped = FallTolerance.resolve(FallDetector.Config(), falseAlarms = 6).config
        assertEquals(32.5f, capped.impactMin, 0.01f)
        assertTrue(confirms(FallSimulator.stream(), capped))
    }

    // ---- Everyday motion that must not alert -----------------------------------------------

    @Test
    fun `walking raises nothing`() {
        val b = Builder()
        b.gait(60_000, peak = 5f, hz = 1.8f, rotation = 1.0f)
        b.hold(12_000, upright, 0.02f)
        assertFalse(confirms(b.samples))
    }

    @Test
    fun `running raises nothing`() {
        // Hard strikes can pass the impact gate, but there is never a weightless phase.
        val b = Builder()
        b.gait(30_000, peak = 15f, hz = 2.8f, rotation = 2.5f)
        b.hold(12_000, upright, 0.02f)
        assertFalse(confirms(b.samples))
    }

    @Test
    fun `sitting down hard raises nothing`() {
        // A dip toward 6 m/s2, a thump of 30, then sitting still for a long time.
        val b = Builder()
        b.hold(2_000, upright, 0.05f)
        b.hold(200, 6f, 0.8f)
        b.emit(30f, 1.0f)
        b.hold(300, 12f, 0.5f)
        b.hold(20_000, upright, 0.02f)
        assertFalse(confirms(b.samples))
    }

    @Test
    fun `phone tossed on a bed raises nothing`() {
        // Real free fall, but the landing is soft.
        val b = Builder()
        b.hold(1_000, upright, 0.05f)
        b.hold(350, 1.2f, 3.0f)
        b.emit(14f, 3.0f)
        b.hold(300, 11f, 1.0f)
        b.hold(20_000, upright, 0.02f)
        assertFalse(confirms(b.samples))
    }

    @Test
    fun `phone set down hard on a table raises nothing`() {
        val b = Builder()
        b.hold(1_000, upright, 0.05f)
        b.emit(40f, 0.3f)
        b.hold(20_000, upright, 0.02f)
        assertFalse(confirms(b.samples))
    }

    @Test
    fun `a phone shaken in a pocket raises nothing`() {
        val b = Builder()
        b.gait(10_000, peak = 12f, hz = 4f, rotation = 3.0f)
        b.hold(12_000, upright, 0.02f)
        assertFalse(confirms(b.samples))
    }

    @Test
    fun `a dropped phone picked up straight away raises nothing`() {
        val b = Builder()
        b.hold(1_000, upright, 0.05f)
        b.hold(450, 1.2f, 3.5f)
        b.emit(45f, 3.5f)
        b.hold(300, 13f, 2.0f)
        b.hold(2_500, upright, 0.02f)
        b.hold(600, 16f, 2.0f)
        b.hold(12_000, upright, 0.02f)
        assertFalse(confirms(b.samples))
    }

    @Test
    fun `a phone dropped flat without tumbling raises nothing`() {
        // Low rotation: it fell flat, as a phone slipping off a nightstand does.
        val b = Builder()
        b.hold(1_000, upright, 0.05f)
        b.hold(450, 1.2f, 0.4f)
        b.emit(45f, 0.5f)
        b.hold(300, 13f, 0.3f)
        b.hold(14_000, upright, 0.02f)
        assertFalse(confirms(b.samples))
    }

    // ---- Known limitation, asserted so it can't be forgotten --------------------------------

    @Test
    fun `KNOWN LIMITATION - a hard drop that tumbles and then lies still confirms`() {
        // From a senior's hand to a hard floor: a tumbling drop with a hard landing and a phone that
        // then lies motionless looks exactly like a fall to a phone sensor. This is why a dropped
        // phone can raise a first false alarm, and why FallTolerance exists. Not a bug to hide.
        val b = Builder()
        b.hold(1_000, upright, 0.05f)
        b.hold(450, 1.2f, 4.0f)
        b.emit(45f, 4.0f)
        b.hold(300, 13f, 2.0f)
        b.hold(14_000, upright, 0.02f)
        assertTrue(confirms(b.samples))
    }

    // ---- Context: what the senior was doing before the drop ---------------------------------

    private fun contextOf(samples: List<Sample>): Pair<Float?, String> {
        val detector = FallDetector()
        assertTrue("the stream should confirm a fall", FallSimulator.replay(samples, detector))
        return detector.lastPreFallMotion to FallContext.encode(detector.lastPreFallMotion, "afternoon")
    }

    @Test
    fun `a fall while walking is recorded as active`() {
        val b = Builder()
        b.gait(5_000, peak = 6f, hz = 2.2f, rotation = 1.2f)
        b.hold(300, 1.5f, 3.5f)
        b.emit(38f, 4.0f)
        b.hold(300, 14f, 2.0f)
        b.hold(12_000, upright, 0.02f)
        val (motion, note) = contextOf(b.samples)
        assertTrue("walking should read well above ${FallContext.ACTIVE_MIN}, got $motion", motion!! >= FallContext.ACTIVE_MIN)
        assertTrue(note, FallContext.decode(note)!!.active == true)
    }

    @Test
    fun `a drop from rest is recorded as not active`() {
        // The phone lay still in a resting hand or on a bed until it fell.
        val (motion, note) = contextOf(FallSimulator.stream())
        assertTrue("rest should read below ${FallContext.ACTIVE_MIN}, got $motion", motion!! < FallContext.ACTIVE_MIN)
        assertTrue(note, FallContext.decode(note)!!.active == false)
    }

    @Test
    fun `motion is unknown when too little came before the drop`() {
        val b = Builder()
        b.hold(200, upright, 0.02f) // 10 samples, under the minimum
        b.hold(450, 1.2f, 4.0f)
        b.emit(45f, 4.0f)
        b.hold(300, 13f, 2.0f)
        b.hold(14_000, upright, 0.02f)
        val detector = FallDetector()
        assertTrue(FallSimulator.replay(b.samples, detector))
        assertEquals(null, detector.lastPreFallMotion)
        val note = FallContext.encode(null, "night")
        assertEquals("night=1", note)
        assertEquals(FallContext.Decoded(null, null, true), FallContext.decode(note))
    }

    @Test
    fun `the context note round trips and unreadable ones decode to null`() {
        val note = FallContext.encode(2.84f, "night")
        assertEquals("pre_dev=2.8;active=1;night=1", note)
        assertEquals(FallContext.Decoded(2.8f, true, true), FallContext.decode(note))
        assertEquals(null, FallContext.decode(null))
        assertEquals(null, FallContext.decode("garbage"))
    }

    @Test
    fun `the context does not change what the detector decides`() {
        // Same drop, once after walking and once from rest: both confirm, so context is a record only.
        val walking = Builder()
        walking.gait(5_000, peak = 6f, hz = 2.2f, rotation = 1.2f)
        walking.hold(450, 1.2f, 4.0f)
        walking.emit(45f, 4.0f)
        walking.hold(300, 13f, 2.0f)
        walking.hold(14_000, upright, 0.02f)
        assertTrue(confirms(walking.samples))
        assertTrue(confirms(FallSimulator.stream()))
    }

    // ---- Tolerance: what the raised gates do to the same drops ------------------------------

    @Test
    fun `after two cancelled falls a marginal drop no longer confirms`() {
        val raised = FallTolerance.resolve(FallDetector.Config(), falseAlarms = 2).config
        assertEquals(27.5f, raised.impactMin, 0.01f)
        val marginal = FallSimulator.stream(impactMagnitude = 26f, stillMs = 9_500)
        assertTrue("base gates should confirm it", confirms(marginal))
        assertFalse("raised gates should not", confirms(marginal, raised))
    }

    @Test
    fun `after two cancelled falls a short stillness no longer confirms`() {
        val raised = FallTolerance.resolve(FallDetector.Config(), falseAlarms = 2).config
        val shortRest = FallSimulator.stream(stillMs = 9_500)
        assertTrue(confirms(shortRest))
        assertFalse(confirms(shortRest, raised))
    }

    @Test
    fun `one cancelled fall changes nothing`() {
        val one = FallTolerance.resolve(FallDetector.Config(), falseAlarms = 1)
        assertFalse(one.raised)
        assertEquals(FallDetector.Config().impactMin, one.config.impactMin, 0.0f)
    }

    @Test
    fun `real falls take the raise back`() {
        val net = FallTolerance.resolve(FallDetector.Config(), falseAlarms = 2, realAlerts = 2)
        assertFalse(net.raised)
        assertEquals(FallDetector.Config().stillnessMs, net.config.stillnessMs)
    }

    @Test
    fun `the senior gets 120 seconds only while the raise holds`() {
        val raised = FallTolerance.resolve(FallDetector.Config(), falseAlarms = 2)
        assertEquals(120, FallTolerance.windowSeconds(raised.encode()))
        val cancelledOut = FallTolerance.resolve(FallDetector.Config(), falseAlarms = 2, realAlerts = 2)
        assertEquals(60, FallTolerance.windowSeconds(cancelledOut.encode()))
    }
}
