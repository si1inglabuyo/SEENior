package com.pup.seenior.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the gap-reconciliation rule by driving it with known inputs, per CLAUDE.md §10.
 *
 * The claim is that a flat step counter means two opposite things depending on whether anything
 * was feeding it, so the tests that matter are the pair: identical readings, identical flat step
 * count, two verdicts.
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
        // Two hours slept through, the counter watching throughout and seeing nothing. She really
        // was still, so this must NOT be capped — capping it would be the detector switching
        // itself off on exactly the reading it exists to catch.
        val verdict = reconcile(7200, gapMillis = 2 * 60 * 60 * 1000L, stepCountObserved = true)
        assertEquals(7200L, verdict.seconds)
        assertTrue(verdict is InactivityReconciler.Verdict.Believed)
    }

    @Test
    fun `a flat counter that never reported is silence and the gap is capped`() {
        // Byte-for-byte the same gap and the same zero steps. The only difference is that nothing
        // was ever feeding the counter — no TYPE_STEP_COUNTER, or ACTIVITY_RECOGNITION denied at
        // onboarding — so there is no witness and no stillness was observed.
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
        // TYPE_STEP_COUNTER restarts at zero on reboot, so the difference goes negative. That is a
        // reboot boundary, not proof she walked backwards, and it must not cap.
        val verdict = reconcile(
            7200, gapMillis = 2 * 60 * 60 * 1000L, stepCountObserved = true, stepsDuringGap = -9000
        )
        assertEquals(7200L, verdict.seconds)
    }

    @Test
    fun `an ordinary sampling interval is never reconciled`() {
        // Under twice the poll interval is jitter, not a suspend. Nothing was missed, so the
        // reading means what it says even with no step counter on the device at all.
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
        // The cap is a ceiling, not an assignment. A senior who moved two seconds ago across a
        // slept gap reads 2, and must not be written up to the listening window.
        val verdict = reconcile(2, gapMillis = 2 * 60 * 60 * 1000L, stepCountObserved = false)
        assertEquals(2L, verdict.seconds)
    }
}
