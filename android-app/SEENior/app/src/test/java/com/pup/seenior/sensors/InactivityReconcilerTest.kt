package com.pup.seenior.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the gap-reconciliation rule with known inputs. A flat step counter means two
 * opposite things depending on whether anything was feeding it, so the key test is the pair:
 * identical readings and flat step count, two verdicts.
 */
class InactivityReconcilerTest {

    private val poll = 5 * 60 * 1000L
    private val listen = 12_000L
    private val listenSeconds = listen / 1000

    private fun reconcile(
        rawSeconds: Long,
        gapMillis: Long?,
        stepCountObserved: Boolean = true,
        stepsDuringGap: Int = 0,
    ) = InactivityReconciler.reconcile(
        rawSeconds, gapMillis, poll, listen, stepCountObserved, stepsDuringGap
    )

    // ------------------------------------------------------------------- the pair

    @Test
    fun `a flat counter that was awake is evidence and the long reading stands`() {
        // Two hours slept through, with the counter watching and seeing nothing. The senior
        // really was still, so this must not be capped.
        val verdict = reconcile(7200, gapMillis = 2 * 60 * 60 * 1000L, stepCountObserved = true)
        assertEquals(7200L, verdict.seconds)
        assertTrue(verdict is InactivityReconciler.Verdict.Believed)
    }

    @Test
    fun `a flat counter that never reported is silence and the gap is capped`() {
        // The same gap and zero steps, but nothing fed the counter (no sensor, or permission
        // denied), so there is no witness and no stillness was observed.
        val verdict = reconcile(7200, gapMillis = 2 * 60 * 60 * 1000L, stepCountObserved = false)
        assertEquals(listenSeconds, verdict.seconds)
        assertTrue(verdict is InactivityReconciler.Verdict.Capped)
    }

    // ------------------------------------------------------- the cases already relied on

    @Test
    fun `steps across the gap cap the reading`() {
        val verdict = reconcile(
            7200, gapMillis = 2 * 60 * 60 * 1000L, stepCountObserved = true, stepsDuringGap = 40
        )
        assertEquals(listenSeconds, verdict.seconds)
        assertTrue((verdict as InactivityReconciler.Verdict.Capped).reason.contains("40 step(s)"))
    }

    @Test
    fun `a reboot mid-gap reads as flat, not as negative steps`() {
        // The counter restarts at zero on reboot, so the difference goes negative. That's a reboot boundary and must not cap.
        val verdict = reconcile(
            7200, gapMillis = 2 * 60 * 60 * 1000L, stepCountObserved = true, stepsDuringGap = -9000
        )
        assertEquals(7200L, verdict.seconds)
    }

    @Test
    fun `an ordinary sampling interval is never reconciled`() {
        // Under twice the poll interval is jitter, not a suspend, so the reading stands even with no step counter.
        val verdict = reconcile(900, gapMillis = 5 * 60 * 1000L, stepCountObserved = false)
        assertEquals(900L, verdict.seconds)
        assertTrue(verdict is InactivityReconciler.Verdict.Believed)
    }

    @Test
    fun `the first sample of a run has no gap to reconcile`() {
        val verdict = reconcile(430, gapMillis = null, stepCountObserved = false)
        assertEquals(430L, verdict.seconds)
    }

    @Test
    fun `capping never inflates a short reading`() {
        // The cap is a ceiling, not an assignment: a movement two seconds ago across a slept gap reads 2.
        val verdict = reconcile(2, gapMillis = 2 * 60 * 60 * 1000L, stepCountObserved = false)
        assertEquals(2L, verdict.seconds)
    }
}
