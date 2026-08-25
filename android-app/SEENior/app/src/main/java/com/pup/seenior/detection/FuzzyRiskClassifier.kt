package com.pup.seenior.detection

import com.pup.seenior.baseline.SeedBaselineGenerator
import java.util.Calendar
import kotlin.math.min

/**
 * Layer 3 of the detection pipeline (CLAUDE.md §5): turns an anomaly signal into a proportionate
 * response rather than a binary alarm.
 *
 * This replaces a straight cutoff — `if (z >= 3.5) "high" else "medium"` — which could only ever
 * answer "how far from normal is this reading?" and never "how worrying is that, right now, for
 * this person?" Two identical z-scores mean different things at eight in the morning and at three
 * in the morning, and the difference is the whole point of the layer.
 *
 * **Mamdani inference, deliberately.** The inputs are fuzzified into overlapping sets, a rule base
 * fires with `min` for AND, the clipped output sets are aggregated with `max`, and the result is
 * defuzzified by centroid. A nest of if-statements would land on the same three words most of the
 * time and would not be fuzzy logic; near a boundary the two disagree, and it is exactly at the
 * boundaries that a graduated response earns its place. It also keeps the layer's output a
 * *separate* thing from the z-score and from Isolation Forest's path-length score, which CLAUDE.md
 * §14 requires and which a single blended number would destroy.
 *
 * No Android imports, so JUnit can drive it directly — the same reason [FallDetector] has none, and
 * what makes CLAUDE.md §10's simulated-data validation possible for this layer.
 *
 * **Layer 2 is not wired in yet.** When Isolation Forest lands (build-order step 7) it enters as a
 * third antecedent — an `ml_flag` membership alongside [deviation] and [rest] — which widens
 * [RULES] to a three-input table. It is left out rather than stubbed because an input that is
 * always null cannot be tested and would only have to be re-derived later.
 *
 * **There is deliberately no baseline-confidence input.** The obvious idea — damp risk while the
 * seed baseline is still being replaced (days 1–14) — double-counts caution that is already
 * applied. [com.pup.seenior.baseline.SeedBaselineGenerator] sets `madValue = maxOf(median * 0.4,
 * floor)`, a deliberately wide MAD, and MAD is the divisor of the z-score: wide MAD, smaller z,
 * fewer alerts. Damping again would make the cold-start window quieter still, in precisely the
 * period when least is known about the senior. Do not add it.
 */
object FuzzyRiskClassifier {

    /** The three levels of CLAUDE.md §5. [stored] is the `Alerts.risk_level` value. */
    enum class Risk(val stored: String) {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high")
    }

    /**
     * @param deviationScore the Modified Z-Score from Layer 1. Only ever at or above the moderate
     *   threshold, since the detector does not consult this layer below it.
     * @param restExpectation how much stillness is normal at this moment, 0.0 (fully waking hours)
     *   to 1.0 (deep in the declared sleep window). See [restExpectation].
     */
    data class Inputs(
        val deviationScore: Double,
        val restExpectation: Double
    )

    /**
     * The rule base, read as `deviation × rest → risk`.
     *
     * The diagonal is the argument the layer exists to make: the same deviation is High during
     * waking hours and Medium while the senior is expected to be asleep, because someone deeply
     * asleep is not an emergency — and a mild deviation at rest is not worth waking anyone for at
     * all, which is where Low comes from. Nothing at rest reaches High: if a genuine emergency
     * begins during sleep, the deviation keeps growing and the waking hours that follow escalate
     * it. Silence is bounded, not permanent.
     */
    private val RULES: List<Triple<Deviation, Rest, Risk>> = listOf(
        Triple(Deviation.MILD, Rest.ACTIVE, Risk.MEDIUM),
        Triple(Deviation.MILD, Rest.TRANSITIONAL, Risk.LOW),
        Triple(Deviation.MILD, Rest.RESTING, Risk.LOW),
        Triple(Deviation.MODERATE, Rest.ACTIVE, Risk.HIGH),
        Triple(Deviation.MODERATE, Rest.TRANSITIONAL, Risk.MEDIUM),
        Triple(Deviation.MODERATE, Rest.RESTING, Risk.LOW),
        Triple(Deviation.EXTREME, Rest.ACTIVE, Risk.HIGH),
        Triple(Deviation.EXTREME, Rest.TRANSITIONAL, Risk.HIGH),
        Triple(Deviation.EXTREME, Rest.RESTING, Risk.MEDIUM)
    )

    private enum class Deviation { MILD, MODERATE, EXTREME }

