package com.pup.seenior.detection

/**
 * How far a senior's confirmed false alarms may loosen Layer 1's trigger for one signal in
 * one time block.
 *
 * Without this, a senior who is harmlessly still at the same hour every week would be asked
 * "Are you safe and well?" every week. The baseline is untouched; only the gate a reading
 * must clear before Layer 3 is consulted moves, and only for the block that keeps alerting.
 *
 * Evidence is the z-score of an alert later closed as a false alarm (the senior answered
 * "Ligtas po ako", or family or the barangay marked it). The caller picks the alerts; this
 * object turns the scores into a threshold, with no Android imports so JUnit can drive it.
 *
 * Guards:
 * - [MIN_EVIDENCE]: one false alarm proves nothing; two in the same block is a pattern.
 * - [CAP]: 3.5 is the "extreme" line. A reading at or past it always alerts, so only the
 *   moderate band (2.5 to 3.5) can be loosened.
 * - Evidence at or past [CAP] is discarded, not clamped, so cancelling extreme alerts (or the
 *   demo's injected z = 4.0, see [AnomalySimulator]) can't push the threshold to the cap.
 * - [WINDOW_DAYS]: the same 14 days as the baseline. Nothing is stored, so it lapses by itself.
 * - The median of the evidence, so one odd score can't drag the threshold.
 *
 * Known limits: the stored score is the z when the alert was raised, so usually just over
 * 2.5 and the raise is small; and a z from an older baseline is compared with a newer one,
 * which the shared window keeps to about one refresh.
 */
object FalseAlarmTolerance {

    /** The moderate threshold of the spec §5 -- what every block starts at, and returns to. */
    const val BASE_THRESHOLD = 2.5

    /** The "extreme" line. The threshold can approach it but never exceed it. */
    const val CAP = 3.5

    /** Headroom above the median tolerated z, so a reading exactly as unusual as the false alarms doesn't flip on rounding. */
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
     *   closed as a false alarm. Scores outside `[BASE_THRESHOLD, CAP)` are dropped here.
     */
    fun thresholdFor(falseAlarmScores: List<Double>): Double {
        val evidence = falseAlarmScores.filter { it >= BASE_THRESHOLD && it < CAP }
        if (evidence.size < MIN_EVIDENCE) return BASE_THRESHOLD
        return (MedianMad.median(evidence) + MARGIN).coerceIn(BASE_THRESHOLD, CAP)
    }
}
