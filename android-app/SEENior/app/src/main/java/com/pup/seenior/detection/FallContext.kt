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
 * - `active`: 1 when `pre_dev` is at least [ACTIVE_MIN], else 0. The still / gentle / moving band
 *   ([levelOf]) is derived from `pre_dev` on the phone and is not written into this string: the
 *   cloud accepts only `key=number` pairs, and the family app and dashboard do not show it yet. Absent when the detector had too
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

    /**
     * Three bands for the 2 s value (mean deviation from 1 g, m/s2), from the 8-person walk test
     * (docs/evidence): resting phones read about 0.05 to 0.2, and walking spreads from about 0.4 to
     * 2 and beyond depending on the person and where the phone is carried. One cutoff forced a
     * yes/no on that middle, so the middle is its own band. Both edges are starting points to be
     * revisited with recordings from seniors; they are not validated values.
     */
    enum class Level { STILL, GENTLE, MOVING }

    /** Below this the phone is hardly moving (resting, asleep, on a table). */
    const val STILL_MAX = 0.5f

    /** At or above this the phone counts as having been moving. Was 1.5 until 2026-11-12. */
    const val ACTIVE_MIN = 1.0f

    /** Which band a 2 s value falls in. Display only; nothing in the detector reads it. */
    fun levelOf(preDev: Float): Level = when {
        preDev >= ACTIVE_MIN -> Level.MOVING
        preDev >= STILL_MAX -> Level.GENTLE
        else -> Level.STILL
    }

    /**
     * `pre_dev=2.8;n=100;sum=280.00;win_ms=2000;thr=1.5;active=1;night=0`, with the motion keys
     * omitted when motion is unknown. The working is stored so a reader can redo it:
     * `pre_dev` = `sum` / `n`, where `sum` adds up |acceleration - 1 g| (m/s2) over the `n`
     * samples in the `win_ms` before the free fall, and `active` is `pre_dev` >= `thr`.
     * `sum` and `n` are two totals, not the sample stream.
     */
    fun encode(
        preFallMotion: Float?,
        timeBlock: String,
        samples: Int? = null,
        steps: Int? = null
    ): String = buildString {
        if (preFallMotion != null) {
            append("pre_dev=").append(String.format(Locale.US, "%.1f", preFallMotion))
            if (samples != null) {
                append(";n=").append(samples)
                append(";sum=").append(String.format(Locale.US, "%.2f", preFallMotion * samples))
                append(";win_ms=").append(FallDetector.PRE_FALL_WINDOW_MS)
                append(";thr=").append(String.format(Locale.US, "%.1f", ACTIVE_MIN))
            }
            // Steps the step detector heard in the same window; left out when the phone has none.
            if (steps != null) append(";steps=").append(steps)
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
