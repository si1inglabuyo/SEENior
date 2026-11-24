package com.pup.seenior.ui.routine

import com.pup.seenior.aggregation.ActiveTimes
import com.pup.seenior.aggregation.StepTotals
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.BaselineHistory
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SensorData
import com.pup.seenior.detection.FalseAlarmTolerance
import com.pup.seenior.ui.family.RoutineSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Pure logic behind the senior's "My routine" screen: what is usual, how a day compares with it,
 * what the phone has learned lately and how careful alerts are right now. Display only: nothing
 * here feeds detection, and nothing leaves the phone.
 *
 * Every figure is one the phone already keeps. The two stillness figures are each block's
 * **longest** stretch (`inactivity_duration` and `screen_idle_duration` are running counters and
 * a block's value is the maximum reading, see `NightlyAggregationWorker.buildAggregate`), so the
 * wording must say "longest", never "average".
 */
object SeniorRoutineModel {

    val BLOCKS = listOf("morning", "afternoon", "evening", "night")

    /** Features shown, in display order. Movement is deliberately left out of this screen. */
    const val STEPS = "step_count"
    const val UNLOCKS = "screen_unlock_count"
    const val STILL = "inactivity_duration"
    const val IDLE = "screen_idle_duration"
    val SHOWN_FEATURES = listOf(STILL, STEPS, UNLOCKS, IDLE)

    /** One block's four figures. Null means the phone has no value for it. */
    data class Figures(
        val steps: Int?,
        val unlocks: Int?,
        val stillSeconds: Long?,
        val idleSeconds: Long?
    ) {
        val isEmpty: Boolean get() = steps == null && unlocks == null && stillSeconds == null && idleSeconds == null
    }

    enum class Verdict { AS_USUAL, QUIETER, BUSIER, NOT_COMPARED }

    enum class DayState { DONE, IN_PROGRESS, NOT_YET }

    data class Window(val block: String, val startMinute: Int, val durationMinutes: Int)

    /** First and last sustained activity inside a block, as minutes after midnight. */
    data class Times(val firstMinute: Int?, val lastMinute: Int?)

    // ---------------------------------------------------------------- blocks

    fun windows(wakeTime: String, sleepTime: String): List<Window> =
        SeedBaselineGenerator.computeTimeBlocks(wakeTime, sleepTime)
            .map { Window(it.block.name.lowercase(), it.startMinute, it.durationMinutes) }
            .sortedBy { BLOCKS.indexOf(it.block) }

    // ---------------------------------------------------------------- usual

    private fun median(rows: List<Baseline>, block: String, feature: String): Double? =
        rows.firstOrNull { it.timeBlock == block && it.featureName == feature }?.medianValue

    fun usual(rows: List<Baseline>, block: String): Figures = Figures(
        steps = median(rows, block, STEPS)?.roundToInt(),
        unlocks = median(rows, block, UNLOCKS)?.roundToInt(),
        stillSeconds = median(rows, block, STILL)?.roundToLong(),
        idleSeconds = median(rows, block, IDLE)?.roundToLong()
    )

    private fun Double.roundToLong(): Long = Math.round(this)

    /**
     * A block counts as learned once it no longer rests mostly on the setup guess. Same rule as
     * the family card (`RoutineSummary.MIN_LEARNED_DAYS`), so the two apps agree.
     */
    fun isLearned(rows: List<Baseline>, block: String): Boolean {
        val mine = rows.filter { it.timeBlock == block }
        return mine.isNotEmpty() && mine.none { it.isSeed && it.sampleCount < RoutineSummary.MIN_LEARNED_DAYS }
    }

    /** Days of real data behind the fingerprint, 0 to 14. */
    fun learnedDays(rows: List<Baseline>): Int = (rows.maxOfOrNull { it.sampleCount } ?: 0).coerceIn(0, 14)

    /**
     * Median first and last active minute per block over the last [ActiveTimes.WINDOW_DAYS] days.
     * Withheld below [ActiveTimes.MIN_DAYS] days so it is never "what happened yesterday".
     */
    fun usualTimes(aggregates: List<DailyAggregate>, today: LocalDate): Map<String, Times> {
        val since = today.minusDays(ActiveTimes.WINDOW_DAYS.toLong() - 1).toString()
        val recent = aggregates.filter { it.date >= since && it.date <= today.toString() }
        return BLOCKS.associateWith { block ->
            val mine = recent.filter { it.timeBlock == block }
            val first = mine.mapNotNull { it.firstActiveMinute }
            val last = mine.mapNotNull { it.lastActiveMinute }
            Times(
                firstMinute = if (first.size >= ActiveTimes.MIN_DAYS) ActiveTimes.median(first) else null,
                lastMinute = if (last.size >= ActiveTimes.MIN_DAYS) ActiveTimes.median(last) else null
            )
        }
    }

    // ---------------------------------------------------------------- a day against the usual

    fun figuresOf(aggregate: DailyAggregate) = Figures(
        steps = aggregate.totalSteps,
        unlocks = aggregate.totalScreenUnlocks,
        stillSeconds = aggregate.totalInactivityDuration,
        idleSeconds = aggregate.avgScreenIdleDuration
    )

    /**
     * Today's figures for one block from the raw readings the phone still holds (they are rolled
     * up at 02:00). It repeats `NightlyAggregationWorker.buildAggregate` for the four figures
     * shown here, including the clip of the running counters to the block they sit in. Keep the
     * two in step: this one is display only, the worker's is what detection learns from.
     */
    fun figuresFromRaw(rows: List<SensorData>, wakeTime: String, sleepTime: String): Figures? {
        if (rows.isEmpty()) return null
        fun clipped(row: SensorData, reading: Long): Long =
            minOf(reading, SeedBaselineGenerator.secondsSinceBlockStart(row.timestamp, wakeTime, sleepTime))
        return Figures(
            steps = StepTotals.forBlock(rows),
            unlocks = rows.sumOf { it.screenUnlockCount },
            stillSeconds = rows.maxOf { clipped(it, it.inactivityDuration) },
            idleSeconds = rows.maxOf { clipped(it, it.screenIdleDuration) }
        )
    }

    /**
     * Soft comparison, never a score. Quieter when steps fall well short of the usual or the
     * longest still spell runs well over it; busier when steps run well over. Blocks still on the
     * setup guess are not compared, since the "usual" there is a guess.
     */
    fun verdict(usual: Figures, day: Figures?, learned: Boolean): Verdict {
        if (!learned || day == null || day.isEmpty) return Verdict.NOT_COMPARED
        val usualSteps = usual.steps
        val usualStill = usual.stillSeconds
        val stepsRatio = if (usualSteps != null && usualSteps >= MIN_STEPS_TO_COMPARE && day.steps != null)
            day.steps.toDouble() / usualSteps else null
        val stillRatio = if (usualStill != null && usualStill >= MIN_STILL_TO_COMPARE_SECONDS && day.stillSeconds != null)
            day.stillSeconds.toDouble() / usualStill else null
        if (stepsRatio == null && stillRatio == null) return Verdict.NOT_COMPARED
        return when {
            (stepsRatio != null && stepsRatio < QUIETER_STEPS_RATIO) ||
                (stillRatio != null && stillRatio > QUIETER_STILL_RATIO) -> Verdict.QUIETER
            stepsRatio != null && stepsRatio > BUSIER_STEPS_RATIO -> Verdict.BUSIER
            else -> Verdict.AS_USUAL
        }
    }

    private const val MIN_STEPS_TO_COMPARE = 100
    private const val MIN_STILL_TO_COMPARE_SECONDS = 600L
    private const val QUIETER_STEPS_RATIO = 0.6
    private const val QUIETER_STILL_RATIO = 1.5
    private const val BUSIER_STEPS_RATIO = 1.5

    /** The block the clock is in, among the four, as an index into [BLOCKS]. */
    fun dayState(block: String, openBlock: String): DayState {
        val i = BLOCKS.indexOf(block)
        val open = BLOCKS.indexOf(openBlock)
        return when {
            i < open -> DayState.DONE
            i == open -> DayState.IN_PROGRESS
            else -> DayState.NOT_YET
        }
    }

    // ---------------------------------------------------------------- what the phone learned

    /** One figure that moved: where it stood a week ago and where it stands now. */
    data class Change(val block: String, val feature: String, val from: Double, val to: Double)

    data class Changes(val items: List<Change>, val includesEstimates: Boolean)

    /**
     * Compares each learned cell's oldest copy in the last [lookbackDays] days with today's
     * fingerprint and keeps those that moved enough to mean something. Cells that are still the
     * setup guess on either side are skipped: that is the guess being replaced, not a routine
     * changing.
     */
    fun changes(
        history: List<BaselineHistory>,
        current: List<Baseline>,
        today: LocalDate,
        lookbackDays: Int = 7
    ): Changes {
        val since = today.minusDays(lookbackDays.toLong()).toString()
        val window = history.filter { it.date >= since && !it.isSeed }
        val out = mutableListOf<Change>()
        var estimate = false
        for (block in BLOCKS) {
            for (feature in SHOWN_FEATURES) {
                val now = current.firstOrNull { it.timeBlock == block && it.featureName == feature && !it.isSeed }
                    ?: continue
                val old = window.filter { it.timeBlock == block && it.featureName == feature }
                    .minByOrNull { it.date } ?: continue
                if (moved(feature, old.medianValue, now.medianValue)) {
                    out += Change(block, feature, old.medianValue, now.medianValue)
                    if (old.isEstimate) estimate = true
                }
            }
        }
        return Changes(out, estimate)
    }

    /** Big enough to mention: at least a fifth, and at least a meaningful absolute amount. */
    internal fun moved(feature: String, from: Double, to: Double): Boolean {
        val diff = kotlin.math.abs(to - from)
        val base = maxOf(kotlin.math.abs(from), 1e-9)
        val floor = when (feature) {
            STEPS -> 100.0
            UNLOCKS -> 2.0
            else -> 600.0 // still / idle, in seconds: ten minutes
        }
        return diff >= floor && diff / base >= 0.2
    }

    // ---------------------------------------------------------------- how careful alerts are

    private val ROUTINE_TRIGGERS = listOf("inactivity", "movement", "screen_idle")
    private val FALSE_ALARM = setOf("self_cancelled", "false_positive")
    private val REAL = setOf("acknowledged_family", "escalated_barangay", "resolved")

    /**
     * One signal in one block where false alarms have loosened the trigger. [answeredOn] are the
     * dates of the alerts that count; [extraMinutes] is the raise as extra tolerated time for the
     * two stillness signals, null for movement; [backToNormal] is when the count drops under two.
     */
    data class Care(
        val block: String,
        val trigger: String,
        val answeredOn: List<LocalDate>,
        val extraMinutes: Int?,
        val backToNormal: LocalDate
    )

    /**
     * Where things sit on the care card's line, as fractions from 0 to 1. The line is the last
     * [FalseAlarmTolerance.WINDOW_DAYS] days with today at the right edge (1), so a dot slides
     * left as it ages and falls off the edge when it stops counting. There is no end marker: the
     * phone is back to normal once fewer than two dots are left. An answer on the very first
     * day outside the line is held on its left edge.
     */
    data class Strip(val days: Int, val answered: List<Float>)

    /** Cells on the line, one per day. The span between the first and the last is one less. */
    const val STRIP_CELLS = FalseAlarmTolerance.WINDOW_DAYS

    fun strip(care: Care, today: LocalDate): Strip {
        val span = STRIP_CELLS - 1
        val start = today.minusDays(span.toLong())
        fun at(d: LocalDate): Float =
            (java.time.temporal.ChronoUnit.DAYS.between(start, d).toFloat() / span).coerceIn(0f, 1f)
        return Strip(days = span, answered = care.answeredOn.map { at(it) })
    }

    /**
     * Rebuilds, from the Alerts table alone, what [FalseAlarmTolerance] is doing right now.
     * It calls the same `explain` the detector uses, so the screen cannot drift from the gate.
     * Evidence follows the detector's rules: only false alarms scored from 2.5 up to (not
     * including) 3.5 count, and real alerts in the same signal and block take their share back.
     */
    fun care(alerts: List<Alert>, baseline: List<Baseline>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<Care> {
        val since = FalseAlarmTolerance.windowStart(nowMillis)
        val out = mutableListOf<Care>()
        for (block in BLOCKS) {
            for (trigger in ROUTINE_TRIGGERS) {
                val mine = alerts.filter {
                    it.triggerType == trigger && it.timeBlock == block && it.triggeredAt >= since
                }
                val falseAlarms = mine.filter { it.status in FALSE_ALARM && it.deviationScore != null }
                val real = mine.count { it.status in REAL }
                val breakdown = FalseAlarmTolerance.explain(falseAlarms.map { it.deviationScore!! }, real)
                if (breakdown.raise <= 0.0) continue

                val counted = falseAlarms
                    .filter { it.deviationScore!! >= FalseAlarmTolerance.BASE_THRESHOLD && it.deviationScore < FalseAlarmTolerance.CAP }
                    .sortedBy { it.triggeredAt }
                val dates = counted.map { Instant.ofEpochMilli(it.triggeredAt).atZone(zone).toLocalDate() }
                // The count falls under MIN_EVIDENCE when all but one of the counted alerts have aged out.
                val lapses = dates[(dates.size - FalseAlarmTolerance.MIN_EVIDENCE).coerceAtLeast(0)]
                    .plusDays(FalseAlarmTolerance.WINDOW_DAYS.toLong())

                val feature = when (trigger) {
                    "inactivity" -> STILL
                    "screen_idle" -> IDLE
                    else -> null
                }
                val extra = feature?.let { f ->
                    baseline.firstOrNull { it.timeBlock == block && it.featureName == f }?.let { row ->
                        val floor = SeedBaselineGenerator.MIN_MAD_FLOOR[f] ?: 1.0
                        breakdown.extraMinutes(maxOf(row.madValue, floor)).roundToInt()
                    }
                }
                out += Care(block, trigger, dates, extra, lapses)
            }
        }
        return out
    }
}
