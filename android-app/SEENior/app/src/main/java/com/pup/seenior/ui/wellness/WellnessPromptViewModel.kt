package com.pup.seenior.ui.wellness

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.alerts.AlertAlarm
import com.pup.seenior.alerts.AlertEscalator
import com.pup.seenior.alerts.AlertNotifier
import com.pup.seenior.alerts.EscalationScheduler
import com.pup.seenior.alerts.EscalationWorker
import com.pup.seenior.alerts.OutcomeReasons
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.ui.alerts.AlertFigures
import com.pup.seenior.ui.routine.SeniorRoutineModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** OUTCOME is the optional "what happened" question, shown only after the senior has already closed the alert. */
enum class PromptStage { PROMPT, ACKNOWLEDGED, SENT, OUTCOME }

/**
 * Drives one wellness check from the moment an alert appears until the senior is done.
 *
 * Three ways out: "I'm safe" closes the alert locally; "I need help" escalates immediately;
 * letting the timer run out escalates too. The escalation itself is in [AlertEscalator],
 * because [EscalationScheduler] does the same job when the app is never opened.
 */
class WellnessPromptViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SeniorAppDatabase.getInstance(application)

    var stage by mutableStateOf(PromptStage.PROMPT)
        private set
    var secondsRemaining by mutableStateOf(0)
        private set
    var isSending by mutableStateOf(false)
        private set

    /** True while the cloud push is in flight. The senior is already on the "Alert Sent" screen; see [escalate]. */
    var isDelivering by mutableStateOf(false)
        private set

    /** Set when the alert was recorded locally but couldn't be pushed to the cloud. The senior
     *  is still told help is coming, but we don't claim the family was reached. */
    var deliveryWarning by mutableStateOf<String?>(null)
        private set

    /**
     * For a daily-pattern alert: the signals of the flagged day that leaned the worrying way, for the
     * "Why are we asking?" card. Empty for every other alert, and until the lookup finishes.
     */
    internal var dailyItems by mutableStateOf<List<AlertFigures.Item>>(emptyList())
        private set

    /** True once the senior has closed an alert that had already gone out, so the confirmation can say so. */
    var stoodDown by mutableStateOf(false)
        private set

    private fun loadDailyItems(alert: Alert) {
        dailyItems = emptyList()
        if (alert.triggerType != "ml_flag") return
        viewModelScope.launch {
            val day = db.dailyAggregateDao().getMostRecentBefore(alert.seniorId, alert.timeBlock, alert.triggeredAt) ?: return@launch
            val baseline = db.baselineDao().getAllBySeniorOnce(alert.seniorId)
            dailyItems = AlertFigures.dailyItems(
                SeniorRoutineModel.usual(baseline, alert.timeBlock),
                SeniorRoutineModel.figuresOf(day)
            )
        }
    }

    private var alert: Alert? = null

    /** Starts the response window. [onFinished] fires once the senior is done, however it ended. */
    fun begin(alert: Alert, onFinished: () -> Unit) {
        // Already closed, but the senior never answered "what happened" (the app was closed first,
        // or they closed it from the Home card). Go straight to the question: no countdown, nothing
        // to escalate. Checked before the guard below because this view model is reused, so its
        // stage can still be left over from the same alert's earlier prompt.
        if (AlertEscalator.awaitingOutcome(alert)) {
            if (this.alert?.alertId != alert.alertId || stage != PromptStage.OUTCOME) {
                this.alert = alert
                deliveryWarning = null
                AlertAlarm.stop(alert.alertId)
                stage = PromptStage.OUTCOME
            }
            return
        }

        if (this.alert?.alertId == alert.alertId && stage != PromptStage.PROMPT) return
        this.alert = alert
        stage = PromptStage.PROMPT
        deliveryWarning = null
        loadDailyItems(alert)

        // They are looking at the alert now, so the notification has done its job.
        AlertNotifier.cancel(getApplication(), alert.alertId)

        // The watchdog may already have escalated this. Don't restart the countdown or push a second copy.
        if (AlertEscalator.hasEscalatedToFamily(alert)) {
            stage = PromptStage.SENT
            // Escalated is not delivered. The audit step is written before the cloud accepts the
            // alert, so this screen must not say contacts were notified while it is still queued.
            if (!alert.isSynced) {
                deliveryWarning =
                    "You are offline. Your family will be notified once this phone reconnects."
            }
            viewModelScope.launch { countdownToClose(onFinished) }
            return
        }

        viewModelScope.launch {
            // Anchored to when the alert was raised, and re-derived from the clock each tick, not
            // counted down: Doze can freeze the app and the display would fall behind the alarm.
            // This keeps it in agreement with EscalationScheduler.
            val windowSeconds = AlertEscalator.windowSecondsFor(alert.triggerType, alert.fallTolerance).toLong()
            while (stage == PromptStage.PROMPT) {
                val remaining = windowSeconds - (System.currentTimeMillis() - alert.triggeredAt) / 1000
                secondsRemaining = remaining.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                if (remaining <= 0) break
                delay(1000)
            }
            // No answer within the window: escalate. This is the branch the whole product is for.
            if (stage == PromptStage.PROMPT) escalate(onFinished)
        }
    }

    /** "I'm safe" / SOS "Cancel" — closes the alert without notifying anyone. */
    fun markSafe(onFinished: () -> Unit) {
        val current = alert ?: return
        // Stopped first, outside the coroutine, so the noise for this alert ends on the button
        // press. The id keeps a different open alert from being silenced.
        AlertAlarm.stop(current.alertId)
        if (stage != PromptStage.PROMPT) return
        stage = PromptStage.ACKNOWLEDGED

        // Nothing is owed on this alert, so stop the watchdog.
        EscalationScheduler.cancel(getApplication(), current.alertId)

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.alertDao().updateEscalationSteps(
                current.alertId,
                AlertEscalator.selfCancelledSteps(current.escalationSteps, current.triggerType, now)
            )
            db.alertDao().updateStatus(current.alertId, "self_cancelled", now)

            // Tell the cloud, or the alert stays pending in the family app. Swallows its own
            // failure and the watchdog retries; the acknowledgement isn't held up by the network.
            AlertEscalator.cancelInCloud(db, current.alertId)

            // A swiped-then-cancelled SOS is recorded without asking anything, so the team can
            // count accidental swipes. How fast it was cancelled goes with it.
            if (current.triggerType == "sos") {
                db.alertDao().updateOutcomeReason(current.alertId, OutcomeReasons.SOS_CANCELLED)
                AlertEscalator.syncOutcome(db, current.alertId)
            }

            delay(ACKNOWLEDGED_DISPLAY_MILLIS)
            askOutcomeOrFinish(current, onFinished)
        }
    }

    /**
     * "I'm fine now": closes an alert that has already reached the family. [markSafe] can't be
     * used because it only works while the prompt is unanswered. The cloud call matters most
     * here, since a relative could otherwise be looking at an emergency the senior called off.
     */
    fun standDown(onFinished: () -> Unit) {
        val current = alert ?: return
        // Stopped first, outside the coroutine. See markSafe.
        AlertAlarm.stop(current.alertId)
        if (stage != PromptStage.SENT) return
        stage = PromptStage.ACKNOWLEDGED

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.alertDao().updateEscalationSteps(
                current.alertId,
                AlertEscalator.selfCancelledSteps(current.escalationSteps, current.triggerType, now)
            )
            db.alertDao().updateStatus(current.alertId, "self_cancelled", now)
            // Nothing is owed on this alert any more — including any queued delivery retry.
            EscalationScheduler.cancel(getApplication(), current.alertId)
            AlertEscalator.cancelInCloud(db, current.alertId)

            stoodDown = true
            delay(ACKNOWLEDGED_DISPLAY_MILLIS)
            askOutcomeOrFinish(current, onFinished)
        }
    }

    /**
     * The alert is already closed. For an anomaly or a fall, ask what happened; everything else
     * (SOS) just closes. The question cannot be skipped and has no timer: it stays until the
     * senior chooses, and [AlertEscalator.STEP_OUTCOME_PENDING] (written when the alert was closed)
     * brings it back if the app is closed first. Nothing here can reopen, delay or escalate the
     * alert: the answer is only a record.
     */
    private fun askOutcomeOrFinish(current: Alert, onFinished: () -> Unit) {
        if (OutcomeReasons.isAsked(current.triggerType)) stage = PromptStage.OUTCOME else onFinished()
    }

    /** One tap on the "what happened" question. Stored locally; sent to the cloud only if the alert is already there. */
    fun answerOutcome(code: String, onFinished: () -> Unit, typedReason: String? = null) {
        val current = alert ?: return
        if (stage != PromptStage.OUTCOME) return
        // A stray value is ignored and the question stays up.
        if (!OutcomeReasons.isValid(current.triggerType, code)) return
        stage = PromptStage.ACKNOWLEDGED // shows the thank-you tick and blocks a second tap
        viewModelScope.launch {
            db.alertDao().updateOutcome(current.alertId, code, OutcomeReasons.cleanDetail(code, typedReason))
            AlertEscalator.syncOutcome(db, current.alertId)
            delay(OUTCOME_THANKS_MILLIS)
            onFinished()
        }
    }

    /** "I need help", or the response window expiring. */
    fun escalate(onFinished: () -> Unit) {
        val current = alert ?: return
        // Stopped first. This is also the timeout path, so the alarm must stop whether the
        // senior pressed something or not. See markSafe.
        AlertAlarm.stop(current.alertId)
        if (isSending) return
        isSending = true

        EscalationScheduler.cancel(getApplication(), current.alertId)

        viewModelScope.launch {
            // Confirm to the senior before the network call: the alert is recorded locally and
            // the free-tier backend can take a minute to wake. The sent screen shows
            // "notifying..." until delivery resolves.
            stage = PromptStage.SENT
            isDelivering = true

            val outcome = AlertEscalator.escalateToFamily(db, current.alertId)

            // Retry delivery on failure so "notified once reconnected" is true. Enqueued for
            // Failed as well as Offline, since the server may accept a retry. Can't duplicate,
            // because escalateToFamily short-circuits on isSynced.
            if (outcome != AlertEscalator.Outcome.Delivered) {
                EscalationWorker.enqueueRetry(getApplication(), current.alertId)
            }

            deliveryWarning = when (outcome) {
                AlertEscalator.Outcome.Delivered -> null
                AlertEscalator.Outcome.Offline ->
                    "You are offline. Your family will be notified once this phone reconnects."
                AlertEscalator.Outcome.Failed ->
                    "We could not reach your family's app. Please call them directly if you can."
            }

            isSending = false
            isDelivering = false
            countdownToClose(onFinished)
        }
    }

    /** The close countdown starts once delivery resolves, so the outcome stays on screen long enough to read. */
    private suspend fun countdownToClose(onFinished: () -> Unit) {
        secondsRemaining = SENT_DISPLAY_SECONDS
        while (secondsRemaining > 0 && stage == PromptStage.SENT) {
            delay(1000)
            secondsRemaining--
        }
        // standDown moves the stage on and owns the close; this guard stops onFinished firing twice.
        if (stage == PromptStage.SENT) onFinished()
    }

    private companion object {
        const val SENT_DISPLAY_SECONDS = 5
        const val ACKNOWLEDGED_DISPLAY_MILLIS = 1800L

        const val OUTCOME_THANKS_MILLIS = 900L
    }
}
