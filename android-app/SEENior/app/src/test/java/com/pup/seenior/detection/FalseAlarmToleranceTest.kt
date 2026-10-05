package com.pup.seenior.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates how confirmed false alarms loosen one block's Layer 1 trigger. What matters is
 * what must not move: one false alarm changes nothing, an extreme reading can't be argued
 * away, and the demo's injected z = 4.0 (see [AnomalySimulator]) can't teach the system.
 */
class FalseAlarmToleranceTest {

    private fun threshold(vararg scores: Double) = FalseAlarmTolerance.thresholdFor(scores.toList())

    private val delta = 1e-9

    // ------------------------------------------------------------ nothing changes without a pattern

    @Test
    fun `no false alarms leaves the moderate threshold alone`() {
        assertEquals(2.5, threshold(), delta)
    }

    @Test
    fun `one false alarm is not a pattern`() {
        assertEquals(2.5, threshold(2.9), delta)
    }

    // ------------------------------------------------------------ the raise

    @Test
    fun `two false alarms raise the threshold to their median plus the margin`() {
        assertEquals(2.85, threshold(2.6, 2.6), delta)
    }

    @Test
    fun `the raise follows how far the false alarms actually were`() {
        // Harmless stillness at z 3.0 earns more slack than harmless stillness at z 2.6.
        assertTrue(threshold(3.0, 3.0) > threshold(2.6, 2.6))
        assertEquals(3.25, threshold(3.0, 3.0), delta)
    }

    @Test
    fun `one odd score does not drag the threshold`() {
        // Median, not mean: 2.6, 2.6, 3.4 -> 2.6 (a mean would say ~2.87).
        assertEquals(2.85, threshold(2.6, 2.6, 3.4), delta)
    }

    // ------------------------------------------------------------ what must never move

    @Test
    fun `the threshold can never exceed the extreme line`() {
        val t = threshold(3.45, 3.49, 3.4, 3.48)
        assertTrue("was $t", t <= FalseAlarmTolerance.CAP)
        assertEquals(FalseAlarmTolerance.CAP, t, delta)
    }

    @Test
    fun `extreme scores are never evidence - the demo's injected z of 4 cannot loosen anything`() {
        assertEquals(2.5, threshold(4.0, 4.0), delta)
        assertEquals(2.5, threshold(4.0, 4.0, 5.2), delta)
    }

    @Test
    fun `a score exactly on the extreme line is not evidence either`() {
        assertEquals(2.5, threshold(3.5, 3.5), delta)
    }

    @Test
    fun `extreme scores do not count towards the minimum`() {
        // One moderate false alarm plus one extreme cancellation is still just one piece of evidence.
        assertEquals(2.5, threshold(2.8, 4.0), delta)
    }

    @Test
    fun `scores below the base threshold are ignored`() {
        // Can't occur in practice, but the function must never lower the threshold below where every block starts.
        assertEquals(2.5, threshold(1.0, 2.0), delta)
    }

    @Test
    fun `the threshold never drops below the base`() {
        assertTrue(threshold(2.5, 2.5) >= FalseAlarmTolerance.BASE_THRESHOLD)
    }

    // ------------------------------------------------------------ the window

    @Test
    fun `evidence lasts fourteen days, the same as the baseline`() {
        val now = 1_000_000_000_000L
        val fourteenDays = 14L * 24 * 60 * 60 * 1000
        assertEquals(now - fourteenDays, FalseAlarmTolerance.windowStart(now))
        assertEquals(14, FalseAlarmTolerance.WINDOW_DAYS)
    }

    // ------------------------------------------------------------ in minutes

    @Test
    fun `the extra tolerated stillness is the raise times this block's own MAD`() {
        // Median 40 min, MAD 16 min: 40 + 2.5*16 = 80 min before, 40 + 2.85*16 = 85.6 min after.
        val mad = 16.0
        val extra = (threshold(2.6, 2.6) - FalseAlarmTolerance.BASE_THRESHOLD) * mad
        assertEquals(5.6, extra, 1e-9)
    }

    // ------------------------------------------------------------ real alerts take the slack back

    private fun gate(falseAlarms: List<Double>, real: Int) = FalseAlarmTolerance.thresholdFor(falseAlarms, real)

    @Test
    fun `a real alert in the block takes back its share of the raise`() {
        // Two false alarms at 3.0 raise 2.5 to 3.25. One real alert keeps (2 - 1) / 2 of that raise.
        assertEquals(2.875, gate(listOf(3.0, 3.0), real = 1), delta)
    }

    @Test
    fun `as many real alerts as false alarms removes the raise entirely`() {
        assertEquals(2.5, gate(listOf(3.0, 3.0), real = 2), delta)
        assertEquals(2.5, gate(listOf(3.0, 3.0), real = 5), delta)
    }

    @Test
    fun `real alerts alone never move the gate, in either direction`() {
        assertEquals(2.5, gate(emptyList(), real = 3), delta)
        assertEquals(2.5, gate(listOf(3.0), real = 0), delta)
    }

    @Test
    fun `more false alarms keep more of the raise against the same real alert`() {
        assertTrue(gate(listOf(3.0, 3.0, 3.0, 3.0), real = 1) > gate(listOf(3.0, 3.0), real = 1))
    }

    @Test
    fun `the breakdown shows every step and agrees with the threshold`() {
        val b = FalseAlarmTolerance.explain(listOf(3.0, 3.0), realAlertCount = 1)
        assertEquals(3.0, b.medianZ!!, delta)
        assertEquals(3.25, b.gateBeforeRealAlerts, delta)
        assertEquals(0.5, b.keptShare, delta)
        assertEquals(2.875, b.gate, delta)
        assertEquals(b.gate, FalseAlarmTolerance.thresholdFor(listOf(3.0, 3.0), 1), delta)
    }

    @Test
    fun `the minutes added are the raise times the block's MAD`() {
        // MAD 1,108 s (18.5 min): 0.75 z is 13.9 min with no real alert, half that with one.
        val mad = 1108.0
        assertEquals(13.85, FalseAlarmTolerance.explain(listOf(3.0, 3.0)).extraMinutes(mad), 0.01)
        assertEquals(6.925, FalseAlarmTolerance.explain(listOf(3.0, 3.0), 1).extraMinutes(mad), 0.01)
    }
}
