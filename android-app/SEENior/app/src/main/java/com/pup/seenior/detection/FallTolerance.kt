package com.pup.seenior.detection

import java.util.Locale

/**
 * How far a senior's confirmed false fall alarms may make Layer 0 harder to trigger.
 *
 * A senior with a failing grip drops the phone often, and a dropped phone looks like a fall: it
 * falls freely, hits, and lies still. The wellness prompt already lets them cancel each one, but
 * a fall alarm every few days is its own harm. Here the cancellations teach the detector.
 *
 * Only two gates move: the impact threshold and the stillness time. Free fall and rotation are
 * left alone, so a dropped phone is filtered by how hard and how long, not by redefining what a
 * fall is.
 *
 * Same shape as [FalseAlarmTolerance], and the same guards:
 * - [MIN_EVIDENCE]: one cancelled fall proves nothing; two in the window is a pattern.
 * - [MAX_RAISE]: the raise is capped, so the detector can never be argued out of detecting a fall.
 * - [WINDOW_DAYS]: the baseline window. The raise is computed from `Alerts` on every call and
 *   stored nowhere, so it lapses by itself.
 * - Real falls take the slack back: a fall that went past the senior and was acted on (family or
 *   barangay) cancels its share. With F false alarms and R real ones the raise is multiplied by
 *   `(F - R) / F`, and is gone once R >= F.
 * - SOS never passes through this: it is the senior's own action, not a detection.
 *
 * No Android imports, so JUnit can drive it.
 */
object FallTolerance {

    /** Confirmed false fall alarms in the window before anything changes. */
    const val MIN_EVIDENCE = 2

    /** How far back false alarms count. Same 14 days as the baseline and the Layer 1 tolerance. */
    const val WINDOW_DAYS = FalseAlarmTolerance.WINDOW_DAYS

    /** Share added to each gate per false alarm: 5%, so two false alarms raise impact 25.0 to 27.5. */
    const val STEP_PER_FALSE_ALARM = 0.05

    /** The most any gate can be raised: 30%, so impact never passes 32.5 m/s2 and stillness 10.4 s. */
    const val MAX_RAISE = 0.30

    /** The senior's answer window for a normal fall alert. */
    const val BASE_WINDOW_SECONDS = 60

    /**
     * The answer window once tolerance is active. A senior who has cancelled several falls is
     * probably across the room from a dropped phone, so they get longer to reach it. The alert
     * stays High with the full alarm and still escalates when the time runs out.
     */
    const val EXTENDED_WINDOW_SECONDS = 120

    /**
     * How long the senior has to answer a fall alert whose stored [note] is [Result.encode]'s
     * output. Read off the alert itself, so the countdown, the watchdog and the server all get
     * the same answer with nothing extra stored. Tolerance that real falls have cancelled out
     * (not [Result.raised]) gives the base window.
     */
    fun windowSeconds(note: String?): Int =
        if (decodeNote(note)?.raised == true) EXTENDED_WINDOW_SECONDS else BASE_WINDOW_SECONDS

    /** Earliest `triggered_at` that still counts as evidence at [now]. */
    fun windowStart(now: Long): Long = FalseAlarmTolerance.windowStart(now)

