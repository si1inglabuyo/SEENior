package com.pup.seenior.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates Layer 0 by injecting known sensor streams, per CLAUDE.md §10 — the same reason the
 * detection engine is validated with simulated data rather than by waiting for a real emergency.
 *
 * The negative cases matter more than the positive one. A detector that fires on a real fall but
 * also on a phone tossed onto a bed produces alerts the family learns to ignore, which is worse
 * than no detector at all.
 */
class FallDetectorTest {

    @Test
    fun `confirms a fall with all three phases`() {
        assertTrue(FallSimulator.replay(FallSimulator.stream()))
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
