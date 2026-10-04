package com.pup.seenior.detection

import com.pup.seenior.baseline.SeedBaselineGenerator
import java.util.Calendar
import kotlin.math.min

/**
 * Layer 3: turns an anomaly signal into a Low / Medium / High risk level.
 *
 * Uses Mamdani fuzzy inference (min for AND, max to aggregate, centroid defuzzification),
 * so the same z-score can mean different things at 8 am and at 3 am, and readings near a
 * boundary get a graduated answer. Its output stays separate from the z-score and from
 * Isolation Forest's score.
 *
 * No Android imports, so JUnit can drive it directly.
 *
 * Isolation Forest's score is a third input (an `ml_flag` membership), giving 27 rules. A
 * Layer 1 alert has no ml_flag score ([Inputs.mlFlagScore] is 0.0), so the original nine
 * rules fire unchanged. A Layer 2 finding alone is capped at Medium, because it is a
 * retrospective judgement; it reaches High only by corroborating a serious Layer 1 deviation.
 *
 * There is deliberately no baseline-confidence input. The seed baseline already uses a wide
 * MAD (see [com.pup.seenior.baseline.SeedBaselineGenerator]), so damping risk again would
 * double-count the caution.
 */
object FuzzyRiskClassifier {

    /** The three levels of the spec §5. [stored] is the `Alerts.risk_level` value. */
    enum class Risk(val stored: String) {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high")
    }

    /**
     * @param deviationScore the Layer 1 Modified Z-Score (always at or above the moderate threshold).
     * @param restExpectation how much stillness is normal now, 0.0 (awake) to 1.0 (asleep). See [restExpectation].
     */
    data class Inputs(
        val deviationScore: Double,
        val restExpectation: Double,
        /**
         * Isolation Forest's score for this block (0.0-1.0), or 0.0 if there isn't one. It is a
         * separate input from [deviationScore] and never blended into it.
         */
        val mlFlagScore: Double = 0.0
    )

    private data class Rule(
        val deviation: Deviation,
        val rest: Rest,
        val mlFlag: MlFlag,
        val risk: Risk
    )

    /**
     * The rule base, read as `deviation x rest x ml_flag -> risk`.
     *
     * The same deviation is High while awake and Medium while expected to be asleep, and a
     * mild deviation at rest is Low. Two invariants hold across all 27 rows:
     * 1. Nothing at full rest reaches High (a real emergency keeps growing and escalates
     *    once the waking hours begin).
     * 2. ml_flag alone never reaches High (it arrives with deviation 0, so its group tops out
     *    at Medium). It raises High only by agreeing with a Layer 1 deviation.
     *
     * The nine [MlFlag.NONE] rows are the original table and must not change.
     */
    private val RULES: List<Rule> = listOf(
        // --- no Layer 2 score: the original nine, untouched ---
        Rule(Deviation.MILD, Rest.ACTIVE, MlFlag.NONE, Risk.MEDIUM),
        Rule(Deviation.MILD, Rest.TRANSITIONAL, MlFlag.NONE, Risk.LOW),
        Rule(Deviation.MILD, Rest.RESTING, MlFlag.NONE, Risk.LOW),
        Rule(Deviation.MODERATE, Rest.ACTIVE, MlFlag.NONE, Risk.HIGH),
        Rule(Deviation.MODERATE, Rest.TRANSITIONAL, MlFlag.NONE, Risk.MEDIUM),
        Rule(Deviation.MODERATE, Rest.RESTING, MlFlag.NONE, Risk.LOW),
        Rule(Deviation.EXTREME, Rest.ACTIVE, MlFlag.NONE, Risk.HIGH),
        Rule(Deviation.EXTREME, Rest.TRANSITIONAL, MlFlag.NONE, Risk.HIGH),
        Rule(Deviation.EXTREME, Rest.RESTING, MlFlag.NONE, Risk.MEDIUM),

        // --- Layer 2 flagged the block ---
        // The MILD group is the pure Layer 2 alert: capped at Medium, and Low at rest so the
        // nightly job can't wake a sleeping senior.
        Rule(Deviation.MILD, Rest.ACTIVE, MlFlag.PRESENT, Risk.MEDIUM),
        Rule(Deviation.MILD, Rest.TRANSITIONAL, MlFlag.PRESENT, Risk.LOW),
        Rule(Deviation.MILD, Rest.RESTING, MlFlag.PRESENT, Risk.LOW),
        Rule(Deviation.MODERATE, Rest.ACTIVE, MlFlag.PRESENT, Risk.HIGH),
        // Layer 1 said moderate and Layer 2 agrees the block was off: worth more than either alone.
        Rule(Deviation.MODERATE, Rest.TRANSITIONAL, MlFlag.PRESENT, Risk.HIGH),
        Rule(Deviation.MODERATE, Rest.RESTING, MlFlag.PRESENT, Risk.MEDIUM),
        Rule(Deviation.EXTREME, Rest.ACTIVE, MlFlag.PRESENT, Risk.HIGH),
        Rule(Deviation.EXTREME, Rest.TRANSITIONAL, MlFlag.PRESENT, Risk.HIGH),
        Rule(Deviation.EXTREME, Rest.RESTING, MlFlag.PRESENT, Risk.MEDIUM),

        // --- Layer 2 flagged it hard ---
        Rule(Deviation.MILD, Rest.ACTIVE, MlFlag.STRONG, Risk.MEDIUM),
        // A strong Layer 2 score lifts an otherwise quiet reading at the edge of the sleep window.
        Rule(Deviation.MILD, Rest.TRANSITIONAL, MlFlag.STRONG, Risk.MEDIUM),
        Rule(Deviation.MILD, Rest.RESTING, MlFlag.STRONG, Risk.LOW),
        Rule(Deviation.MODERATE, Rest.ACTIVE, MlFlag.STRONG, Risk.HIGH),
        Rule(Deviation.MODERATE, Rest.TRANSITIONAL, MlFlag.STRONG, Risk.HIGH),
        Rule(Deviation.MODERATE, Rest.RESTING, MlFlag.STRONG, Risk.MEDIUM),
        Rule(Deviation.EXTREME, Rest.ACTIVE, MlFlag.STRONG, Risk.HIGH),
        Rule(Deviation.EXTREME, Rest.TRANSITIONAL, MlFlag.STRONG, Risk.HIGH),
        Rule(Deviation.EXTREME, Rest.RESTING, MlFlag.STRONG, Risk.MEDIUM)
    )

