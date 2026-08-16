package com.pup.seenior.detection

import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.SensorData

/**
 * Demo trigger for the anomaly-detection pipeline.
 *
 * This does NOT fabricate an alert. It fabricates a single sensor *reading* — an implausibly long
 * stretch of no movement — and then hands it to the real [MedianMadDetector], which computes a
 * real Modified Z-Score against this senior's real baseline and writes the alert itself. Everything
 * downstream of the reading is production code: the z-score, the medium/high cutoff, the dedup
 * against an already-active alert, the row that lands in `Alerts`.
 *
 * CLAUDE.md §10 endorses exactly this — detection accuracy is validated by injecting known sensor
 * values, not by waiting for a real emergency. It is also the only practical option: a genuinely
 * stationary test device took ~75 minutes to cross even the moderate threshold during testing.
 *
 * The synthetic reading is deliberately **never written to `Sensor_Data`**. Doing so would let it
 * flow into the nightly aggregation and permanently corrupt the senior's Routine Fingerprint with
 * an event that never happened.
 */
object AnomalySimulator {

    /** Multiple of the effective MAD to sit above the median. Comfortably past the 3.5 "extreme"
     *  cutoff so the demo reliably produces a HIGH-risk alert rather than landing near the
     *  medium/high boundary and varying run to run. */
    private const val TARGET_Z_SCORE = 4.0

    private const val INACTIVITY = "inactivity_duration"

    sealed interface Result {
        /** The detector produced (or upgraded into) an alert. [alert] is null when an existing
         *  one was upgraded rather than a new row written — that alert's response chain is
         *  already running and must not be started a second time. */
        data class Triggered(val zScore: Double, val alert: Alert?) : Result
        /** No baseline exists for this feature in the current time block, so the detector had
         *  nothing to compare against and correctly did nothing. */
        data object NoBaseline : Result
        /** An alert for this trigger type is already working its way through the escalation
         *  chain. The detector dedups into it by design rather than spawning a duplicate. */
        data object AlreadyActive : Result
        data object NoSenior : Result
    }

    suspend fun simulateProlongedInactivity(db: SeniorAppDatabase): Result {
        val senior = db.seniorDao().getOnboardedSenior() ?: return Result.NoSenior
        val onboarding = db.seniorOnboardingDao().getBySeniorId(senior.seniorId) ?: return Result.NoSenior

        val now = System.currentTimeMillis()
        val timeBlock = SeedBaselineGenerator
            .resolveTimeBlock(now, onboarding.wakeTime, onboarding.sleepTime)
            .name.lowercase()

        val baselineDao = db.baselineDao()
        val alertDao = db.alertDao()

        val inactivityBaseline =
            baselineDao.getBaselineByFeatureAndTimeBlock(senior.seniorId, INACTIVITY, timeBlock)
                ?: return Result.NoBaseline

        if (alertDao.getActiveAlert(senior.seniorId, "inactivity") != null) return Result.AlreadyActive

        // Invert the detector's own formula so the reading lands at a known z-score against this
        // senior's actual baseline, whatever that baseline happens to be. A hardcoded "8 hours
        // still" would read as extreme for one senior and unremarkable for another.
        val madFloor = SeedBaselineGenerator.MIN_MAD_FLOOR[INACTIVITY] ?: 1.0
        val effectiveMad = maxOf(inactivityBaseline.madValue, madFloor)
        val inactivitySeconds = inactivityBaseline.medianValue + TARGET_Z_SCORE * effectiveMad

        // Movement and screen-idle are pinned to their own medians (z = 0) so this produces one
        // clean inactivity alert instead of three simultaneous ones. The detector evaluates every
        // feature it has a baseline for, and a real "collapsed on the floor" reading would breach
        // several at once — but one alert at a time is what the wellness prompt is built to show.
        val movementMedian = baselineDao
            .getBaselineByFeatureAndTimeBlock(senior.seniorId, "movement_score", timeBlock)
            ?.medianValue ?: 0.0
        val screenIdleMedian = baselineDao
            .getBaselineByFeatureAndTimeBlock(senior.seniorId, "screen_idle_duration", timeBlock)
            ?.medianValue ?: 0.0

        val syntheticReading = SensorData(
            seniorId = senior.seniorId,
            timestamp = now,
            timeBlock = timeBlock,
            movementScore = movementMedian,
            inactivityDuration = inactivitySeconds.toLong(),
            screenIdleDuration = screenIdleMedian.toLong(),
            screenUnlockCount = 0,
            isCharging = false,
            stepCount = 0
        )

        val created = MedianMadDetector.evaluate(senior.seniorId, syntheticReading, baselineDao, alertDao)

        return if (alertDao.getActiveAlert(senior.seniorId, "inactivity") != null) {
            Result.Triggered(TARGET_Z_SCORE, created.firstOrNull { it.triggerType == "inactivity" })
        } else {
            // Defensive: the detector declined to alert. Only reachable if the baseline changed
            // between the read above and the evaluate call.
            Result.NoBaseline
        }
    }
}
