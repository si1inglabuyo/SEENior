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
 * - Real alerts take the slack back. An alert in the same (signal, block) that went past the
 *   senior and was not a false alarm (family or the barangay acted on it) means this hour
 *   does sometimes matter, so each one cancels its share of the raise: with F false alarms
 *   and R real ones, the raise is multiplied by `(F - R) / F`, and is gone once R >= F.
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
    fun thresholdFor(falseAlarmScores: List<Double>, realAlertCount: Int = 0): Double =
        explain(falseAlarmScores, realAlertCount).gate

    /**
     * Every step of [thresholdFor], kept so the demo can show the working and the detector
     * can't drift from it: there is one computation, and this is it.
     */
    data class Breakdown(
        /** The false-alarm scores that count as evidence (those in `[BASE_THRESHOLD, CAP)`). */
        val evidence: List<Double>,
        val realAlertCount: Int,
        /** Median of [evidence]; null until there are [MIN_EVIDENCE] of them. */
        val medianZ: Double?,
        /** `median + MARGIN` held to `[BASE_THRESHOLD, CAP]`, before real alerts take any back. */
        val gateBeforeRealAlerts: Double,
        /** The part of the raise that survives the real alerts, 0.0 to 1.0. */
        val keptShare: Double,
        val gate: Double
    ) {
        /** How far the gate sits above the base, in z-scores. */
        val raise: Double get() = gate - BASE_THRESHOLD

        /** The same raise as extra tolerated stillness, given this block's effective MAD in seconds. */
        fun extraMinutes(effectiveMadSeconds: Double): Double = raise * effectiveMadSeconds / 60.0
    }

    fun explain(falseAlarmScores: List<Double>, realAlertCount: Int = 0): Breakdown {
        val evidence = falseAlarmScores.filter { it >= BASE_THRESHOLD && it < CAP }
        if (evidence.size < MIN_EVIDENCE) {
            return Breakdown(evidence, realAlertCount, null, BASE_THRESHOLD, 1.0, BASE_THRESHOLD)
        }
        val median = MedianMad.median(evidence)
        val before = (median + MARGIN).coerceIn(BASE_THRESHOLD, CAP)
        val kept = ((evidence.size - realAlertCount).toDouble() / evidence.size).coerceIn(0.0, 1.0)
        return Breakdown(
            evidence, realAlertCount, median, before, kept,
            gate = BASE_THRESHOLD + (before - BASE_THRESHOLD) * kept
        )
    }
}
