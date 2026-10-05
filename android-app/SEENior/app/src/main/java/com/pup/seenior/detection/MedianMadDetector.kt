package com.pup.seenior.detection

import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.dao.AlertDao
import com.pup.seenior.database.dao.BaselineDao
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.SensorData
import com.pup.seenior.database.entities.SeniorOnboarding
import java.util.UUID

object MedianMadDetector {

    private const val MODERATE_THRESHOLD = 2.5

    /** Severity order, so [evaluate] can upgrade an open alert but never quietly downgrade one. */
    private val RISK_ORDER = listOf("low", "medium", "high")

    /**
     * How long one logged low-risk note stands in for repeats of the same quiet anomaly. A
     * senior lying still re-crosses the threshold every poll, which would write ~100 rows a
     * night. Longer than [com.pup.seenior.alerts.AlertEscalator.dedupeSecondsFor], since nobody
     * is waiting on these.
     */
    private const val LOGGED_DEDUPE_SECONDS = 3600L

    /** The status that keeps a low-risk alert out of every query that drives the response chain. */
    private const val STATUS_LOGGED = "logged"

    private val FEATURE_TO_TRIGGER = mapOf(
        "inactivity_duration" to "inactivity",
        "movement_score" to "movement",
        "screen_idle_duration" to "screen_idle"
    )

    /** Which side of the median is the side worth acting on. See [FEATURE_CONCERN]. */
    private enum class Concern { ABOVE_MEDIAN, BELOW_MEDIAN }

    /**
     * The direction of deviation that means something may be wrong, per feature.
     *
     * The z-score has no sign, so a senior moving more than usual scores as high as one who
     * stopped. Only the second is a reason to ask. This matters because the seed baseline's
     * `madValue = median * 0.4` puts a reading of zero exactly on [MODERATE_THRESHOLD], so a
     * senior holding her phone used to be told she hadn't moved. The score itself is stored
     * unchanged; this only decides which half is worth acting on.
     */
    private val FEATURE_CONCERN = mapOf(
        // Unusually still, and unusually disengaged from the phone.
        "inactivity_duration" to Concern.ABOVE_MEDIAN,
        "screen_idle_duration" to Concern.ABOVE_MEDIAN,
        // Unusually little movement. Agitation is not what this system is watching for.
        "movement_score" to Concern.BELOW_MEDIAN,
    )

    /**
     * Features whose readings are running counters ("seconds since X"). They climb straight
     * across a time-block boundary and across the end of a declared nap while the baseline
     * changes, so readings are clipped. See [SeedBaselineGenerator.secondsSinceBlockStart] and
     * [FuzzyRiskClassifier.secondsSinceNapEnd].
     */
    private val RUNNING_COUNTER_FEATURES = setOf("inactivity_duration", "screen_idle_duration")

    /**
     * What one reading produced. [created] are new alerts whose chain the caller must start.
     * [upgraded] are ids of open alerts re-classified as more serious: their chain is already
     * running, but the caller must push the new level to the cloud (see
     * [com.pup.seenior.alerts.AlertEscalator.syncSeverity]). Low-risk anomalies appear in neither.
     */
    data class Findings(val created: List<Alert>, val upgraded: List<Int>)

