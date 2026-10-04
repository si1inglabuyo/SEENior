package com.pup.seenior.aggregation


import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pup.seenior.alerts.AlertResponder
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SensorData
import com.pup.seenior.database.entities.SeniorOnboarding
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.detection.IsolationForestDetector
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

        // Needed before the loop, since the open block is defined by this senior's wake and sleep times.
        val onboarding = database.seniorOnboardingDao().getBySeniorId(senior.seniorId)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val now = System.currentTimeMillis()

        // Logical day, not calendar day (see SeedBaselineGenerator.logicalDayMillis), so a night
        // isn't split in two. With no onboarding row it falls back to the calendar date, which is
        // safe because the deferral below refuses to roll up today's data in that case.
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
         * Aggregate only blocks that can no longer receive samples, and leave the open block's
         * raw rows for the next run. Rebuilding an already rolled-up block from only the later
         * rows used to shorten max-based fields like `total_inactivity_duration`, and it hit
         * `night` every day (this worker runs at 02:00, before the night block ends). Waiting
         * for the block to close builds each one once, from all of it.
         */
        val (open, closed) = groups.entries.partition { (key, _) ->
            val (date, timeBlock) = key
            // With no onboarding row we can't tell which block is open, so nothing dated today
            // is touched. A delayed roll-up is harmless; a destructive one can't be undone.
            date == today && (openBlock == null || timeBlock == openBlock)
        }

        for ((key, rows) in closed) {
            val (date, timeBlock) = key
            val aggregate = buildAggregate(senior.seniorId, date, timeBlock, rows, onboarding)
            dailyAggregateDao.deleteByDateAndTimeBlock(senior.seniorId, date, timeBlock)
            dailyAggregateDao.insert(aggregate)
        }

        // Only what was rolled up. Marking the open block's rows would delete samples this deferral protects.
        val aggregatedIds = closed.flatMap { (_, rows) -> rows }.map { it.dataId }
        if (open.isNotEmpty()) {
            android.util.Log.i(
                "NightlyAggregation",
                "Deferred ${open.sumOf { it.value.size }} row(s) in the still-open block"
            )
        }
        // Room expands `IN (:dataIds)` into one parameter per ID and SQLite's limit is 999, so chunk.
        aggregatedIds.chunked(900).forEach { chunk -> sensorDataDao.markAsAggregated(chunk) }
        sensorDataDao.deleteAggregated()

        // The updater blends against this senior's seed values, so it needs their onboarding
        // answers. Without them, skip rather than overwrite the baseline with unblended data.
        if (onboarding != null) {
            com.pup.seenior.baseline.BaselineUpdater.updateForSenior(
                senior.seniorId,
                onboarding,
                database.baselineDao(),
                dailyAggregateDao
            )

            runIsolationForest(database, senior.seniorId, onboarding)
        }

        return Result.success()
    }

    /**
     * Layer 2's once-a-day pass, run here because the aggregates and baseline were just
     * refreshed. Wrapped and never fatal: the roll-up, purge and Layer 1 fingerprint are
     * essential, and a Layer 2 failure should be logged without failing the nightly run.
     */
    private suspend fun runIsolationForest(
        database: SeniorAppDatabase,
        seniorId: Int,
        onboarding: SeniorOnboarding
    ) {
        try {
            val outcome = IsolationForestDetector.run(
                seniorId,
                onboarding,
                database.dailyAggregateDao(),
                database.baselineDao(),
                database.alertDao(),
                database.mlModelMetadataDao()
            )
            android.util.Log.i("NightlyAggregation", "Isolation Forest: $outcome")

            // Only a raised alert gets a response chain. Logged notes, cold-start bail-outs and naps stay silent.
            if (outcome is IsolationForestDetector.Outcome.Raised) {
                AlertResponder.onAlertCreated(applicationContext, database, outcome.alert)
            }
        } catch (e: Exception) {
            android.util.Log.e("NightlyAggregation", "Isolation Forest pass failed", e)
        }
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
         * inactivity_duration and screen_idle_duration are running "seconds since X" counters,
         * so a block's longest streak is its max reading; averaging would halve it. But the
         * counters climb across block boundaries, so each reading is clipped to how much of its
         * own block had elapsed, as MedianMadDetector does. Without this, an impossible value
         * (26,652 s of morning stillness in a 15,600 s block) reached the Baseline.
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

        // Lives in [StepTotals] because the counter misbehaves in ways worth testing directly.
        val totalSteps = StepTotals.forBlock(rows)

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
            isChargingMajority = isChargingMajority,
            // The only moment this can be taken: after the rows are deleted, nothing can say
            // whether a block came from a full 52 readings or only a few.
            sampleCount = rows.size
        )
    }
}