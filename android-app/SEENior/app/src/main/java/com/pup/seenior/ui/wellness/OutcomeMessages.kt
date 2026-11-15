package com.pup.seenior.ui.wellness

import com.pup.seenior.alerts.OutcomeReasons

/**
 * The words for the optional "what happened" question shown after "I'm safe". Kept beside
 * [WellnessMessages] and out of `strings.xml`, for the same reason: the language is a per-senior
 * setting, not the device locale. Formal register ("po"), like the rest of the prompt.
 *
 * The codes come from [OutcomeReasons]; only the labels are translated, so both languages store
 * the same value.
 */
object OutcomeMessages {

    data class Option(val code: String, val label: String)

    data class Copy(
        val question: String,
        val options: List<Option>
    )

    fun forAlert(language: String, triggerType: String): Copy? {
        val codes = OutcomeReasons.codesFor(triggerType)
        if (codes.isEmpty()) return null
        val fall = triggerType == "fall_pattern"
        val filipino = language == WellnessMessages.FILIPINO
        return Copy(
            question = when {
                fall && filipino -> "Ano po ang nangyari?"
                fall -> "What happened?"
                filipino -> "Bakit po kaya?"
                else -> "Why was that?"
            },
            options = codes.map { Option(it, label(it, filipino)) }
        )
    }

    private fun label(code: String, filipino: Boolean): String = when (code) {
        OutcomeReasons.PHONE_DROPPED -> if (filipino) "Nalaglag po ang phone" else "Phone dropped"
        OutcomeReasons.FELL_FINE -> if (filipino) "Nahulog po ako, ayos lang" else "I fell, I'm fine"
        OutcomeReasons.UNUSED_FINE ->
            if (filipino) "Matagal po di nagamit, ayos lang" else "Unused for long, I'm fine"
        OutcomeReasons.LEFT_BEHIND_FINE ->
            if (filipino) "Naiwan po ang phone, ayos lang" else "Left the phone, I'm fine"
        else -> if (filipino) "Iba pa" else "Others"
    }
}
