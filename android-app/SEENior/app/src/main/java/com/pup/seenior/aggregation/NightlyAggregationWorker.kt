package com.pup.seenior.aggregation


import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SensorData
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

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val groups = unaggregated.groupBy { row -> dateFormat.format(Date(row.timestamp)) to row.timeBlock }

        for ((key, rows) in groups) {
            val (date, timeBlock) = key
            val aggregate = buildAggregate(senior.seniorId, date, timeBlock, rows)
            dailyAggregateDao.deleteByDateAndTimeBlock(senior.seniorId, date, timeBlock)
            dailyAggregateDao.insert(aggregate)
        }

        val aggregatedIds = unaggregated.map { it.dataId }
        // Room expands `IN (:dataIds)` into one bound SQL parameter per ID; SQLite's
        // default limit is 999. Chunk so a backlog (missed nightly runs) can't blow past it.
        aggregatedIds.chunked(900).forEach { chunk -> sensorDataDao.markAsAggregated(chunk) }
        sensorDataDao.deleteAggregated()

        com.pup.seenior.baseline.BaselineUpdater.updateForSenior(
            senior.seniorId,
            database.baselineDao(),
            dailyAggregateDao
        )

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

        val avgScreenIdleDuration = rows.map { it.screenIdleDuration }.average().toLong()
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