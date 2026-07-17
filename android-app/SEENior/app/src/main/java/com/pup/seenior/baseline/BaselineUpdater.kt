package com.pup.seenior.baseline

import com.pup.seenior.database.dao.BaselineDao
import com.pup.seenior.database.dao.DailyAggregateDao
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.detection.MedianMad

object BaselineUpdater {
    private  const val ROLLING_WINDOWS_DAYS = 14
    // Below this many daily samples, a real median/MAD is too unstable to replace the
    // conservative seed baseline (CLAUDE.md §6 describes a gradual Day-1-to-Day-14
    // replacement, not a single-sample overwrite).
    private const val MIN_SAMPLES_TO_REPLACE_SEED = 3
    private val TIME_BLOCKS = listOf("morning", "afternoon", "evening", "night")

    suspend fun updateForSenior(
        seniorId: Int,
        baselineDao: BaselineDao,
        dailyAggregateDao: DailyAggregateDao
    ) {
        for (timeBlock in TIME_BLOCKS) {
            val rows = dailyAggregateDao.getRecentByTimeBlock(seniorId, timeBlock, ROLLING_WINDOWS_DAYS)

            if (rows.size < MIN_SAMPLES_TO_REPLACE_SEED) continue

            updateFeature(seniorId, timeBlock, "movement_score", rows.map { it.avgMovementScore }, baselineDao)
            updateFeature(seniorId, timeBlock, "inactivity_duration", rows.map { it.totalInactivityDuration.toDouble() }, baselineDao)
            updateFeature(seniorId, timeBlock, "screen_idle_duration", rows.map { it.avgScreenIdleDuration.toDouble() }, baselineDao)
            updateFeature(seniorId, timeBlock, "screen_unlock_count", rows.map { it.totalScreenUnlocks.toDouble() }, baselineDao)
            updateFeature(seniorId, timeBlock, "step_count", rows.map { it.totalSteps.toDouble() }, baselineDao)
        }
    }

    private suspend fun updateFeature(
        seniorId: Int,
        timeBlock: String,
        featureName: String,
        values: List<Double>,
        baselineDao: BaselineDao
    ) {
        val median = MedianMad.median(values)
        val mad = MedianMad.mad(values, median)

        baselineDao.replaceFeatureBaseline(
            Baseline(
                seniorId = seniorId,
                featureName = featureName,
                timeBlock = timeBlock,
                medianValue = median,
                madValue = mad,
                sampleCount = values.size,
                isSeed = false
            )
        )
    }
}