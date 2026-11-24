package com.pup.seenior.ui.routine

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.ui.routine.SeniorRoutineModel.BLOCKS
import com.pup.seenior.ui.routine.SeniorRoutineModel.DayState
import com.pup.seenior.ui.routine.SeniorRoutineModel.Figures
import com.pup.seenior.ui.routine.SeniorRoutineModel.Times
import com.pup.seenior.ui.routine.SeniorRoutineModel.Verdict
import com.pup.seenior.ui.wellness.WellnessMessages
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** Usual figures for one part of the day. */
data class UsualBlock(
    val window: SeniorRoutineModel.Window,
    val usual: Figures,
    val learned: Boolean,
    val times: Times
)

/** One part of a particular day (yesterday or today) set beside the usual. */
data class DayBlock(
    val window: SeniorRoutineModel.Window,
    val state: DayState,
    val figures: Figures?,
    /** First and last sustained activity that day, minutes after midnight. */
    val times: Times,
    val usualTimes: Times,
    val usual: Figures,
    val learned: Boolean,
    val verdict: Verdict
)

data class SeniorRoutineState(
    val fil: Boolean = false,
    val days: Int = 0,
    val usual: List<UsualBlock> = emptyList(),
    val yesterdayDate: LocalDate? = null,
    /** Null when no finished day is stored yet. */
    val yesterday: List<DayBlock>? = null,
    val todayDate: LocalDate? = null,
    val today: List<DayBlock> = emptyList(),
    val changes: SeniorRoutineModel.Changes = SeniorRoutineModel.Changes(emptyList(), false),
    val care: List<SeniorRoutineModel.Care> = emptyList(),
    val loaded: Boolean = false
)

/**
 * Backs "My routine". Reads only what the phone already holds locally (Baseline, the day
 * summaries, today's raw readings, the fingerprint history and the alerts) and writes nothing.
 */
class SeniorRoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SeniorAppDatabase.getInstance(application)

    var state by mutableStateOf(SeniorRoutineState())
        private set

    fun load() {
        viewModelScope.launch { state = build() }
    }

    private suspend fun build(): SeniorRoutineState {
        val senior = db.seniorDao().getOnboardedSenior() ?: return SeniorRoutineState(loaded = true)
        val onboarding = db.seniorOnboardingDao().getBySeniorId(senior.seniorId)
            ?: return SeniorRoutineState(loaded = true)
        val seniorId = senior.seniorId
        val fil = onboarding.languagePreference == WellnessMessages.FILIPINO
        val wake = onboarding.wakeTime
        val sleep = onboarding.sleepTime

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun logicalDate(ts: Long): String =
            dateFormat.format(Date(SeedBaselineGenerator.logicalDayMillis(ts, wake, sleep)))

        // "Today" is the logical day, so a senior up past midnight is still in the same day.
        val todayDate = LocalDate.parse(logicalDate(now))
        val yesterdayDate = todayDate.minusDays(1)

        val baseline = db.baselineDao().getAllBySeniorOnce(seniorId)
        val aggregates = db.dailyAggregateDao().getAllOnce(seniorId)
        val windows = SeniorRoutineModel.windows(wake, sleep)
        val usualTimes = SeniorRoutineModel.usualTimes(aggregates, todayDate)
        val emptyTimes = Times(null, null)

        val usual = windows.map { w ->
            UsualBlock(
                window = w,
                usual = SeniorRoutineModel.usual(baseline, w.block),
                learned = SeniorRoutineModel.isLearned(baseline, w.block),
                times = usualTimes[w.block] ?: emptyTimes
            )
        }

        // Yesterday: the finished day's summaries.
        val yesterdayRows = aggregates.filter { it.date == yesterdayDate.toString() }
        val yesterday = if (yesterdayRows.isEmpty()) null else usual.map { u ->
            val row = yesterdayRows.firstOrNull { it.timeBlock == u.window.block }
            val figures = row?.let { SeniorRoutineModel.figuresOf(it) }
            DayBlock(
                window = u.window,
                state = DayState.DONE,
                figures = figures,
                times = Times(row?.firstActiveMinute, row?.lastActiveMinute),
                usualTimes = u.times,
                usual = u.usual,
                learned = u.learned,
                verdict = SeniorRoutineModel.verdict(u.usual, figures, u.learned)
            )
        }

        // Today: summaries if a block was already rolled up, otherwise the raw readings so far.
        val openBlock = SeedBaselineGenerator.resolveTimeBlock(now, wake, sleep).name.lowercase()
        val todayAggregates = aggregates.filter { it.date == todayDate.toString() }
        val rawToday = db.sensorDataDao().getUnaggregatedSensorData(seniorId)
            .filter { logicalDate(it.timestamp) == todayDate.toString() }
        val today = usual.map { u ->
            val block = u.window.block
            val summary = todayAggregates.firstOrNull { it.timeBlock == block }
            val raw = rawToday.filter { it.timeBlock == block }
            val figures = summary?.let { SeniorRoutineModel.figuresOf(it) }
                ?: SeniorRoutineModel.figuresFromRaw(raw, wake, sleep)
            val times = if (summary != null) Times(summary.firstActiveMinute, summary.lastActiveMinute)
            else com.pup.seenior.aggregation.ActiveTimes.firstAndLastMinute(raw).let { Times(it.first, it.second) }
            val state = SeniorRoutineModel.dayState(block, openBlock)
            DayBlock(
                window = u.window,
                state = state,
                figures = figures,
                times = times,
                usualTimes = u.times,
                usual = u.usual,
                learned = u.learned,
                // A block still in progress is shown "so far" and is not judged against a whole block's usual.
                verdict = if (state == DayState.DONE) SeniorRoutineModel.verdict(u.usual, figures, u.learned)
                else Verdict.NOT_COMPARED
            )
        }

        val history = db.baselineHistoryDao().since(seniorId, todayDate.minusDays(8).toString())
        val changes = SeniorRoutineModel.changes(history, baseline, todayDate)

        val alerts = db.alertDao().getAllBySenior(seniorId).first()
        val care = SeniorRoutineModel.care(alerts, baseline, now, zone)

        return SeniorRoutineState(
            fil = fil,
            days = SeniorRoutineModel.learnedDays(baseline),
            usual = usual,
            yesterdayDate = yesterdayDate,
            yesterday = yesterday,
            todayDate = todayDate,
            today = today,
            changes = changes,
            care = care,
            loaded = true
        )
    }
}

internal fun Long.toLocalTime(zone: ZoneId = ZoneId.systemDefault()): String =
    java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        .format(Instant.ofEpochMilli(this).atZone(zone))
