package com.pup.seenior.ui.demo

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.alerts.AlertResponder
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.detection.AggregateFeatures
import com.pup.seenior.detection.AggregateSimulator
import com.pup.seenior.detection.AnomalySimulator
import com.pup.seenior.detection.FallSimulator
import com.pup.seenior.detection.FalseAlarmTolerance
import com.pup.seenior.detection.IsolationForestDetector
import com.pup.seenior.detection.ToleranceExplanation
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Backs the Demo tab. Every button goes through the real detector for its layer (see
 * [AnomalySimulator], [FallSimulator]), so what it raises is what the live sensor stream would
 * raise from the same readings. The alerts are real rows and run the real escalation chain,
 * including the cloud sync and the family / barangay notifications.
 */
class DemoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SeniorAppDatabase.getInstance(application)

    var message by mutableStateOf<String?>(null)
        private set

    var running by mutableStateOf(false)
        private set

    fun simulateInactivity() = simulateSignal(AnomalySimulator.Signal.INACTIVITY)

    fun simulateScreenIdle() = simulateSignal(AnomalySimulator.Signal.SCREEN_IDLE)

    fun simulateLowMovement() = simulateSignal(AnomalySimulator.Signal.LOW_MOVEMENT)

    /** A reading inside the moderate band, the only kind false-alarm tolerance can loosen. */
    fun simulateModerateInactivity() = simulateSignal(
        AnomalySimulator.Signal.INACTIVITY, AnomalySimulator.DEMO_FALSE_ALARM_Z
    )

    /** The working behind the open block's gate, redrawn after every tolerance action. */
    var toleranceSteps by mutableStateOf<List<ToleranceExplanation.Step>?>(null)
        private set

    fun showTolerance() = launchDemo {
        refreshTolerance()
        "Showing the working for the open block."
    }

    private suspend fun refreshTolerance() {
        toleranceSteps = AnomalySimulator.toleranceState(db, AnomalySimulator.Signal.INACTIVITY)?.steps()
    }

    /** One alert in the open block that family acknowledged: a real alert, which takes back part of the slack. */
    fun plantRealAlert() = launchDemo {
        val block = AnomalySimulator.plantRealAlert(db, AnomalySimulator.Signal.INACTIVITY)
            ?: return@launchDemo "No senior profile on this phone."
        refreshTolerance()
        "Planted 1 real alert (acknowledged by family) in the $block block."
    }

    /** Two "I'm fine now" alerts at z = 3.0 in the open block, enough to lift its gate. */
    fun plantFalseAlarmHistory() = launchDemo {
        val signal = AnomalySimulator.Signal.INACTIVITY
        val block = AnomalySimulator.plantFalseAlarmHistory(db, signal)
            ?: return@launchDemo "No senior profile on this phone."
        refreshTolerance()
        "Planted 2 false alarms (z = 3.0) in the $block block."
    }

    fun removeFalseAlarmHistory() = launchDemo {
        AnomalySimulator.removeFalseAlarmHistory(db)
        refreshTolerance()
        "Removed the planted alerts."
    }

    private fun simulateSignal(
        signal: AnomalySimulator.Signal,
        zScore: Double = AnomalySimulator.TARGET_Z_SCORE
    ) = launchDemo {
        val gate = AnomalySimulator.toleranceState(db, signal)?.gate ?: FalseAlarmTolerance.BASE_THRESHOLD
        val message = when (val result = AnomalySimulator.simulate(db, signal, zScore)) {
            is AnomalySimulator.Result.Triggered -> {
                result.alert?.let { AlertResponder.onAlertCreated(getApplication(), db, it) }
                "Detector flagged an anomaly (z = %.1f, gate %.2f). The senior is asked.".format(result.zScore, gate)
            }
            AnomalySimulator.Result.AlreadyActive -> "An alert of this type is already open. Answer it first."
            AnomalySimulator.Result.NoBaseline ->
                "No baseline for this time block yet, so there is nothing to compare against."
            AnomalySimulator.Result.NoSenior -> "No senior profile found on this phone."
            is AnomalySimulator.Result.LoggedOnly ->
                if (result.zScore < gate) {
                    "z = %.1f is under this block's raised gate of %.2f, so earlier false alarms taught it to stay quiet. Logged, nobody asked."
                        .format(result.zScore, gate)
                } else {
                    "Deviation found (z = %.1f), judged normal for this hour. Logged, nobody notified."
                        .format(result.zScore)
                }
            AnomalySimulator.Result.SuppressedByNap ->
                "Inside the declared nap window, so detection is suppressed."
        }
        refreshTolerance()
        message
    }

    fun simulateFall() = launchDemo {
        when (FallSimulator.simulate(getApplication(), db)) {
            is FallSimulator.Result.Raised -> "Fall signature confirmed: free fall, impact, then no movement."
            FallSimulator.Result.NotConfirmed -> "The detector did not confirm a fall from that motion."
            FallSimulator.Result.AlreadyActive -> "A fall alert is already open. Answer it first."
            FallSimulator.Result.NoSenior -> "No senior profile found on this phone."
        }
    }

    fun simulateSos() = launchDemo {
        if (AlertResponder.raise(getApplication(), db, "sos", "high") != null) {
            "SOS raised."
        } else {
            "An SOS is already open. Answer it first."
        }
    }

    /**
     * Fabricates nothing: the real [IsolationForestDetector] over the real `Daily_Aggregates`
     * already on this phone, the same call the nightly worker makes. So it only raises when the
     * stored days really contain an unusual one.
     */
    /**
     * Writes one unusual block-day into `Daily_Aggregates` for the block that is open right now,
     * so [runIsolationForest] has something to find. Layer 2 reads stored days, and an ordinary
     * day scores quiet, so without this the button can only ever say "nothing crossed".
     *
     * The day is the "combination" case from `IsolationForestTest`: movement and steps near
     * zero while stillness runs high, each only mildly off so no single signal would cross
     * Layer 1's 2.5 on its own. The row is [AggregateSimulator]'s, scaled to this senior's own
     * baseline.
     *
     * Safe to plant: the nightly worker never rolls up the open block, so a row dated today in
     * the open block can only be this one, and it is replaced when that block really closes.
     */
    fun plantUnusualDay() = launchDemo {
        val (senior, onboarding) = seniorAndOnboarding() ?: return@launchDemo "No senior profile on this phone."
        val (date, block) = openBlock(onboarding)

        val baselines = db.baselineDao().getAllBySeniorOnce(senior.seniorId)
        if (baselines.none { it.timeBlock == block }) return@launchDemo "No baseline for the $block block yet."

        db.dailyAggregateDao().deleteByDateAndTimeBlock(senior.seniorId, date, block)
        db.dailyAggregateDao().insert(
            AggregateSimulator.day(
                seniorId = senior.seniorId,
                date = date,
                block = block,
                baselines = baselines,
                movementFactor = 0.0,
                stepsFactor = 0.15,
                stillnessFactor = 1.38,
                sampleCount = AggregateFeatures.expectedSampleCount(onboarding, block)
                    ?: AggregateSimulator.DEFAULT_SAMPLE_COUNT,
                random = Random(System.currentTimeMillis())
            )
        )
        "Planted an unusual $block day. Now press Run Isolation Forest."
    }

    /** Takes the planted day back out, so it can't colour tomorrow's baseline or a later run. */
    fun removePlantedDay() = launchDemo {
        val (senior, onboarding) = seniorAndOnboarding() ?: return@launchDemo "No senior profile on this phone."
        val (date, block) = openBlock(onboarding)
        db.dailyAggregateDao().deleteByDateAndTimeBlock(senior.seniorId, date, block)
        "Removed the planted $block day."
    }

    private suspend fun seniorAndOnboarding() = db.seniorDao().getOnboardedSenior()?.let { senior ->
        db.seniorOnboardingDao().getBySeniorId(senior.seniorId)?.let { senior to it }
    }

    /** The logical date and name of the block open now, as the nightly worker keys its rows. */
    private fun openBlock(onboarding: com.pup.seenior.database.entities.SeniorOnboarding): Pair<String, String> {
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
            Date(SeedBaselineGenerator.logicalDayMillis(now, onboarding.wakeTime, onboarding.sleepTime))
        )
        val block = SeedBaselineGenerator.resolveTimeBlock(now, onboarding.wakeTime, onboarding.sleepTime)
            .name.lowercase()
        return date to block
    }

    fun runIsolationForest() = launchDemo {
        val senior = db.seniorDao().getOnboardedSenior()
        val onboarding = senior?.let { db.seniorOnboardingDao().getBySeniorId(it.seniorId) }
        if (senior == null || onboarding == null) return@launchDemo "No senior profile on this phone."

        when (val outcome = IsolationForestDetector.run(
            senior.seniorId,
            onboarding,
            db.dailyAggregateDao(),
            db.baselineDao(),
            db.alertDao(),
            db.mlModelMetadataDao()
        )) {
            is IsolationForestDetector.Outcome.Raised -> {
                AlertResponder.onAlertCreated(getApplication(), db, outcome.alert)
                "Layer 2 flagged %s (score %.3f). Alert raised.".format(outcome.alert.timeBlock, outcome.score)
            }
            is IsolationForestDetector.Outcome.Logged ->
                "Flagged (score %.3f), judged quiet for this hour. Logged, nobody notified.".format(outcome.score)
            is IsolationForestDetector.Outcome.NothingFlagged ->
                "Scored ${outcome.scored} block-day(s); none crossed ${IsolationForestDetector.THRESHOLD}."
            is IsolationForestDetector.Outcome.NotEnoughData ->
                "Only ${outcome.usableRows} usable block-days. Needs ${IsolationForestDetector.MIN_TRAINING_ROWS}."
            IsolationForestDetector.Outcome.SuppressedByNap -> "Inside the declared nap window. Suppressed."
            IsolationForestDetector.Outcome.AlreadyActive -> "An ml_flag alert is already open."
        }
    }

    /** One button at a time, so a double tap can't race two injections into the same dedup check. */
    private fun launchDemo(block: suspend () -> String) {
        if (running) return
        running = true
        message = null
        viewModelScope.launch {
            message = try {
                block()
            } finally {
                running = false
            }
        }
    }
}
