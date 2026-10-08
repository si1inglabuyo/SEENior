package com.pup.seenior.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates Layer 0 by injecting known sensor streams, since real falls can't be collected
 * on demand. The negative cases matter more: a detector that also fires on a phone tossed
 * onto a bed produces alerts the family learns to ignore.
 */
class FallDetectorTest {

    @Test
    fun `confirms a fall with all three phases`() {
        assertTrue(FallSimulator.replay(FallSimulator.stream()))
    }

    @Test
    fun `records what each gate measured and it survives encoding`() {
        val detector = FallDetector()
        assertTrue(FallSimulator.replay(FallSimulator.stream(), detector))

        val evidence = detector.lastEvidence!!
        val cfg = FallDetector.Config()
        assertTrue(evidence.freeFallMs in cfg.freeFallMinMs..cfg.freeFallMaxMs)
        assertTrue(evidence.impactPeak >= cfg.impactMin)
        assertTrue(evidence.stillnessMs >= cfg.stillnessMs)
        assertTrue(evidence.rotationPeak!! >= cfg.rotationMin)
        assertEquals(evidence, FallEvidence.decode(evidence.encode()))
    }

    @Test
    fun `no evidence is recorded for a fall that did not confirm`() {
        val detector = FallDetector()
        assertFalse(FallSimulator.replay(FallSimulator.stream(standsUpAfterMs = 3_000), detector))
        assertNull(detector.lastEvidence)
    }

    @Test
    fun `unreadable evidence decodes to null`() {
        assertNull(FallEvidence.decode(null))
        assertNull(FallEvidence.decode("garbage"))
    }

    @Test
    fun `ignores an impact with no free fall`() {
        // A phone put down hard on a table: impact and stillness, but it never fell.
        assertFalse(FallSimulator.replay(FallSimulator.stream(freeFallMs = 0)))
    }

    @Test
    fun `ignores a drop that lands softly`() {
        // Onto a bed or a sofa cushion. Free fall and stillness, no impact spike.
        assertFalse(FallSimulator.replay(FallSimulator.stream(impactMagnitude = 12f)))
    }

    @Test
    fun `ignores a fall the senior gets up from`() {
        // They can move, so they are not the emergency this tier exists for.
        assertFalse(FallSimulator.replay(FallSimulator.stream(standsUpAfterMs = 3_000)))
    }

    @Test
    fun `ignores a brief weightless blip`() {
        // Shorter than a real drop — the phone being handed over or slipping in a pocket.
        assertFalse(FallSimulator.replay(FallSimulator.stream(freeFallMs = 40)))
    }

    @Test
    fun `does not confirm on acceleration alone when a gyroscope is present`() {
        val detector = FallDetector(FallDetector.Config(requireRotation = true))
        assertFalse(FallSimulator.replay(FallSimulator.stream(rotationPeak = 0.1f), detector))
    }

    @Test
    fun `confirms without rotation on a phone that has no gyroscope`() {
        val detector = FallDetector(FallDetector.Config(requireRotation = false))
        assertTrue(FallSimulator.replay(FallSimulator.stream(rotationPeak = 0f), detector))
    }

    @Test
    fun `reports a fall only once`() {
        val detector = FallDetector()
        val confirmations = FallSimulator.stream().count { sample ->
            detector.onRotation(sample.timestampNanos, sample.angularSpeed)
            detector.onAcceleration(sample.timestampNanos, sample.magnitude)
        }
        assertTrue("expected exactly one confirmation, got $confirmations", confirmations == 1)
    }
}
