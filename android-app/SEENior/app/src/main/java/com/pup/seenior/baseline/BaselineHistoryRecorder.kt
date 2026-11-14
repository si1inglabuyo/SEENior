package com.pup.seenior.baseline

import com.pup.seenior.database.dao.BaselineDao
import com.pup.seenior.database.dao.BaselineHistoryDao
import com.pup.seenior.database.dao.DailyAggregateDao
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.BaselineHistory
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SeniorOnboarding
import java.time.LocalDate

/**
 * Keeps one copy of the Routine Fingerprint per day, so the family app and the barangay dashboard
 * can show how a routine was learned. Local first: this builds the table, and `RoutineSyncer`
 * sends the last 30 days.
 *
 * Two kinds of row, told apart by [BaselineHistory.isEstimate]:
 *  - **Saved** ([recordToday]): the fingerprint the phone really held that day.
 *  - **Estimated** ([backfillIfEmpty]): the older fingerprints were overwritten and never saved,
 *    so each past day is worked out again from the stored `Daily_Aggregates` by replaying the
 *    same calculation the nightly update uses ([BaselineUpdater.cellsFrom]). It applies *today's*
 *    rules to old data, so it follows the same trend but is not what the detector used that day.
 *
 * Nothing here reads `Sensor_Data`, and no per-day aggregate leaves the phone: only the finished
 * median and MAD per cell are ever stored in the history.
 */
object BaselineHistoryRecorder {

    /** Same window the fingerprint itself uses. */
    private const val WINDOW_DAYS = 14

    /** How long the phone keeps copies. The cloud keeps 30 days; the phone keeps a little more. */
    private const val KEEP_DAYS = 90L

    /** Saves the fingerprint the phone holds now as today's copy, replacing today's estimate. */
    suspend fun recordToday(
        seniorId: Int,
        today: LocalDate,
        baselineDao: BaselineDao,
        historyDao: BaselineHistoryDao
    ) {
        val current = baselineDao.getAllBySeniorOnce(seniorId)
        if (current.isEmpty()) return
        historyDao.insertAll(current.map { it.toHistory(today.toString(), isEstimate = false) })
        historyDao.pruneBefore(seniorId, today.minusDays(KEEP_DAYS).toString())
    }

    /** Heartbeat entry point: makes sure today has a saved copy, without redoing it every 15 minutes. */
    suspend fun ensureToday(
        seniorId: Int,
        today: LocalDate,
        baselineDao: BaselineDao,
        historyDao: BaselineHistoryDao
    ) {
        if (historyDao.savedCountOn(seniorId, today.toString()) > 0) return
        recordToday(seniorId, today, baselineDao, historyDao)
    }

    /**
     * Fills the history from the stored aggregates, once: a phone that already has history is
     * left alone, so this can be called on every run.
     */
    suspend fun backfillIfEmpty(
        seniorId: Int,
        onboarding: SeniorOnboarding,
        dailyAggregateDao: DailyAggregateDao,
        historyDao: BaselineHistoryDao
    ) {
        if (historyDao.count(seniorId) > 0) return
        val rows = reconstruct(seniorId, onboarding, dailyAggregateDao.getAllOnce(seniorId))
        if (rows.isNotEmpty()) historyDao.insertAll(rows)
    }

    /**
     * Replays the nightly update day by day. The state starts as the pure questionnaire seed
     * (shown for the day before the first aggregate), and after each stored day it takes the
     * cells that day's window would have moved, keeping the rest, exactly as the stored
     * fingerprint does. Pure, so a test can check it without a database.
     */
    internal suspend fun reconstruct(
        seniorId: Int,
        onboarding: SeniorOnboarding,
        aggregates: List<DailyAggregate>
    ): List<BaselineHistory> {
        val dates = aggregates.map { it.date }.distinct().sorted()
        if (dates.isEmpty()) return emptyList()

        val seeds = SeedBaselineGenerator.generate(seniorId, onboarding)
            .associateBy { it.featureName to it.timeBlock }
        val state = seeds.toMutableMap()

        val out = mutableListOf<BaselineHistory>()
        val dayBeforeFirst = LocalDate.parse(dates.first()).minusDays(1).toString()
        state.values.mapTo(out) { it.toHistory(dayBeforeFirst, isEstimate = true) }

        for (date in dates) {
            val cells = BaselineUpdater.cellsFrom(seniorId, onboarding, seeds) { timeBlock ->
                aggregates
                    .filter { it.timeBlock == timeBlock && it.date <= date }
                    .sortedByDescending { it.date }
                    .take(WINDOW_DAYS)
            }
            cells.forEach { state[it.featureName to it.timeBlock] = it }
            state.values.mapTo(out) { it.toHistory(date, isEstimate = true) }
        }
        return out
    }

    private fun Baseline.toHistory(date: String, isEstimate: Boolean) = BaselineHistory(
        seniorId = seniorId,
        date = date,
        featureName = featureName,
        timeBlock = timeBlock,
        medianValue = medianValue,
        madValue = madValue,
        sampleCount = sampleCount,
        isSeed = isSeed,
        isEstimate = isEstimate
    )
}
