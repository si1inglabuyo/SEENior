package com.pup.seenior.ui.alerts

import com.pup.seenior.detection.IsolationForestDetector
import com.pup.seenior.ui.wellness.WellnessMessages
import java.util.Locale
import kotlin.math.abs

/**
 * The senior-facing explanation of a single alert on the Alerts tab: why it triggered, plus a
 * short note on what the detection math actually found, both in plain language.
 *
 * The "why" half reuses [WellnessMessages.forAlert]'s `reason` field — the same sentence the
 * wellness prompt itself showed at the time (CLAUDE.md §7) — so a senior reading their history
 * later sees a consistent story, not a second, differently-worded explanation. Name is passed as
 * empty because `reason` never interpolates it (only `question`/`sosQuestion` do).
 *
 * The "finding" half is new here: a one-clause translation of [Alert.deviationScore] (CLAUDE.md
 * §5's Median-MAD z-score) into "noticeably" vs. "far" outside normal, using the same 2.5/3.5
 * moderate/extreme boundary the detector itself uses — never the raw number, which means nothing
 * to a non-technical reader. `ml_flag` and `fall_pattern` carry no z-score (Isolation Forest's
 * score is path-length based, not z-score based — CLAUDE.md §14 forbids collapsing the two into
 * one number), so they get their own fixed clause instead.
 */
object AlertDescriptions {

    fun describe(
        language: String,
        triggerType: String,
        timeBlock: String,
        deviationScore: Double?
    ): String {
        val reason = WellnessMessages.forAlert(language, "", triggerType, timeBlock).reason
        val finding = finding(language, triggerType, deviationScore)
        return if (finding != null) "$reason $finding" else reason
    }

    private fun finding(language: String, triggerType: String, deviationScore: Double?): String? {
        val fil = language == WellnessMessages.FILIPINO
        return when (triggerType) {
            "fall_pattern" -> if (fil)
                "Kahawig ito ng biglaang pagkahulog — bigla kang bumagsak, tapos hindi na gumalaw."
            else
                "This matched the pattern of a sudden fall — a drop, then no movement afterward."
            "ml_flag" -> if (fil)
                "Walang iisang bagay na kakaiba, pero magkasama, iba ang buong araw mo kumpara sa dati."
            else
                "No single reading stood out on its own, but together, your whole day looked different from usual."
            "sos" -> null
            else -> deviationScore?.let { z ->
                val extreme = abs(z) >= EXTREME_THRESHOLD
                if (fil) {
                    if (extreme) "Malayong-malayo ito sa karaniwan mong ginagawa sa oras na ito."
                    else "Kapansin-pansin itong iba sa karaniwan mong ginagawa sa oras na ito."
                } else {
                    if (extreme) "This was far outside what's normal for you at this time of day."
                    else "This was noticeably outside what's normal for you at this time of day."
                }
            }
        }
    }

    /** Matches the moderate/extreme boundary CLAUDE.md §5 documents for Layer 1. */
    private const val EXTREME_THRESHOLD = 3.5

    /**
     * A one-line "show your work" for the two layers that actually compute a score — the
     * Median-MAD anomaly detector (Layer 1) and Isolation Forest (Layer 2).
     *
     * Deliberately absent for `fall_pattern` (a signature match, not a scored anomaly) and `sos`
     * (the senior's own action) — there is nothing to compute for either, and inventing a number
     * would misrepresent how they work. CLAUDE.md §14 forbids collapsing the z-score and the
     * path-length score into one figure, so this keeps them on two separate lines with two
     * separate labels rather than a single generic "confidence" reading.
     *
     * @param mlFlagScore Isolation Forest's score for the block this `ml_flag` alert was raised
     *   from, looked up from `Daily_Aggregates` by the caller ([AlertsViewModel.mlFlagScores]) —
     *   null if it hasn't loaded yet, in which case no line is shown rather than a wrong one.
     */
    fun computationLine(
        language: String,
        triggerType: String,
        deviationScore: Double?,
        mlFlagScore: Double?
    ): String? {
        val fil = language == WellnessMessages.FILIPINO
        return when (triggerType) {
            "ml_flag" -> mlFlagScore?.let { score ->
                val threshold = fmt(IsolationForestDetector.THRESHOLD)
                if (fil)
                    "Isolation Forest: anomaly score = ${fmt(score)} (hangganan ng pag-flag: $threshold)"
                else
                    "Isolation Forest: anomaly score = ${fmt(score)} (flag threshold: $threshold)"
            }
            "inactivity", "movement", "screen_idle" -> deviationScore?.let { z ->
                if (fil)
                    "Anomaly Detector: |binasa ngayon − karaniwan mong binasa| ÷ karaniwan mong pagbabago = ${fmt(z)}"
                else
                    "Anomaly Detector: |your reading − your usual| ÷ your usual variation = ${fmt(z)}"
            }
            else -> null
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
}
