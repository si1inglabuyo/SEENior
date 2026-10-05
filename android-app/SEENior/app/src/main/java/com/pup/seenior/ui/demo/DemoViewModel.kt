package com.pup.seenior.ui.demo

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.alerts.AlertResponder
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.detection.AnomalySimulator
import com.pup.seenior.detection.FallSimulator
import com.pup.seenior.detection.IsolationForestDetector
import kotlinx.coroutines.launch

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

    private fun simulateSignal(signal: AnomalySimulator.Signal) = launchDemo {
        when (val result = AnomalySimulator.simulate(db, signal)) {
            is AnomalySimulator.Result.Triggered -> {
                result.alert?.let { AlertResponder.onAlertCreated(getApplication(), db, it) }
                "Detector flagged an anomaly (z = %.1f).".format(result.zScore)
            }
            AnomalySimulator.Result.AlreadyActive -> "An alert of this type is already open. Answer it first."
            AnomalySimulator.Result.NoBaseline ->
                "No baseline for this time block yet, so there is nothing to compare against."
            AnomalySimulator.Result.NoSenior -> "No senior profile found on this phone."
            is AnomalySimulator.Result.LoggedOnly ->
                "Deviation found (z = %.1f), judged normal for this hour. Logged, nobody notified."
                    .format(result.zScore)
            AnomalySimulator.Result.SuppressedByNap ->
                "Inside the declared nap window, so detection is suppressed."
        }
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
