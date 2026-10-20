package com.pup.seenior.detection

import java.util.Locale

/**
 * What the senior was doing when a fall was judged, stored on the alert beside [FallEvidence] and
 * the tolerance note so the database shows the situation as well as the thresholds.
 *
 * Two figures and a flag, never the sample stream, so the privacy boundary holds:
 * - `pre_dev`: mean distance of the phone's acceleration from 1 g over the two seconds before the
 *   free fall began (m/s2). Near zero for a phone lying on a bed or held in a resting hand; a few
 *   m/s2 for someone walking.
 * - `active`: 1 when `pre_dev` is at least [ACTIVE_MIN], else 0. Absent when the detector had too
 *   few samples before the drop to say.
 * - `night`: 1 when the alert fell in the senior's night block (their own sleep time to wake time).
 *
 * This is a record, not a rule. The detector's decision and [FallTolerance] do not read it. It
 * exists so a dropped phone at rest ("pre_dev=0.1;active=0;night=1") can be told apart in the
 * database from a fall while moving ("pre_dev=2.8;active=1;night=0"). [ACTIVE_MIN] is an
 * engineering starting point, not a published value.
 *
 * No Android imports, so JUnit can drive it.
 */
object FallContext {

    /** Mean deviation from 1 g (m/s2) at or above which the phone counts as having been moving. */
    const val ACTIVE_MIN = 1.5f

    /** `pre_dev=2.8;active=1;night=0`, with `pre_dev` and `active` omitted when motion is unknown. */
    fun encode(preFallMotion: Float?, timeBlock: String): String = buildString {
        if (preFallMotion != null) {
            append("pre_dev=").append(String.format(Locale.US, "%.1f", preFallMotion))
            append(";active=").append(if (preFallMotion >= ACTIVE_MIN) 1 else 0)
            append(';')
        }
        append("night=").append(if (timeBlock == "night") 1 else 0)
    }

    data class Decoded(val preFallMotion: Float?, val active: Boolean?, val night: Boolean)

    /** Null for anything unreadable, including falls recorded before this existed. */
    fun decode(raw: String?): Decoded? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        return runCatching {
            Decoded(
                preFallMotion = parts["pre_dev"]?.toFloat(),
                active = parts["active"]?.let { it == "1" },
                night = parts.getValue("night") == "1"
            )
        }.getOrNull()
    }
}
