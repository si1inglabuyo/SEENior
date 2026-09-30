package com.pup.seenior.detection

/**
 * How far a senior's confirmed false alarms may loosen Layer 1's trigger for one signal in one
 * time block.
 *
 * Until this existed a false alarm was only a status label: nothing read it back, so a senior who
 * is harmlessly still at the same hour every week was asked "Are you safe and well?" at the same
 * hour every week, forever. This closes that loop without touching the baseline. The Median/MAD
 * fingerprint stays exactly what the senior's own days produced (CLAUDE.md §14); only the *gate*
 * a reading must clear before Layer 3 is consulted moves, and only for the block that keeps
 * crying wolf.
 *
 * **What counts as evidence** is a z-score from an alert that was later closed as a false alarm --
 * the senior answered "Ligtas po ako", or a family contact or the barangay marked it a false
 * alarm. The caller decides which alerts qualify; this object only turns their scores into a
 * threshold, and has no Android imports so JUnit can drive it directly (CLAUDE.md §10).
 *
 * **Every number below is a guard, and the cap is the one that matters:**
 *
 * - [MIN_EVIDENCE] -- one false alarm proves nothing. Two in the same block within the window is
 *   a pattern. Below that, behaviour is byte-for-byte what it was before this existed.
 * - [CAP] -- 3.5 is the "extreme" line (CLAUDE.md §5). A reading at or past it always alerts, no
 *   matter how many false alarms came before, so a real emergency deep into the block cannot be
 *   argued away. Only the *moderate* band, 2.5 to 3.5, can ever be loosened.
 * - Evidence at or past [CAP] is discarded rather than clamped. Otherwise a senior cancelling two
 *   genuinely extreme alerts (or the demo's injected z = 4.0 reading, see [AnomalySimulator]) would
 *   push the threshold straight to the cap, teaching the system that extremes are ordinary.
 * - [WINDOW_DAYS] -- the same 14 days as the baseline. Nothing is stored, so the loosening lapses
 *   by itself; if false alarms recur afterwards, two more are needed. That bounds the annoyance
 *   at two per block per window instead of leaving a permanent blind spot.
 * - The median, not the mean, of the evidence -- one odd score cannot drag the threshold.
 *
 * Known limits, stated rather than hidden: the stored score is the z at the moment the alert was
 * *raised*, not at the moment it was dismissed, so it is usually just over 2.5 and the resulting
 * raise is small on purpose; and a z computed against an older baseline is compared with a
 * newer one, which the shared 14-day window keeps to roughly one baseline refresh.
 */
object FalseAlarmTolerance {

    /** The moderate threshold of CLAUDE.md §5 -- what every block starts at, and returns to. */
    const val BASE_THRESHOLD = 2.5

    /** The "extreme" line. The threshold can approach it but never exceed it. */
    const val CAP = 3.5

    /** Headroom added above the median tolerated z, so a reading exactly as unusual as the
     *  false alarms does not sit on the line and flip on rounding. */
    const val MARGIN = 0.25

    /** Confirmed false alarms in the same (signal, block) before anything changes. */
    const val MIN_EVIDENCE = 2

    /** How far back false alarms count. Matches the 14-day baseline window. */
    const val WINDOW_DAYS = 14

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** Earliest `triggered_at` that still counts as evidence at [now]. */
    fun windowStart(now: Long): Long = now - WINDOW_DAYS * DAY_MILLIS

    /**
     * The z-score a reading must reach before this block raises an alert.
     *
     * @param falseAlarmScores the `deviation_score` of each recent alert in this block that was
     *   closed as a false alarm. Unfiltered: scores outside `[BASE_THRESHOLD, CAP)` are dropped
     *   here, so no caller can forget to.
     */
    fun thresholdFor(falseAlarmScores: List<Double>): Double {
        val evidence = falseAlarmScores.filter { it >= BASE_THRESHOLD && it < CAP }
        if (evidence.size < MIN_EVIDENCE) return BASE_THRESHOLD
        return (MedianMad.median(evidence) + MARGIN).coerceIn(BASE_THRESHOLD, CAP)
    }
}
