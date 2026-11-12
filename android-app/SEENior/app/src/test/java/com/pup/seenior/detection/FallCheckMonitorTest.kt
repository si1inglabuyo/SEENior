package com.pup.seenior.detection

import com.pup.seenior.detection.FallCheckMonitor.Gate
import com.pup.seenior.detection.FallCheckMonitor.Outcome
import com.pup.seenior.detection.FallCheckMonitor.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live fall check must agree with the real detector, gate by gate. */
class FallCheckMonitorTest {

    private fun run(samples: List<FallSimulator.Sample>, monitor: FallCheckMonitor = FallCheckMonitor()): Boolean =
        samples.fold(false) { confirmed, s ->
            monitor.onRotation(s.timestampNanos, s.angularSpeed)
            monitor.onAcceleration(s.timestampNanos, s.magnitude) || confirmed
        }

    @Test
    fun `a textbook fall passes all four gates and agrees with the detector`() {
        val samples = FallSimulator.stream()
        val monitor = FallCheckMonitor()
        val confirmed = run(samples, monitor)
        val snap = monitor.snapshot()

        assertEquals(FallSimulator.replay(samples), confirmed)
        assertTrue(confirmed)
        assertEquals(Outcome.CONFIRMED, snap.outcome)
        Gate.entries.forEach { assertEquals(it.name, Status.PASSED, snap.statuses[it]) }
        assertEquals(35f, snap.impactPeak!!, 0.01f)
        assertTrue(snap.freeFallMs!! in 100..2_000)
        assertTrue(snap.rotationPeak!! >= 2.0f)
        assertTrue(snap.stillMs!! >= 8_000)
    }

    @Test
    fun `a soft landing fails at impact and shows how hard it was`() {
        val monitor = FallCheckMonitor()
        run(FallSimulator.stream(impactMagnitude = 18f), monitor)
        val snap = monitor.snapshot()
        assertEquals(Outcome.NOT_CONFIRMED, snap.outcome)
        assertEquals(Status.PASSED, snap.statuses[Gate.FREE_FALL])
        assertEquals(Status.FAILED, snap.statuses[Gate.IMPACT])
        assertNotNull(snap.impactPeak)
        assertTrue(snap.impactPeak!! < 25f)
    }

    @Test
    fun `getting up straight away fails at stillness`() {
        val monitor = FallCheckMonitor()
        val confirmed = run(FallSimulator.stream(standsUpAfterMs = 3_000), monitor)
        val snap = monitor.snapshot()
        assertEquals(false, confirmed)
        assertEquals(Status.PASSED, snap.statuses[Gate.IMPACT])
        assertEquals(Status.FAILED, snap.statuses[Gate.STILLNESS])
    }

    @Test
    fun `a fall without tumbling fails at rotation`() {
        val monitor = FallCheckMonitor()
        run(FallSimulator.stream(rotationPeak = 0.5f), monitor)
        val snap = monitor.snapshot()
        assertEquals(Outcome.NOT_CONFIRMED, snap.outcome)
        assertEquals(Status.FAILED, snap.statuses[Gate.ROTATION])
        assertEquals(Status.PASSED, snap.statuses[Gate.STILLNESS])
    }

    @Test
    fun `without a gyroscope the rotation gate is shown as not used`() {
        val monitor = FallCheckMonitor(FallDetector.Config(requireRotation = false))
        run(FallSimulator.stream(), monitor)
        val snap = monitor.snapshot()
        assertEquals(Status.NOT_USED, snap.statuses[Gate.ROTATION])
        assertEquals(Outcome.CONFIRMED, snap.outcome)
    }

    @Test
    fun `reset clears the panel and lets the next fall be judged straight away`() {
        val monitor = FallCheckMonitor()
        run(FallSimulator.stream(), monitor)
        monitor.reset()
        assertEquals(Outcome.NONE, monitor.snapshot().outcome)
        // A second fall right after: without reset the detector's one-minute pause would ignore it.
        assertTrue(run(FallSimulator.stream(), monitor))
    }

    @Test
    fun `with no cooldown a second fall right after the first is judged too`() {
        val monitor = FallCheckMonitor(FallDetector.Config(cooldownMs = 0))
        val first = FallSimulator.stream()
        assertTrue(run(first, monitor))
        // The second fall starts after the first, on the same clock, with no reset in between.
        val offset = first.last().timestampNanos + 20_000_000L
        val second = FallSimulator.stream().map { it.copy(timestampNanos = it.timestampNanos + offset) }
        assertTrue(run(second, monitor))
        assertEquals(Outcome.CONFIRMED, monitor.snapshot().outcome)
    }

    @Test
    fun `the real detector's one-minute cooldown would have ignored that second fall`() {
        val monitor = FallCheckMonitor()
        val first = FallSimulator.stream()
        run(first, monitor)
        val offset = first.last().timestampNanos + 20_000_000L
        val second = FallSimulator.stream().map { it.copy(timestampNanos = it.timestampNanos + offset) }
        assertEquals(false, run(second, monitor))
    }
}
