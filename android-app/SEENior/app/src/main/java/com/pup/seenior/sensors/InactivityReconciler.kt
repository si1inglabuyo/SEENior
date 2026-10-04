package com.pup.seenior.sensors

/**
 * The decision half of [SensorCollectionService.reconcileInactivity], without logging or
 * Android types, so JUnit can drive it (like [com.pup.seenior.detection.FallDetector]). The
 * question has been got wrong twice on real data, so the three cases are worth testing.
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
     * @param gapMillis         time since the previous sample; null when there is none.
     * @param pollIntervalMs    the normal sampling period. A gap under twice this was not a sleep.
     * @param listenWindowMs    how long the service listens each poll, the most stillness one sample can attest to.
     * @param stepCountObserved whether the step counter has ever reported. False means no witness, not no steps.
     * @param stepsDuringGap    rise in the step counter across the gap. Zero or less means no steps or a reboot; both read as flat.
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

        // A gap this short is sampling jitter, not a suspend, so the reading means what it says.
        if (gapMillis <= pollIntervalMs * 2) return Verdict.Believed(rawSeconds)

        val listenWindowSeconds = listenWindowMs / 1000
        val capped = minOf(rawSeconds, listenWindowSeconds)

        // No witness: a flat step count would be silence, not testimony, and believing it would
        // turn every slept gap into unobserved stillness (no step counter, or permission denied).
        if (!stepCountObserved) {
            return Verdict.Capped(
                capped,
                "Slept " + (gapMillis / 1000) + "s with no step counter reporting; " +
                    "capping inactivity at " + listenWindowSeconds + "s"
            )
        }

        // The counter was awake and saw nothing, which is evidence, so the long reading stands.
        if (stepsDuringGap <= 0) return Verdict.Believed(rawSeconds)

        // Steps rose, so the senior moved somewhere in the gap. Cut to what one sample can attest to.
        return Verdict.Capped(
            capped,
            "Slept " + (gapMillis / 1000) + "s with " + stepsDuringGap + " step(s); " +
                "capping inactivity at " + listenWindowSeconds + "s"
        )
    }
}
