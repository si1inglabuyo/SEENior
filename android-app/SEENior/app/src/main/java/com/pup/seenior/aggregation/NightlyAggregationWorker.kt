package com.pup.seenior.aggregation


import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SensorData
import com.pup.seenior.database.entities.SeniorOnboarding
import com.pup.seenior.baseline.SeedBaselineGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NightlyAggregationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = SeniorAppDatabase.getInstance(applicationContext)
        val senior = database.seniorDao().getOnboardedSenior() ?: return Result.success()

        val sensorDataDao = database.sensorDataDao()
        val dailyAggregateDao = database.dailyAggregateDao()

        val unaggregated = sensorDataDao.getUnaggregatedSensorData(senior.seniorId)
        if (unaggregated.isEmpty()) return Result.success()

        // Needed before the loop, not after, because the open block below is defined by this
        // senior's own wake and sleep times.
        val onboarding = database.seniorOnboardingDao().getBySeniorId(senior.seniorId)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val now = System.currentTimeMillis()

        // Logical day, not calendar day -- see SeedBaselineGenerator.logicalDayMillis. Grouping a
        // night by calendar date splits it in two and mixes each half with a different night.
        //
        // With no onboarding row there are no wake/sleep times to place the night with, so this
        // falls back to the calendar date. Safe, because the deferral below already refuses to
        // roll up anything dated today when onboarding is missing.
        fun logicalDate(timestamp: Long): String = dateFormat.format(
            Date(
                onboarding?.let {
                    SeedBaselineGenerator.logicalDayMillis(timestamp, it.wakeTime, it.sleepTime)
                } ?: timestamp
            )
        )

        val today = logicalDate(now)
        val openBlock = onboarding?.let {
            SeedBaselineGenerator.resolveTimeBlock(now, it.wakeTime, it.sleepTime).name.lowercase()
        }

        val groups = unaggregated.groupBy { row -> logicalDate(row.timestamp) to row.timeBlock }

        /*
         * Aggregate only blocks that can no longer receive samples, and leave the open one's raw
         * rows where they are for the next run.
         *
         * The delete-then-insert below exists so a repeated run does not duplicate a row, but it
         * used to be destructive: a block that had already been rolled up would be REBUILT from
         * whatever rows arrived since, and the earlier ones were long deleted. That silently
         * shortened every max-based field -- `total_inactivity_duration` is the block's longest
         * streak, and a rebuild from the tail of the block cannot see the streak in its head.
         *
         * It hit `night` every single day: this worker runs at 02:00, rolls up night-so-far, then
         * the rest of that same night block accumulates until wake time and replaces it on the
         * next run. Evidence it was live on the pilot handset: `aggregate_id` 4 is missing from
         * Agnes's table, deleted and reinserted under a new id.
         *
         * Waiting for the block to close means each one is built exactly once, from all of it.
         */
        val (open, closed) = groups.entries.partition { (key, _) ->
            val (date, timeBlock) = key
            // With no onboarding row there is no way to know which block is open, so nothing
            // dated today is touched. Conservative: a delayed roll-up costs nothing, a
            // destructive one cannot be undone.
            date == today && (openBlock == null || timeBlock == openBlock)
        }

        for ((key, rows) in closed) {
            val (date, timeBlock) = key
            val aggregate = buildAggregate(senior.seniorId, date, timeBlock, rows, onboarding)
            dailyAggregateDao.deleteByDateAndTimeBlock(senior.seniorId, date, timeBlock)
            dailyAggregateDao.insert(aggregate)
        }

        // Only what was actually rolled up. Marking the open block's rows here would delete them
        // on the next line and lose the very samples this deferral is protecting.
        val aggregatedIds = closed.flatMap { (_, rows) -> rows }.map { it.dataId }
        if (open.isNotEmpty()) {
            android.util.Log.i(
                "NightlyAggregation",
                "Deferred ${open.sumOf { it.value.size }} row(s) in the still-open block"
            )
        }
        // Room expands `IN (:dataIds)` into one bound SQL parameter per ID; SQLite's
        // default limit is 999. Chunk so a backlog (missed nightly runs) can't blow past it.
        aggregatedIds.chunked(900).forEach { chunk -> sensorDataDao.markAsAggregated(chunk) }
        sensorDataDao.deleteAggregated()

        // The updater blends real data against this senior's seed values, so it needs the
        // onboarding answers those seeds were built from. No onboarding row means no seed to
        // blend against, and overwriting a baseline with unblended early data is the bug this
        // is here to prevent -- so skip rather than fall back.
        if (onboarding != null) {
            com.pup.seenior.baseline.BaselineUpdater.updateForSenior(
                senior.seniorId,
                onboarding,
                database.baselineDao(),
                dailyAggregateDao
            )
        }

        return Result.success()
    }

    private fun buildAggregate(
        seniorId: Int,
        date: String,
        timeBlock: String,
        rows: List<SensorData>,
        onboarding: SeniorOnboarding?
    ): DailyAggregate {
        val avgMovementScore = rows.map { it.movementScore }.average()

        /*
         * inactivity_duration and screen_idle_duration are running "seconds since X last
         * happened" counters, not per-poll deltas, so the block's longest streak is its max
         * reading -- averaging a running counter halves it and would drag the baseline median
         * below what the block actually looked like.
         *
         * But the counters keep climbing straight across a block boundary, so a reading taken
         * early in a block can be describing stillness that belongs to the previous one. Each
         * reading is therefore clipped to how much of its own block had elapsed when it was
         * taken, so a block is only ever summarised on what happened inside it.
         *
         * MedianMadDetector already clips exactly this way -- that is what stopped alert 20's
         * 10:05 false alarm. Without the same clip here the impossible value never fires an
         * alert but still reaches the Baseline, and later Isolation Forest: `aggregate_id` 12 on
         * the pilot handset recorded 26,652 s of morning stillness inside a morning block only
         * 15,600 s long.
         */
        fun clipToBlock(row: SensorData, reading: Long): Long = onboarding?.let {
            minOf(
                reading,
                SeedBaselineGenerator.secondsSinceBlockStart(row.timestamp, it.wakeTime, it.sleepTime)
            )
        } ?: reading

        val totalInactivityDuration = rows.maxOf { clipToBlock(it, it.inactivityDuration) }
        val avgScreenIdleDuration = rows.maxOf { clipToBlock(it, it.screenIdleDuration) }
        val totalScreenUnlocks = rows.sumOf { it.screenUnlockCount }

        // step_count is the raw cumulative-since-boot sensor reading. A device reboot
        // mid-block resets the counter, so max-min would silently floor to 0 and lose
        // real steps; sum positive deltas between timestamp-ordered readings instead,
        // treating any decrease as a reboot boundary (counter restarted from 0).
        val totalSteps = rows.sortedBy { it.timestamp }.zipWithNext { prev, curr ->
            val delta = curr.stepCount - prev.stepCount
            if (delta >= 0) delta else curr.stepCount
        }.sum().coerceAtLeast(0)

        val chargingCount = rows.count { it.isCharging }
        val isChargingMajority = chargingCount > rows.size / 2

        return DailyAggregate(
            seniorId = seniorId,
            date = date,
            timeBlock = timeBlock,
            avgMovementScore = avgMovementScore,
            totalInactivityDuration = totalInactivityDuration,
            avgScreenIdleDuration = avgScreenIdleDuration,
            totalScreenUnlocks = totalScreenUnlocks,
            totalSteps = totalSteps,
            isChargingMajority = isChargingMajority
        )
    }
}