    private enum class Deviation { MILD, MODERATE, EXTREME }

    private enum class Rest { ACTIVE, TRANSITIONAL, RESTING }

    private enum class MlFlag { NONE, PRESENT, STRONG }

    /**
     * Runs the inference and returns the risk level to store. The sets overlap, so a reading
     * near a boundary fires two rules partly.
     */
    fun classify(inputs: Inputs): Risk {
        val deviation = Deviation.entries.associateWith { membership(it, inputs.deviationScore) }
        val rest = Rest.entries.associateWith { membership(it, inputs.restExpectation) }
        val mlFlag = MlFlag.entries.associateWith { membership(it, inputs.mlFlagScore) }

        val strengths = RULES.map { rule ->
            // min for AND across the three antecedents.
            rule.risk to min(
                min(deviation.getValue(rule.deviation), rest.getValue(rule.rest)),
                mlFlag.getValue(rule.mlFlag)
            )
        }

        val centroid = defuzzify(strengths) ?: return Risk.MEDIUM
        return when {
            centroid < LOW_CEILING -> Risk.LOW
            centroid < MEDIUM_CEILING -> Risk.MEDIUM
            else -> Risk.HIGH
        }
    }

    /**
     * Centre of gravity of the aggregated output, or null if no rule fired. Sampled rather
     * than solved analytically. [classify] answers Medium for null, so an unclassifiable
     * anomaly still asks the senior a question they can dismiss.
     */
    private fun defuzzify(strengths: List<Pair<Risk, Double>>): Double? {
        var weighted = 0.0
        var total = 0.0
        for (i in 0..SAMPLES) {
            val y = i.toDouble() / SAMPLES
            // max-aggregation across rules, each clipped to its own firing strength.
            val aggregated = strengths.maxOf { (risk, strength) -> min(strength, outputMembership(risk, y)) }
            weighted += y * aggregated
            total += aggregated
        }
        return if (total <= 0.0) null else weighted / total
    }

    private fun membership(set: Deviation, z: Double): Double = when (set) {
        // Shouldered at the bottom, since every forwarded reading is at least a mild deviation.
        Deviation.MILD -> ramp(z, 3.25, 2.75)
        Deviation.MODERATE -> triangle(z, 2.9, 3.6, 4.4)
        // Shouldered at the top for the same reason in reverse: there is no ceiling on a z-score.
        Deviation.EXTREME -> ramp(z, 3.8, 5.0)
    }

    private fun membership(set: Rest, rest: Double): Double = when (set) {
        Rest.ACTIVE -> ramp(rest, 0.35, 0.0)
        Rest.TRANSITIONAL -> triangle(rest, 0.15, 0.5, 0.85)
        Rest.RESTING -> ramp(rest, 0.65, 1.0)
    }

    /**
     * Where the sets sit relative to [com.pup.seenior.detection.IsolationForestDetector.THRESHOLD] (0.58).
     *
     * [MlFlag.NONE] is shouldered at the bottom so an absent score (0.0) has full membership,
     * and [MlFlag.STRONG] at the top. The three must sum to 1.0 at every score (NONE hands over
     * to PRESENT across 0.50-0.62, PRESENT to STRONG across 0.62-0.78); a gap once made the
     * risk fall as the score rose. The test `risk never decreases as the ml_flag score grows`
     * guards this.
     */
    private fun membership(set: MlFlag, score: Double): Double = when (set) {
        MlFlag.NONE -> ramp(score, 0.62, 0.50)
        MlFlag.PRESENT -> triangle(score, 0.50, 0.62, 0.78)
        MlFlag.STRONG -> ramp(score, 0.62, 0.78)
    }