    /** Runs Layer 1 and Layer 3 over one reading. See [Findings] for what the caller owes each part. */
    suspend fun evaluate(
        seniorId: Int,
        sensorData: SensorData,
        onboarding: SeniorOnboarding,
        baselineDao: BaselineDao,
        alertDao: AlertDao,
        /**
         * Seconds of the current time block already elapsed, used to clip
         * [RUNNING_COUNTER_FEATURES]. [AnomalySimulator] passes null so an injected reading
         * isn't clipped.
         */
        blockElapsedSeconds: Long? = SeedBaselineGenerator.secondsSinceBlockStart(
            sensorData.timestamp, onboarding.wakeTime, onboarding.sleepTime
        )
    ): Findings {
        val minuteOfDay = FuzzyRiskClassifier.minuteOfDay(sensorData.timestamp)

        // The senior declared a nap here, so stillness is expected. Layer 0 and SOS don't come
        // through this function and are unaffected.
        if (FuzzyRiskClassifier.isWithinNapWindow(
                minuteOfDay,
                onboarding.napTime.takeIf { onboarding.hasNap },
                onboarding.napDurationMinutes
            )
        ) {
            return Findings(emptyList(), emptyList())
        }

        val restExpectation =
            FuzzyRiskClassifier.restExpectation(minuteOfDay, onboarding.wakeTime, onboarding.sleepTime)

        /*
         * How much of a running counter this reading may be scored on: the part of the streak
         * in this block and after the declared nap. Whichever excuses more applies, so it is a
         * min. Null (the simulator) means no clipping.
         */
        val clipCeilingSeconds = blockElapsedSeconds?.let { elapsed ->
            val sinceNapEnd = FuzzyRiskClassifier.secondsSinceNapEnd(
                sensorData.timestamp,
                onboarding.napTime.takeIf { onboarding.hasNap },
                onboarding.napDurationMinutes
            )
            if (sinceNapEnd == null) elapsed else minOf(elapsed, sinceNapEnd)
        }

        val created = mutableListOf<Alert>()
        val upgraded = mutableListOf<Int>()
        val readings = mapOf(
            "inactivity_duration" to sensorData.inactivityDuration.toDouble(),
            "movement_score" to sensorData.movementScore,
            "screen_idle_duration" to sensorData.screenIdleDuration.toDouble()
        ).mapValues { (featureName, value) ->
            // A streak from an earlier block or the nap isn't evidence about this one. Only
            // running counters are clipped.
            if (clipCeilingSeconds != null && featureName in RUNNING_COUNTER_FEATURES) {
                minOf(value, clipCeilingSeconds.toDouble())
            } else {
                value
            }
        }

        for ((featureName, currentValue) in readings) {
            val baseline = baselineDao.getBaselineByFeatureAndTimeBlock(seniorId, featureName, sensorData.timeBlock)
                ?: continue

            // Deviating the safe way is not an anomaly (see [FEATURE_CONCERN]). Checked before
            // scoring so such a reading never reaches Layer 3 or the log.
            val deviatesTowardConcern = when (FEATURE_CONCERN.getValue(featureName)) {
                Concern.ABOVE_MEDIAN -> currentValue > baseline.medianValue
                Concern.BELOW_MEDIAN -> currentValue < baseline.medianValue
            }
            if (!deviatesTowardConcern) continue

            val madFloor = SeedBaselineGenerator.MIN_MAD_FLOOR[featureName] ?: 1.0
            val zScore = MedianMad.deviationsScore(currentValue, baseline.medianValue, baseline.madValue, madFloor)

            if (zScore < MODERATE_THRESHOLD) continue

            val triggerType = FEATURE_TO_TRIGGER.getValue(featureName)

            // This block may have earned slack from two or more recent false alarms (see
            // [FalseAlarmTolerance]). A reading above 2.5 but below the loosened threshold is
            // logged and nobody is asked. Asked only after the reading clears 2.5, so ordinary
            // polls skip the lookup. The stored score and Layer 3 are unchanged.
            val since = FalseAlarmTolerance.windowStart(sensorData.timestamp)
            val threshold = FalseAlarmTolerance.thresholdFor(
                alertDao.getToleratedDeviationScores(seniorId, triggerType, sensorData.timeBlock, since),
                alertDao.getConfirmedAlertCount(seniorId, triggerType, sensorData.timeBlock, since)
            )
            if (zScore < threshold) {
                recordLowRisk(seniorId, triggerType, sensorData, zScore, alertDao)
                continue
            }

            // Layer 3: the classifier decides what the z-score is worth at this hour. The score
            // is stored unchanged alongside it; they are separate outputs.
            val risk = FuzzyRiskClassifier.classify(
                FuzzyRiskClassifier.Inputs(deviationScore = zScore, restExpectation = restExpectation)
            )

            if (risk == FuzzyRiskClassifier.Risk.LOW) {
                recordLowRisk(seniorId, triggerType, sensorData, zScore, alertDao)
                continue
            }

            val active = alertDao.getActiveAlert(seniorId, triggerType)
            if (active != null) {
                // Upgrade only: medium can become high, but an open alert is never talked down.
                if (RISK_ORDER.indexOf(risk.stored) > RISK_ORDER.indexOf(active.riskLevel)) {
                    alertDao.updateSeverity(active.alertId, risk.stored, zScore)
                    // Reported so the cloud copy can be corrected.
                    upgraded += active.alertId
                }
                continue
            }

            val alert = Alert(
                seniorId = seniorId,
                syncId = UUID.randomUUID().toString(),
                triggerType = triggerType,
                riskLevel = risk.stored,
                timeBlock = sensorData.timeBlock,
                deviationScore = zScore
            )
            created += alert.copy(alertId = alertDao.insert(alert).toInt())
        }

        return Findings(created, upgraded)
    }

    /**
     * Records a low-risk anomaly and tells nobody. The row is kept so the record shows what
     * the system saw and chose not to act on. Its [STATUS_LOGGED] status keeps it out of
     * [AlertDao.getUnacknowledgedAlerts] (no prompt) and [AlertDao.getActiveAlert].
     */
    private suspend fun recordLowRisk(
        seniorId: Int,
        triggerType: String,
        sensorData: SensorData,
        zScore: Double,
        alertDao: AlertDao
    ) {
        val notBefore = sensorData.timestamp - LOGGED_DEDUPE_SECONDS * 1000
        val existing = alertDao.getRecentLoggedAlert(seniorId, triggerType, notBefore)
        if (existing != null) {
            // Keep the worst reading of the hour, not the newest.
            if (zScore > (existing.deviationScore ?: 0.0)) {
                alertDao.updateSeverity(existing.alertId, FuzzyRiskClassifier.Risk.LOW.stored, zScore)
            }
            return
        }

        alertDao.insert(
            Alert(
                seniorId = seniorId,
                syncId = UUID.randomUUID().toString(),
                triggerType = triggerType,
                riskLevel = FuzzyRiskClassifier.Risk.LOW.stored,
                timeBlock = sensorData.timeBlock,
                deviationScore = zScore,
                status = STATUS_LOGGED
            )
        )
    }
}
