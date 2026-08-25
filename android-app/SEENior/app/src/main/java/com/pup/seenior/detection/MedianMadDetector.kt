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
     * How long one logged low-risk note stands in for repeats of the same quiet anomaly.
     *
     * A senior lying still through the night re-crosses the moderate threshold on every
     * five-minute poll, and each one would otherwise write its own row — roughly a hundred a
     * night saying the same unremarkable thing. An hour keeps the record without the noise.
     * Deliberately longer than [com.pup.seenior.alerts.AlertEscalator.dedupeSecondsFor], which
     * governs alerts somebody is going to be told about; nobody is waiting on these.
     */
    private const val LOGGED_DEDUPE_SECONDS = 3600L

    /** The status that keeps a low-risk alert out of every query that drives the response chain. */
    private const val STATUS_LOGGED = "logged"

    private val FEATURE_TO_TRIGGER = mapOf(
        "inactivity_duration" to "inactivity",
        "movement_score" to "movement",
        "screen_idle_duration" to "screen_idle"
    )

    /** Returns the alerts this reading created, so the caller can start their response chain. An
     *  alert that was merely upgraded in severity is not returned — its chain is already running.
     *  Neither are low-risk ones: they are recorded and nobody is told (CLAUDE.md §5). */
    suspend fun evaluate(
        seniorId: Int,
        sensorData: SensorData,
        onboarding: SeniorOnboarding,
        baselineDao: BaselineDao,
        alertDao: AlertDao
    ): List<Alert> {
        val minuteOfDay = FuzzyRiskClassifier.minuteOfDay(sensorData.timestamp)

        // CLAUDE.md §6: the senior told us they nap here, so stillness is the expected reading and
        // an alert would be a false positive by construction. Layer 0 and the SOS button do not
        // come through this function and are unaffected — a fall during a nap is still a fall.
        if (FuzzyRiskClassifier.isWithinNapWindow(
                minuteOfDay,
                onboarding.napTime.takeIf { onboarding.hasNap },
                onboarding.napDurationMinutes
            )
        ) {
            return emptyList()
        }

        val restExpectation =
            FuzzyRiskClassifier.restExpectation(minuteOfDay, onboarding.wakeTime, onboarding.sleepTime)

        val created = mutableListOf<Alert>()
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

            // Layer 3 (CLAUDE.md §5). The z-score says how far from normal this reading is; the
            // classifier decides what that is worth at this hour of this senior's day. The score
            // itself is stored unchanged alongside it — the two are separate outputs and §14
            // requires them to stay that way.
            val risk = FuzzyRiskClassifier.classify(
                FuzzyRiskClassifier.Inputs(deviationScore = zScore, restExpectation = restExpectation)
            )

            if (risk == FuzzyRiskClassifier.Risk.LOW) {
                recordLowRisk(seniorId, triggerType, sensorData, zScore, alertDao)
                continue
            }

            val active = alertDao.getActiveAlert(seniorId, triggerType)
            if (active != null) {
                // Upgrade only. A senior whose situation worsens must be able to move from medium
                // to high, but an alert already being acted on must never be talked back down by
                // a later, calmer reading.
                if (RISK_ORDER.indexOf(risk.stored) > RISK_ORDER.indexOf(active.riskLevel)) {
                    alertDao.updateSeverity(active.alertId, risk.stored, zScore)
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

        return created
    }

    /**
     * Records a low-risk anomaly and tells nobody (CLAUDE.md §5).
     *
     * The row is written rather than dropped because the whole case for a graduated response
     * rests on being able to show afterwards what the system saw and chose not to act on. It
     * carries [STATUS_LOGGED] rather than "pending", which keeps it out of
     * [AlertDao.getUnacknowledgedAlerts] — so no wellness prompt — and out of
     * [AlertDao.getActiveAlert], so a quiet note can never stand in the way of a real alert for
     * the same signal minutes later. No alarm is armed and no notification is posted, because
     * this never reaches the caller that would do either.
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
            // Keep the worst reading of the hour rather than the newest, so the record reflects
            // how far the senior actually drifted and not merely where they finished.
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
