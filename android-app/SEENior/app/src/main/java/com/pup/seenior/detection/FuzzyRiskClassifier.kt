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
 * **Layer 2 is wired in** (build-order step 7, Phase 6). Isolation Forest's path-length score
 * enters as a third antecedent — an `ml_flag` membership alongside [deviation] and [rest] — which
 * widens [RULES] from nine rows to twenty-seven. The original nine are unchanged and still fire
 * exactly as they did: a Layer 1 alert carries no ml_flag score, [Inputs.mlFlagScore] defaults to
 * 0.0, and [MlFlag.NONE] then has full membership while the other two have none.
 *
 * **A Layer 2 finding cannot reach High on its own.** It is retrospective — a judgement about a
 * block that has already closed, produced by a job that runs once a day — so it is not the same
 * class of claim as a fall happening now. Alone (deviation 0, so [Deviation.MILD]) its ceiling is
 * Medium, which is the wellness prompt: the senior is asked, and answers, and that is the whole
 * intent. It reaches High only by *corroborating* a Layer 1 deviation that was already serious.
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
        val restExpectation: Double,
        /**
         * Isolation Forest's path-length anomaly score for this block, 0.0–1.0, or 0.0 when there
         * isn't one. Layer 1 alerts leave it at the default, which puts [MlFlag.NONE] at full
         * membership and reproduces the original nine rules exactly.
         *
         * Deliberately a *separate* input from [deviationScore] and never blended into it: they
         * are different measurements of different things (a z-score against a median, versus how
         * few random splits isolated the day), and CLAUDE.md §14 requires the three layers to
         * keep three distinct outputs.
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
     * The rule base, read as `deviation × rest × ml_flag → risk`.
     *
     * The rest diagonal is the argument this layer exists to make: the same deviation is High
     * during waking hours and Medium while the senior is expected to be asleep, because someone
     * deeply asleep is not an emergency — and a mild deviation at rest is not worth waking anyone
     * for at all, which is where Low comes from.
     *
     * **Two invariants hold across all twenty-seven rows, and both are load-bearing:**
     *
     * 1. *Nothing at full rest reaches High.* If a genuine emergency begins during sleep, the
     *    deviation keeps growing and the waking hours that follow escalate it. Silence is bounded,
     *    not permanent.
     * 2. *ml_flag alone never reaches High.* A Layer 2 finding arrives with deviation 0 — hence
     *    [Deviation.MILD] — so its whole row group tops out at Medium however strong the score is.
     *    Medium is the wellness prompt, which is the proportionate answer to "yesterday looked
     *    unusual": ask her. It raises High only where a Layer 1 deviation was *already* moderate or
     *    extreme and Layer 2 independently agrees, which is corroboration rather than a new claim.
     *
     * The nine [MlFlag.NONE] rows are the original table, unchanged, and must stay that way — they
     * are what every Layer 1 alert still runs through.
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
        // The MILD group is the pure-Layer-2 alert: capped at Medium by invariant 2, and dropped
        // to Low at rest, so the nightly job cannot wake a sleeping senior to ask about a block
        // that closed hours ago. The score is still written to the aggregate row either way.
        Rule(Deviation.MILD, Rest.ACTIVE, MlFlag.PRESENT, Risk.MEDIUM),
        Rule(Deviation.MILD, Rest.TRANSITIONAL, MlFlag.PRESENT, Risk.LOW),
        Rule(Deviation.MILD, Rest.RESTING, MlFlag.PRESENT, Risk.LOW),
        Rule(Deviation.MODERATE, Rest.ACTIVE, MlFlag.PRESENT, Risk.HIGH),
        // Layer 1 called it moderate and Layer 2 independently agrees the whole block was off.
        // Two different measurements concurring is worth more than either alone, which is the
        // entire reason for having a second layer.
        Rule(Deviation.MODERATE, Rest.TRANSITIONAL, MlFlag.PRESENT, Risk.HIGH),
        Rule(Deviation.MODERATE, Rest.RESTING, MlFlag.PRESENT, Risk.MEDIUM),
        Rule(Deviation.EXTREME, Rest.ACTIVE, MlFlag.PRESENT, Risk.HIGH),
        Rule(Deviation.EXTREME, Rest.TRANSITIONAL, MlFlag.PRESENT, Risk.HIGH),
        Rule(Deviation.EXTREME, Rest.RESTING, MlFlag.PRESENT, Risk.MEDIUM),

        // --- Layer 2 flagged it hard ---
        Rule(Deviation.MILD, Rest.ACTIVE, MlFlag.STRONG, Risk.MEDIUM),
        // The one place a strong Layer 2 score lifts an otherwise-quiet reading: on the edge of
        // the sleep window, where Low would mean the finding is never mentioned at all.
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
     * Runs the inference and returns the level to store on the alert.
     *
     * The sets overlap on purpose, so a reading near a boundary fires two rules partly rather than
     * one rule wholly, and the centroid lands between them.
     */
    fun classify(inputs: Inputs): Risk {
        val deviation = Deviation.entries.associateWith { membership(it, inputs.deviationScore) }
        val rest = Rest.entries.associateWith { membership(it, inputs.restExpectation) }
        val mlFlag = MlFlag.entries.associateWith { membership(it, inputs.mlFlagScore) }

        val strengths = RULES.map { rule ->
            // min for AND, across all three antecedents now — the same Mamdani inference, one
            // dimension wider.
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

    /**
     * Where the sets sit relative to [com.pup.seenior.detection.IsolationForestDetector.THRESHOLD]
     * (0.58), the value tuned against the nine cases in `IsolationForestTest`.
     *
     * [MlFlag.NONE] is shouldered at the bottom so an absent score — 0.0, which is every Layer 1
     * alert — has full membership and the original nine rules fire untouched. [MlFlag.STRONG] is
     * shouldered at the top because the score is bounded at 1.0 and anything past ~0.78 is as
     * isolated as the forest can report.
     *
     * **These three must sum to 1.0 at every score, and the edges are chosen for that and nothing
     * else** — NONE hands over to PRESENT across exactly 0.50–0.62, PRESENT to STRONG across
     * exactly 0.62–0.78. An earlier version left a gap between where NONE finished falling and
     * where PRESENT began rising; total firing strength collapsed inside it, and because the Low
     * and Medium rules shrank at different rates the defuzzified answer *fell* as the score rose.
     * A senior's day scoring more anomalous produced a calmer verdict. `risk never decreases as
     * the ml_flag score grows` in the test suite is what caught it and is what keeps it caught.
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
     * Seconds since the senior's declared nap ended, or null if they declared no nap.
     *
     * The companion to [isWithinNapWindow], and the reason it is not enough on its own.
     * [isWithinNapWindow] gates on the *reading's own* timestamp, which is the right test for a
     * per-sample measurement. But `inactivity_duration` and `screen_idle_duration` are running
     * counters — "seconds since the last time X happened" — and they keep climbing all through the
     * nap. The minute the window closes, the counter already holds the whole nap, and
     * [MedianMadDetector] scores it in full against a block median that expects nothing of the
     * kind.
     *
     * Measured on the pilot handset on 2026-09-16: her nap is declared 14:00 for 60 minutes and
     * her afternoon block starts 14:20, so alerts 100 and 101 fired at 15:23 and 15:28 at z = 7.26
     * and z = 8.20 — both `high`, both escalated, both self-cancelled — on a counter reading of
     * roughly 3,593 s that had been accumulating since about 14:28. Half of that stretch was the
     * nap she had told us about. Clipped to this value the same reading scores z = 2.13, under the
     * 2.5 threshold, and neither alert is raised.
     *
     * This is the same shape as the wake-time bug that [SeedBaselineGenerator.secondsSinceBlockStart]
     * exists to fix — a counter judged against a window that did not accumulate it — arriving at
     * the other end of the nap instead of at the start of the morning.
     *
     * Away from the nap the answer is naturally large (it climbs to a full day just before the next
     * one begins), so a caller taking `min` of this and the block elapsed time is unaffected on
     * every reading except the ones just after the senior gets up. Inside the nap the question does
     * not arise: [MedianMadDetector] has already returned by then.
     */
    fun secondsSinceNapEnd(timestampMillis: Long, napTime: String?, napDurationMinutes: Int?): Long? {
        val start = napTime?.let { SeedBaselineGenerator.parseToMinuteOfDay(it) } ?: return null
        val duration = napDurationMinutes ?: return null
        if (duration <= 0) return null
        val end = (start + duration) % MINUTES_PER_DAY
        val minutesSince = ((minuteOfDay(timestampMillis) - end) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        // Plus the seconds inside the current minute, matching [secondsSinceBlockStart] so the two
        // clips rise at the same rate and neither steps ahead of the other by up to a minute.
        return minutesSince * 60L + secondOfMinute(timestampMillis)
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
