package com.pup.seenior.ui.alerts

import com.pup.seenior.detection.IsolationForestDetector
import com.pup.seenior.ui.wellness.WellnessMessages
import java.util.Locale
import kotlin.math.abs

/**
 * The senior-facing explanation of one alert on the Alerts tab: why it triggered, and a short
 * plain-language note on what the detection found.
 *
 * The "why" reuses [WellnessMessages.forAlert]'s `reason`, the same sentence the prompt showed
 * at the time. The name is passed empty since `reason` never uses it.
 *
 * The "finding" turns [Alert.deviationScore] into "noticeably" vs "far" outside normal, using
 * the detector's 2.5/3.5 boundary, never the raw number. `ml_flag` and `fall_pattern` have no
 * z-score (Isolation Forest's score is path-length based and must stay separate), so they
 * get a fixed clause.
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

    /** Matches the moderate/extreme boundary the spec §5 documents for Layer 1. */
    private const val EXTREME_THRESHOLD = 3.5

    /**
     * A one-line "show your work" for the two layers that compute a score: Median-MAD
     * (Layer 1) and Isolation Forest (Layer 2). Absent for `fall_pattern` (a signature match)
     * and `sos` (the senior's own action). The two scores stay on separate lines with separate labels.
     *
     * @param mlFlagScore Isolation Forest's score for the block, looked up by the caller
     *   ([AlertsViewModel.mlFlagScores]); null if not loaded yet, in which case no line is shown.
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
                    "Gawi Ngayon: Kakaiba ang buong araw mo ngayon. (Iskor: ${fmt(score)} / Normal ay mababa sa $threshold)"
                else
                    "Daily Routine: Your whole day looks unusual today. (Score: ${fmt(score)} / Normal is under $threshold)"
            }
            "inactivity", "movement", "screen_idle" -> deviationScore?.let { z ->
                if (fil)
                    "Galaw Ngayon: Iba ang galaw mo ngayon kumpara sa dati. (Iskor: ${fmt(z)} / Normal ay mababa sa 2.5)"
                else
                    "Activity Check: Your movement today is very unusual. (Score: ${fmt(z)} / Normal is under 2.5)"
            }
            else -> null
        }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
}