    /**
     * Everything the detector was set to and why, in one place so the Alerts tab, the demo and
     * the database all show the same working.
     */
    data class Result(
        val config: FallDetector.Config,
        val baseConfig: FallDetector.Config,
        val falseAlarms: Int,
        val realAlerts: Int,
        /** Share of the base added to impact and stillness, 0.0 to [MAX_RAISE]. */
        val raise: Double
    ) {
        val raised: Boolean get() = raise > 0.0

        /**
         * `impact_min=27.5;base_impact=25.0;still_ms=8800;base_still=8000;false=2;real=0`.
         * Metadata only: the thresholds the detector was set to and how many alerts earned them,
         * never what a sensor measured. This string is what syncs to the cloud.
         */
        fun encode(): String = buildString {
            append("impact_min=").append(num(config.impactMin))
            append(";base_impact=").append(num(baseConfig.impactMin))
            append(";still_ms=").append(config.stillnessMs)
            append(";base_still=").append(baseConfig.stillnessMs)
            append(";false=").append(falseAlarms)
            append(";real=").append(realAlerts)
        }

        /** One line saying what moved and why, or that nothing did. */
        fun line(filipino: Boolean = false): String = if (!raised) {
            if (filipino) "Walang pagbabago sa sensitibidad ($falseAlarms maling alarma sa nakaraang $WINDOW_DAYS araw)."
            else "Fall sensitivity unchanged ($falseAlarms false alarm(s) in the last $WINDOW_DAYS days)."
        } else if (filipino) {
            "Binawasan ang sensitibidad: tama sa lupa ≥ ${num(config.impactMin)} (dati ${num(baseConfig.impactMin)}), " +
                "hindi gumalaw ≥ ${config.stillnessMs} ms (dati ${baseConfig.stillnessMs}) " +
                "dahil sa $falseAlarms maling alarma at $realAlerts tunay."
        } else {
            "Fall sensitivity lowered: impact ≥ ${num(config.impactMin)} (was ${num(baseConfig.impactMin)}), " +
                "stillness ≥ ${config.stillnessMs} ms (was ${baseConfig.stillnessMs}) " +
                "after $falseAlarms false alarm(s) and $realAlerts real."
        }
    }

    /**
     * The detector settings for a senior with [falseAlarms] cancelled falls and [realAlerts]
     * acted-on falls in the window. One computation, used by the detector, the demo and the tests.
     */
    fun resolve(
        base: FallDetector.Config,
        falseAlarms: Int,
        realAlerts: Int = 0
    ): Result {
        if (falseAlarms < MIN_EVIDENCE) return Result(base, base, falseAlarms, realAlerts, 0.0)
        val earned = (falseAlarms * STEP_PER_FALSE_ALARM).coerceAtMost(MAX_RAISE)
        val kept = ((falseAlarms - realAlerts).toDouble() / falseAlarms).coerceIn(0.0, 1.0)
        val raise = earned * kept
        // Rounded to one decimal / whole millisecond so the stored note reads cleanly.
        val config = base.copy(
            impactMin = round1((base.impactMin * (1 + raise)).toFloat()),
            stillnessMs = Math.round(base.stillnessMs * (1 + raise))
        )
        return Result(config, base, falseAlarms, realAlerts, raise)
    }

    /**
     * Reads the `impact_min` / `still_ms` back out of a stored note, so the Alerts tab can show
     * the thresholds a fall was actually judged against. Null for anything unreadable.
     */
    fun decode(raw: String?, base: FallDetector.Config = FallDetector.Config()): FallDetector.Config? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        return runCatching {
            base.copy(
                impactMin = parts.getValue("impact_min").toFloat(),
                stillnessMs = parts.getValue("still_ms").toLong()
            )
        }.getOrNull()
    }

    /**
     * The whole note read back as a [Result], so the Alerts tab can say what moved and why.
     * Null for anything unreadable, including falls recorded before this existed.
     */
    fun decodeNote(raw: String?, defaults: FallDetector.Config = FallDetector.Config()): Result? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        return runCatching {
            val base = defaults.copy(
                impactMin = parts.getValue("base_impact").toFloat(),
                stillnessMs = parts.getValue("base_still").toLong()
            )
            val config = base.copy(
                impactMin = parts.getValue("impact_min").toFloat(),
                stillnessMs = parts.getValue("still_ms").toLong()
            )
            Result(
                config = config,
                baseConfig = base,
                falseAlarms = parts.getValue("false").toInt(),
                realAlerts = parts.getValue("real").toInt(),
                raise = (config.impactMin / base.impactMin - 1.0).coerceAtLeast(0.0)
            )
        }.getOrNull()
    }

    private fun round1(v: Float): Float = Math.round(v * 10f) / 10f

    private fun num(v: Float): String = String.format(Locale.US, "%.1f", v)
}
