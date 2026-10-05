package com.pup.seenior.detection

import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.SensorData

/**
 * Demo trigger for the anomaly-detection pipeline. It doesn't fabricate an alert: it
 * fabricates one sensor reading (an implausibly long stretch of no movement) and hands it to
 * the real [MedianMadDetector], which computes a real z-score against this senior's baseline
 * and writes the alert. This follows the spec's approach of validating detection by injecting
 * known sensor values; a stationary test phone took ~75 minutes to cross the moderate threshold.
 *
 * The reading is never written to `Sensor_Data`, or it would corrupt the Routine Fingerprint.
 */
object AnomalySimulator {

    /** Multiple of the effective MAD above the median, comfortably past the 3.5 "extreme" cutoff so the demo reliably gives HIGH. */
    private const val TARGET_Z_SCORE = 4.0

    sealed interface Result {
        /** The detector produced (or upgraded into) an alert. [alert] is null when an existing one
         *  was upgraded, whose response chain is already running. */
        data class Triggered(val zScore: Double, val alert: Alert?) : Result
        /** No baseline for this feature in the current time block, so nothing was compared. */
        data object NoBaseline : Result
        /** An alert for this trigger type is already in the escalation chain; the detector dedups into it. */
        data object AlreadyActive : Result
        data object NoSenior : Result
        /** The reading crossed the threshold but Layer 3 judged it unremarkable for this hour, so
         *  it was logged and nobody was told. This is the graduated response working. */
        data class LoggedOnly(val zScore: Double) : Result
        /** Inside the senior's declared nap, where stillness is the expected reading (§6). */
        data object SuppressedByNap : Result
    }

    /** The Layer 1 signals the demo can push past their threshold, one per alert type. */
    enum class Signal(val featureName: String, val triggerType: String) {
        INACTIVITY("inactivity_duration", "inactivity"),
        SCREEN_IDLE("screen_idle_duration", "screen_idle"),
        LOW_MOVEMENT("movement_score", "movement")
    }

    suspend fun simulateProlongedInactivity(db: SeniorAppDatabase): Result =
        simulate(db, Signal.INACTIVITY)

    suspend fun simulate(db: SeniorAppDatabase, signal: Signal): Result {
        val senior = db.seniorDao().getOnboardedSenior() ?: return Result.NoSenior
        val onboarding = db.seniorOnboardingDao().getBySeniorId(senior.seniorId) ?: return Result.NoSenior

        val now = System.currentTimeMillis()

        // Checked here too, so the demo can say why nothing happened.
        if (FuzzyRiskClassifier.isWithinNapWindow(
                FuzzyRiskClassifier.minuteOfDay(now),
                onboarding.napTime.takeIf { onboarding.hasNap },
                onboarding.napDurationMinutes
            )
        ) {
            return Result.SuppressedByNap
        }

        val timeBlock = SeedBaselineGenerator
            .resolveTimeBlock(now, onboarding.wakeTime, onboarding.sleepTime)
            .name.lowercase()

        val baselineDao = db.baselineDao()
        val alertDao = db.alertDao()

        val targetBaseline =
            baselineDao.getBaselineByFeatureAndTimeBlock(senior.seniorId, signal.featureName, timeBlock)
                ?: return Result.NoBaseline

        if (alertDao.getActiveAlert(senior.seniorId, signal.triggerType) != null) return Result.AlreadyActive

        // Invert the detector's formula so the reading lands at a known z-score against this
        // senior's actual baseline, whatever it is.
        val madFloor = SeedBaselineGenerator.MIN_MAD_FLOOR[signal.featureName] ?: 1.0
        val effectiveMad = maxOf(targetBaseline.madValue, madFloor)
        val target = when (signal) {
            Signal.LOW_MOVEMENT ->
                // The concerning side is below the median, and a score can't go under zero.
                (targetBaseline.medianValue - TARGET_Z_SCORE * effectiveMad).coerceAtLeast(0.0)
            else -> targetBaseline.medianValue + TARGET_Z_SCORE * effectiveMad
        }

        // Every other signal is pinned to its median (z = 0), so this produces one clean alert
        // of the requested type instead of three at once.
        suspend fun median(featureName: String): Double = baselineDao
            .getBaselineByFeatureAndTimeBlock(senior.seniorId, featureName, timeBlock)
            ?.medianValue ?: 0.0

        val syntheticReading = SensorData(
            seniorId = senior.seniorId,
            timestamp = now,
            timeBlock = timeBlock,
            movementScore = if (signal == Signal.LOW_MOVEMENT) target else median("movement_score"),
            inactivityDuration =
                (if (signal == Signal.INACTIVITY) target else median("inactivity_duration")).toLong(),
            screenIdleDuration =
                (if (signal == Signal.SCREEN_IDLE) target else median("screen_idle_duration")).toLong(),
            screenUnlockCount = 0,
            isCharging = false,
            stepCount = 0
        )

        val findings = MedianMadDetector.evaluate(
            senior.seniorId,
            syntheticReading,
            onboarding,
            baselineDao,
            alertDao,
            // Null on purpose: the detector normally clips running counters to the elapsed part
            // of the block, which would undo an injected reading standing in for hours of stillness.
            blockElapsedSeconds = null
        )

        val created = findings.created.firstOrNull { it.triggerType == signal.triggerType }
        return if (alertDao.getActiveAlert(senior.seniorId, signal.triggerType) != null) {
            Result.Triggered(created?.deviationScore ?: TARGET_Z_SCORE, created)
        } else {
            // No open alert, so Layer 3 answered Low: the z-score wasn't worth telling anyone at
            // this hour. Expected at night and during declared rest.
            Result.LoggedOnly(TARGET_Z_SCORE)
        }
    }
}