    private fun outputMembership(risk: Risk, y: Double): Double = when (risk) {
        Risk.LOW -> ramp(y, 0.35, 0.0)
        Risk.MEDIUM -> triangle(y, 0.25, 0.5, 0.75)
        Risk.HIGH -> ramp(y, 0.65, 1.0)
    }

    /**
     * How much stillness is expected at [minuteOfDay], from the senior's declared hours.
     *
     * 0.0 while awake, 1.0 inside the sleep window, with a linear ramp of [RAMP_MINUTES] around
     * waking and bedtime. Takes the "HH:mm" strings as stored, so it is testable on its own.
     */
    fun restExpectation(minuteOfDay: Int, wakeTime: String, sleepTime: String): Double {
        val wake = SeedBaselineGenerator.parseToMinuteOfDay(wakeTime)
        val sleep = SeedBaselineGenerator.parseToMinuteOfDay(sleepTime)

        val awakeLength = ((sleep - wake) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        // Identical wake and sleep times: treat the whole day as waking so detection stays on.
        if (awakeLength == 0) return 0.0

        val sinceWake = ((minuteOfDay - wake) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        if (sinceWake >= awakeLength) return 1.0

        val untilSleep = awakeLength - sinceWake
        val ramp = min(RAMP_MINUTES, awakeLength / 2)
        if (ramp == 0) return 0.0

        val justWoken = 1.0 - (sinceWake.toDouble() / ramp)
        val nearlyBed = 1.0 - (untilSleep.toDouble() / ramp)
        return maxOf(justWoken, nearlyBed).coerceIn(0.0, 1.0)
    }

    /**
     * Whether [minuteOfDay] falls inside the senior's declared nap. Alerts inside it would be
     * false positives by construction, so detection is suppressed there. Mostly useful in the
     * first two weeks, before real data includes the nap. Only Layers 1 and 2 use this; a
     * fall or SOS during a nap still alerts.
     */
    fun isWithinNapWindow(minuteOfDay: Int, napTime: String?, napDurationMinutes: Int?): Boolean {
        val start = napTime?.let { SeedBaselineGenerator.parseToMinuteOfDay(it) } ?: return false
        val duration = napDurationMinutes ?: return false
        if (duration <= 0) return false
        val offset = ((minuteOfDay - start) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return offset < duration
    }

    /**
     * Seconds since the senior's declared nap ended, or null if they declared no nap.
     *
     * [isWithinNapWindow] checks the reading's own time, but `inactivity_duration` and
     * `screen_idle_duration` are running counters that keep climbing through the nap, so just
     * after it ends the counter already holds the whole nap. Clipping the reading to this value
     * (like [SeedBaselineGenerator.secondsSinceBlockStart] does for block starts) avoids false
     * alerts right after the senior gets up. Away from the nap the value is large, so taking
     * `min` with the block elapsed time changes nothing.
     */
    fun secondsSinceNapEnd(timestampMillis: Long, napTime: String?, napDurationMinutes: Int?): Long? {
        val start = napTime?.let { SeedBaselineGenerator.parseToMinuteOfDay(it) } ?: return null
        val duration = napDurationMinutes ?: return null
        if (duration <= 0) return null
        val end = (start + duration) % MINUTES_PER_DAY
        val minutesSince = ((minuteOfDay(timestampMillis) - end) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        // Plus seconds in the current minute, matching [secondsSinceBlockStart].
        return minutesSince * 60L + secondOfMinute(timestampMillis)
    }

    /**
     * Minute of the day a timestamp falls on, in the device's time zone, to compare with the
     * senior's local wake, sleep and nap times.
     */
    fun minuteOfDay(timestampMillis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    /** Seconds elapsed inside the current minute, on the same clock as [minuteOfDay]. */
    private fun secondOfMinute(timestampMillis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        return calendar.get(Calendar.SECOND)
    }

    /** Rises 0→1 from [from] to [to], or falls 1→0 when [from] is the larger. */
    private fun ramp(x: Double, from: Double, to: Double): Double =
        if (from < to) ((x - from) / (to - from)).coerceIn(0.0, 1.0)
        else ((from - x) / (from - to)).coerceIn(0.0, 1.0)

    private fun triangle(x: Double, start: Double, peak: Double, end: Double): Double =
        min(ramp(x, start, peak), ramp(x, end, peak))

    private const val MINUTES_PER_DAY = 24 * 60

    /** How long either side of waking and of bedtime counts as neither awake nor asleep. */
    private const val RAMP_MINUTES = 60

    private const val SAMPLES = 200

    private const val LOW_CEILING = 0.4
    private const val MEDIUM_CEILING = 0.7
}
