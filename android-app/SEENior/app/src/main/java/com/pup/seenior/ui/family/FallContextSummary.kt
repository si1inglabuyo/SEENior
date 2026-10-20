package com.pup.seenior.ui.family

import com.pup.seenior.detection.FallContext
import com.pup.seenior.detection.FallTolerance

/**
 * The few facts about a potential fall that a family member can act on, with every raw figure
 * removed. Built from the alert's `fall_context` and `fall_tolerance` notes; the sentences live in
 * [FamilyStrings.Copy.fallContextLines].
 *
 * A hint for a human, not a finding: the detector never reads any of it, and the wording says
 * "may" and "could", never "is". The barangay dashboard has a JavaScript twin
 * (`barangay-dashboard/src/fallContext.js`); the two must be changed together.
 */
object FallContextSummary {

    enum class Motion { MOVING, AT_REST, UNKNOWN }

    data class Summary(
        val motion: Motion,
        /** The alert fell in the senior's own sleep block. */
        val sleeping: Boolean,
        /** The fall detector had been made less sensitive after repeated false alarms. */
        val lessSensitive: Boolean
    )

    /** Null when the alert carries no context (older rows, other triggers, an unreadable note). */
    fun from(contextNote: String?, toleranceNote: String?): Summary? {
        val context = FallContext.decode(contextNote) ?: return null
        val motion = when (context.active) {
            true -> Motion.MOVING
            false -> Motion.AT_REST
            null -> Motion.UNKNOWN
        }
        return Summary(
            motion = motion,
            sleeping = context.night,
            lessSensitive = FallTolerance.decodeNote(toleranceNote)?.raised == true
        )
    }
}
