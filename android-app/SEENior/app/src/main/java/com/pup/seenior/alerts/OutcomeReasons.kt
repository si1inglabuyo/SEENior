package com.pup.seenior.alerts

/**
 * The one optional question the senior is asked after answering "I'm safe": what happened.
 *
 * Fixed codes only, never free text, so nothing personal can ride along and the answers can be
 * counted. It exists to tell a dropped phone from a real fall that turned out fine, and an
 * explained quiet spell from a missed one; the alert's status alone ("self_cancelled") cannot.
 *
 * A record, not a rule: detection, escalation and the false-alarm tolerance never read it.
 * SOS is never asked, since it is the senior calling for help.
 */
object OutcomeReasons {

    const val PHONE_DROPPED = "phone_dropped"
    const val FELL_FINE = "fell_fine"
    const val UNUSED_FINE = "unused_fine"
    const val LEFT_BEHIND_FINE = "left_behind_fine"
    const val OTHER = "other"

    private val FALL_CODES = listOf(PHONE_DROPPED, FELL_FINE, OTHER)
    private val ANOMALY_CODES = listOf(UNUSED_FINE, LEFT_BEHIND_FINE, OTHER)

    private val ANOMALY_TRIGGERS = setOf("inactivity", "movement", "screen_idle", "charging", "ml_flag")

    /** The codes to offer for this trigger, in the order shown. Empty when the senior is not asked. */
    fun codesFor(triggerType: String): List<String> = when (triggerType) {
        "fall_pattern" -> FALL_CODES
        in ANOMALY_TRIGGERS -> ANOMALY_CODES
        else -> emptyList()
    }

    /** Whether the senior is asked about this trigger at all. */
    fun isAsked(triggerType: String): Boolean = codesFor(triggerType).isNotEmpty()

    /** Whether [code] is one this trigger offers; a stray value is never stored or sent. */
    fun isValid(triggerType: String, code: String): Boolean = code in codesFor(triggerType)
}
