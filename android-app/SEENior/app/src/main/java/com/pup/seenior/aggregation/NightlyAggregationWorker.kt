package com.pup.seenior.aggregation


import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SensorData
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
        val today = dateFormat.format(Date(now))
        val openBlock = onboarding?.let {
            SeedBaselineGenerator.resolveTimeBlock(now, it.wakeTime, it.sleepTime).name.lowercase()
        }

        val groups = unaggregated.groupBy { row -> dateFormat.format(Date(row.timestamp)) to row.timeBlock }

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
            val aggregate = buildAggregate(senior.seniorId, date, timeBlock, rows)
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
        rows: List<SensorData>
    ): DailyAggregate {
        val avgMovementScore = rows.map { it.movementScore }.average()

        // inactivity_duration is a running "seconds since last movement" counter,
        // not a per-poll delta — the block's longest streak is its max reading.
        val totalInactivityDuration = rows.maxOf { it.inactivityDuration }

        // screen_idle_duration is a running "seconds since the screen was last on" counter
        // now, not a per-poll delta -- so the block's longest streak is its max reading, exactly
        // as for inactivity above. Averaging a running counter halves it and would drag the
        // baseline median below what the block actually looked like.
        val avgScreenIdleDuration = rows.maxOf { it.screenIdleDuration }
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