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
        sensorDataDao.markAsAggregated(aggregatedIds)
        sensorDataDao.deleteAggregated()

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

        // step_count is the raw cumulative sensor reading, so the block's
        // step total is the delta between its highest and lowest reading.
        val totalSteps = (rows.maxOf { it.stepCount } - rows.minOf { it.stepCount }).coerceAtLeast(0)

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