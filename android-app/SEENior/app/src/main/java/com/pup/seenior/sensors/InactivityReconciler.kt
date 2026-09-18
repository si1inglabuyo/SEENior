package com.pup.seenior.sensors

/**
 * The decision half of [SensorCollectionService.reconcileInactivity], with the logging and the
 * Android types left behind.
 *
 * Extracted so JUnit can drive it, for the same reason [com.pup.seenior.detection.FallDetector]
 * has no Android imports (CLAUDE.md §10). The question it answers has been got wrong twice on real
 * data and both times it cost a baseline, so it is worth being able to state the three cases as
 * tests rather than reasoning about them in a Service that needs a device to run.
 */
object InactivityReconciler {

    /** What to record for a gap, and why — [Capped.reason] is the Service's log line. */
    sealed interface Verdict {
        val seconds: Long

        /** The reading is believed exactly as measured. */
        data class Believed(override val seconds: Long) : Verdict

        /** The reading is cut down to the listening window. */
        data class Capped(override val seconds: Long, val reason: String) : Verdict
    }

    /**
     * @param rawSeconds        the inactivity counter as read, seconds since the last movement.
     * @param gapMillis         how long since the previous sample; null when there is no previous.
     * @param pollIntervalMs    the normal sampling period. A gap under twice this was not a sleep.
     * @param listenWindowMs    how long the service actually listens each poll — the most
     *                          stillness a single sample can honestly attest to.
     * @param stepCountObserved whether the step counter has ever reported. False means there is no
     *                          witness, not that the senior took no steps.
     * @param stepsDuringGap    rise in the step counter across the gap. Zero or less is either "no
     *                          steps" or a reboot restarting the counter; both are read as flat.
     */
    fun reconcile(
        rawSeconds: Long,
        gapMillis: Long?,
        pollIntervalMs: Long,
        listenWindowMs: Long,
        stepCountObserved: Boolean,
        stepsDuringGap: Int,
    ): Verdict {
        // Nothing to reconcile against: the first sample of a run has no gap behind it.
        if (gapMillis == null) return Verdict.Believed(rawSeconds)

        // A gap this short is ordinary sampling jitter, not a suspend. The counter was watched
        // throughout, so the reading means what it says.
        if (gapMillis <= pollIntervalMs * 2) return Verdict.Believed(rawSeconds)

        val listenWindowSeconds = listenWindowMs / 1000
        val capped = minOf(rawSeconds, listenWindowSeconds)

        // No witness. The flat step count below would be silence, not testimony, and believing it
        // turns every slept gap into stillness nobody observed — on any handset without
        // TYPE_STEP_COUNTER, and on any handset where ACTIVITY_RECOGNITION was denied.
        if (!stepCountObserved) {
            return Verdict.Capped(
                capped,
                "Slept " + (gapMillis / 1000) + "s with no step counter reporting; " +
                    "capping inactivity at " + listenWindowSeconds + "s"
            )
        }

        // The counter was awake across the gap and saw nothing. That is evidence, so the long
        // reading stands.
        if (stepsDuringGap <= 0) return Verdict.Believed(rawSeconds)

        // Steps rose, so the senior moved somewhere inside the gap. Where exactly is unknowable,
        // so the reading is cut to what one sample can attest to.
        return Verdict.Capped(
            capped,
            "Slept " + (gapMillis / 1000) + "s with " + stepsDuringGap + " step(s); " +
                "capping inactivity at " + listenWindowSeconds + "s"
        )
    }
}
