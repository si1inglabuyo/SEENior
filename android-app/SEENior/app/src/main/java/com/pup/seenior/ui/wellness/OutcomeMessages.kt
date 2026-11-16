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
        val options: List<Option>,
        // The follow-up shown after "Others": what to type, and the buttons.
        val detailPrompt: String,
        val detailHint: String,
        val detailWarning: String,
        val detailSend: String
    )

    fun forAlert(language: String, triggerType: String): Copy? {
        val codes = OutcomeReasons.codesFor(triggerType)
        if (codes.isEmpty()) return null
        val filipino = language == WellnessMessages.FILIPINO
        return Copy(
            question = if (filipino) "Anong nangyari?" else "What happened?",
            options = codes.map { Option(it, label(it, filipino)) },
            detailPrompt = if (filipino) "Anong nangyari?" else "Please tell us what happened.",
            detailHint = if (filipino) "Isulat po dito (hindi kailangan)" else "Type here (optional)",
            detailWarning = if (filipino) "Huwag pong isama ang pangalan o numero ng telepono."
            else "Please do not include names or phone numbers.",
            detailSend = if (filipino) "Ipadala po" else "Send"
        )
    }

    private fun label(code: String, filipino: Boolean): String = when (code) {
        OutcomeReasons.PHONE_DROPPED -> if (filipino) "Nalaglag ang phone" else "Dropped my phone"
        OutcomeReasons.FELL_FINE ->
            if (filipino) "May totoong pagkahulog kanina" else "There was an actual fall"
        OutcomeReasons.UNUSED_FINE ->
            if (filipino) "Matagal hindi nagamit ang phone" else "Phone not used for long time"
        OutcomeReasons.LEFT_BEHIND_FINE ->
            if (filipino) "Naiwanan ang phone" else "Left the phone unattended"
        else -> if (filipino) "Iba pa" else "Others"
    }
}
