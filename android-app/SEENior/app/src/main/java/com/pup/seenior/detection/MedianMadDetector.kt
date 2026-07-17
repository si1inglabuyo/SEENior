package com.pup.seenior.detection

import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.dao.AlertDao
import com.pup.seenior.database.dao.BaselineDao
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.SensorData
import java.util.UUID

object MedianMadDetector {

    private const val MODERATE_THRESHOLD = 2.5
    private const val EXTREME_THRESHOLD = 3.5

    private val FEATURE_TO_TRIGGER = mapOf(
        "inactivity_duration" to "inactivity",
        "movement_score" to "movement",
        "screen_idle_duration" to "screen_idle"
    )

    suspend fun evaluate(seniorId: Int, sensorData: SensorData, baselineDao: BaselineDao, alertDao: AlertDao) {
        val readings = mapOf(
            "inactivity_duration" to sensorData.inactivityDuration.toDouble(),
            "movement_score" to sensorData.movementScore,
            "screen_idle_duration" to sensorData.screenIdleDuration.toDouble()
        )

        for ((featureName, currentValue) in readings) {
            val baseline = baselineDao.getBaselineByFeatureAndTimeBlock(seniorId, featureName, sensorData.timeBlock)
                ?: continue

            val madFloor = SeedBaselineGenerator.MIN_MAD_FLOOR[featureName] ?: 1.0
            val zScore = MedianMad.deviationsScore(currentValue, baseline.medianValue, baseline.madValue, madFloor)

            if (zScore < MODERATE_THRESHOLD) continue

            val triggerType = FEATURE_TO_TRIGGER.getValue(featureName)

            // Provisional risk_level from the z-score cutoff alone; Fuzzy Logic (build-order
            // step 8) will later combine this with Isolation Forest + context to refine it.
            val riskLevel = if (zScore >= EXTREME_THRESHOLD) "high" else "medium"

            val active = alertDao.getActiveAlert(seniorId, triggerType)
            if (active != null) {
                // Don't downgrade or spam duplicate rows — only upgrade medium -> high.
                if (active.riskLevel != "high" && riskLevel == "high") {
                    alertDao.updateSeverity(active.alertId, riskLevel, zScore)
                }
                continue
            }

            alertDao.insert(
                Alert(
                    seniorId = seniorId,
                    syncId = UUID.randomUUID().toString(),
                    triggerType = triggerType,
                    riskLevel = riskLevel,
                    timeBlock = sensorData.timeBlock,
                    deviationScore = zScore
                )
            )
        }
    }
}