    private enum class Rest { ACTIVE, TRANSITIONAL, RESTING }

    /**
     * Runs the inference and returns the level to store on the alert.
     *
     * The sets overlap on purpose, so a reading near a boundary fires two rules partly rather than
     * one rule wholly, and the centroid lands between them.
     */
    fun classify(inputs: Inputs): Risk {
        val deviation = Deviation.entries.associateWith { membership(it, inputs.deviationScore) }
        val rest = Rest.entries.associateWith { membership(it, inputs.restExpectation) }

        val strengths = RULES.map { (d, r, risk) ->
            risk to min(deviation.getValue(d), rest.getValue(r))
        }

        val centroid = defuzzify(strengths) ?: return Risk.MEDIUM
        return when {
            centroid < LOW_CEILING -> Risk.LOW
            centroid < MEDIUM_CEILING -> Risk.MEDIUM
            else -> Risk.HIGH
        }
    }

    /**
     * Centre of gravity of the aggregated output, or null when no rule fired at all.
     *
     * Sampled rather than solved analytically: the aggregate is the max of several clipped
     * shapes and has no closed form worth deriving. [SAMPLES] over a unit interval is far finer
     * than three output buckets can resolve.
     *
     * A null means the antecedents landed outside every set, which the detector's own moderate
     * threshold should already prevent. [classify] answers Medium there rather than Low — an
     * unclassifiable anomaly is still an anomaly, and the failure has to be in the direction of
     * asking the senior a question they can dismiss.
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
        // Shouldered at the bottom: everything the detector forwards is at least a mild deviation,
        // so the set has to stay saturated below its peak rather than falling away to nothing.
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

    private fun outputMembership(risk: Risk, y: Double): Double = when (risk) {
        Risk.LOW -> ramp(y, 0.35, 0.0)
        Risk.MEDIUM -> triangle(y, 0.25, 0.5, 0.75)
        Risk.HIGH -> ramp(y, 0.65, 1.0)
    }

    /**
     * How much stillness is expected at [minuteOfDay], from the senior's own declared hours.
     *
     * 0.0 through the waking day, 1.0 once inside the sleep window, and a linear ramp across
     * [RAMP_MINUTES] either side of waking and of going to bed. The ramps matter: nobody is fully
     * awake the instant their alarm goes off, and a hard step would put a cliff in the middle of
     * the two moments a senior is most likely to be lying still for perfectly ordinary reasons.
     *
     * Takes the times as the "HH:mm" strings they are stored as, so this stays testable without
     * building a [com.pup.seenior.database.entities.SeniorOnboarding].
     */
    fun restExpectation(minuteOfDay: Int, wakeTime: String, sleepTime: String): Double {
        val wake = SeedBaselineGenerator.parseToMinuteOfDay(wakeTime)
        val sleep = SeedBaselineGenerator.parseToMinuteOfDay(sleepTime)

        val awakeLength = ((sleep - wake) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        // A senior who declared identical wake and sleep times has no awake window to speak of;
        // treat the whole day as waking rather than as permanent sleep, so detection stays on.
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
     * Whether [minuteOfDay] falls inside the senior's declared nap.
     *
     * A nap is the one stretch of daytime stillness the senior told us to expect, so an alert
     * raised inside it would be a false positive by construction (CLAUDE.md §6). Detection is
     * suppressed outright here rather than merely downgraded — the window is the senior's own
     * statement about their day, not a judgement call for the rule base.
     *
     * This matters most in the first fortnight. Once real data replaces the seed values, the
     * afternoon block's own median rises to include the nap and it stops registering as a
     * deviation at all; the window is chiefly cold-start protection.
     *
     * Only Layer 1 and Layer 2 consult this. A fall or an SOS during a nap still raises an alert,
     * because neither is a statement about how much the senior is moving.
     */
    fun isWithinNapWindow(minuteOfDay: Int, napTime: String?, napDurationMinutes: Int?): Boolean {
        val start = napTime?.let { SeedBaselineGenerator.parseToMinuteOfDay(it) } ?: return false
        val duration = napDurationMinutes ?: return false
        if (duration <= 0) return false
        val offset = ((minuteOfDay - start) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return offset < duration
    }

    /**
     * Minute of the day a timestamp falls on, in the device's own time zone.
     *
     * The senior's wake, sleep and nap times are local wall-clock strings they typed during
     * onboarding, so the reading has to be placed on the same clock to be compared with them.
     */
    fun minuteOfDay(timestampMillis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
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
