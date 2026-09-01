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

    /** Which side of the median is the side worth acting on. See [FEATURE_CONCERN]. */
    private enum class Concern { ABOVE_MEDIAN, BELOW_MEDIAN }

    /**
     * The direction of deviation that means something may be wrong, per feature.
     *
     * The Modified Z-Score of CLAUDE.md §5 is a distance and carries no sign: `|current - median|`
     * scores a senior who is moving *more* than usual exactly as high as one who has stopped
     * moving. Only one of those is a reason to ask if she is safe.
     *
     * It is not a small effect at the margins, either — it is most of the false positives. The
     * seed baseline sets `madValue = median * 0.4`, so `median / mad` is exactly 2.5 for every
     * seeded feature, which puts a reading of **zero** precisely on [MODERATE_THRESHOLD]. A
     * senior holding her phone reads inactivity 0 and screen-idle 0, and the app told her it had
     * noticed she hadn't moved in a while — while she was moving.
     *
     * The score itself is unchanged, and still stored unchanged on the alert. This decides which
     * half of it is worth acting on, which is a different question from how far from normal the
     * reading is, and CLAUDE.md §14 requires those to stay separate.
     */
    private val FEATURE_CONCERN = mapOf(
        // Unusually still, and unusually disengaged from the phone.
        "inactivity_duration" to Concern.ABOVE_MEDIAN,
        "screen_idle_duration" to Concern.ABOVE_MEDIAN,
        // Unusually little movement. Agitation is not what this system is watching for.
        "movement_score" to Concern.BELOW_MEDIAN,
    )

    /**
     * The features whose readings are running counters rather than per-sample measurements.
     *
     * Both mean "seconds since the last time something happened", so they climb straight across a
     * time-block boundary while the Baseline they are scored against changes at it. See
     * [SeedBaselineGenerator.secondsSinceBlockStart] for what that did in production and why the
     * reading is clipped to the part of the streak that belongs to the current block.
     */
    private val RUNNING_COUNTER_FEATURES = setOf("inactivity_duration", "screen_idle_duration")

    /**
     * What one reading produced.
     *
     * [created] are alerts whose response chain the caller must start. [upgraded] are ids of
     * alerts that were already open and have just been re-classified as more serious: their chain
     * is already running and must not be started a second time, but the cloud copy now says the
     * wrong thing and the caller has to push the new level up (see
     * [com.pup.seenior.alerts.AlertEscalator.syncSeverity]). Low-risk anomalies appear in neither
     * -- they are recorded and nobody is told (CLAUDE.md §5).
     */
    data class Findings(val created: List<Alert>, val upgraded: List<Int>)

    /** Runs Layer 1 and Layer 3 over one reading. See [Findings] for what comes back and what the
     *  caller owes each part of it. */
    suspend fun evaluate(
        seniorId: Int,
        sensorData: SensorData,
        onboarding: SeniorOnboarding,
        baselineDao: BaselineDao,
        alertDao: AlertDao,
        /**
         * Seconds of the current time block already elapsed, used to clip
         * [RUNNING_COUNTER_FEATURES]. Defaults to the real elapsed time. [AnomalySimulator]
         * passes null on purpose: an injected reading (CLAUDE.md §10) stands in for a stretch of
         * stillness the demo has no time to wait out, and clipping it to the few real minutes of
         * the block would defeat the injection.
         */
        blockElapsedSeconds: Long? = SeedBaselineGenerator.secondsSinceBlockStart(
            sensorData.timestamp, onboarding.wakeTime, onboarding.sleepTime
        )
    ): Findings {
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
            return Findings(emptyList(), emptyList())
        }

        val restExpectation =
            FuzzyRiskClassifier.restExpectation(minuteOfDay, onboarding.wakeTime, onboarding.sleepTime)

        val created = mutableListOf<Alert>()
        val upgraded = mutableListOf<Int>()
        val readings = mapOf(
            "inactivity_duration" to sensorData.inactivityDuration.toDouble(),
            "movement_score" to sensorData.movementScore,
            "screen_idle_duration" to sensorData.screenIdleDuration.toDouble()
        ).mapValues { (featureName, value) ->
            // A streak that began in an earlier block is not evidence about this one. Only the
            // running counters are clipped; movement_score is measured fresh every sample and
            // means the same thing wherever it is read.
            if (blockElapsedSeconds != null && featureName in RUNNING_COUNTER_FEATURES) {
                minOf(value, blockElapsedSeconds.toDouble())
            } else {
                value
            }
        }

        for ((featureName, currentValue) in readings) {
            val baseline = baselineDao.getBaselineByFeatureAndTimeBlock(seniorId, featureName, sensorData.timeBlock)
                ?: continue

            // Deviating the safe way is not an anomaly (see [FEATURE_CONCERN]). Checked before
            // the score rather than after, so a reading nobody would act on never reaches Layer 3
            // and never lands in the log as an anomaly that was merely judged unremarkable.
            val deviatesTowardConcern = when (FEATURE_CONCERN.getValue(featureName)) {
                Concern.ABOVE_MEDIAN -> currentValue > baseline.medianValue
                Concern.BELOW_MEDIAN -> currentValue < baseline.medianValue
            }
            if (!deviatesTowardConcern) continue

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
                    // Reported to the caller so the cloud copy can be corrected. Everyone outside
                    // this phone is still looking at the level the alert was first sent with.
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
