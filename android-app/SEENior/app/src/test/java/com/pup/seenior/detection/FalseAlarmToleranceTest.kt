package com.pup.seenior.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates how confirmed false alarms loosen one block's Layer 1 trigger, per the spec §10.
 *
 * What matters is what must *not* move: one false alarm changes nothing, an extreme reading can
 * never be argued away, and the demo's injected z = 4.0 reading (see [AnomalySimulator]) cannot
 * teach the system anything when the presenter cancels it.
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
        // Cannot occur in practice (nothing under 2.5 becomes an alert), but the function must
        // not be able to lower the threshold below where every block starts.
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
}
