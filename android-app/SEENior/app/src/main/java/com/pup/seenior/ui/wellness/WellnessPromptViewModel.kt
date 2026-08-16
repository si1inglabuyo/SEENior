package com.pup.seenior.ui.wellness

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.alerts.AlertEscalator
import com.pup.seenior.alerts.AlertNotifier
import com.pup.seenior.alerts.EscalationWorker
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PromptStage { PROMPT, ACKNOWLEDGED, SENT }

/**
 * Drives one wellness check from the moment an alert appears to the moment the senior is done
 * with it — the human-in-the-loop step of CLAUDE.md §7's escalation chain.
 *
 * Three ways out, and they are not symmetrical:
 * - "I'm safe" closes the alert locally and nothing ever leaves the phone.
 * - "I need help" escalates immediately.
 * - Letting the timer run out escalates too. Silence is the case the whole system exists for.
 *
 * The escalation itself lives in [AlertEscalator] rather than here, because this screen is not
 * the only thing that can escalate: [EscalationWorker] does the same job when the senior never
 * opens the app at all.
 */
class WellnessPromptViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SeniorAppDatabase.getInstance(application)

    var stage by mutableStateOf(PromptStage.PROMPT)
        private set
    var secondsRemaining by mutableStateOf(0)
        private set
    var isSending by mutableStateOf(false)
        private set

    /** True while the cloud push is still in flight. The senior is already on the "Alert Sent"
     *  screen by then — see [escalate] for why the screen does not wait for the network. */
    var isDelivering by mutableStateOf(false)
        private set

    /** Set when the alert was recorded locally but could not be pushed to the cloud. The senior
     *  is still told help is coming — the alert is real and stored — but we do not claim the
     *  family was reached when they were not. SMS fallback (CLAUDE.md §7) would cover this case
     *  and is not built yet. */
    var deliveryWarning by mutableStateOf<String?>(null)
        private set

    private var alert: Alert? = null

    /**
     * Starts the response window. [onFinished] fires once the senior is done and the screen
     * should close, whichever way it ended.
     */
    fun begin(alert: Alert, onFinished: () -> Unit) {
        if (this.alert?.alertId == alert.alertId && stage != PromptStage.PROMPT) return
        this.alert = alert
        stage = PromptStage.PROMPT
        deliveryWarning = null

        // They are looking at the alert now, so the notification that brought them here has
        // done its job.
        AlertNotifier.cancel(getApplication(), alert.alertId)

        // The background watchdog may have already escalated this while the phone lay
        // untouched. Re-running the countdown would ask for an answer that can no longer change
        // anything, and re-escalating would push a second copy of the same alert.
        if (AlertEscalator.hasEscalatedToFamily(alert)) {
            stage = PromptStage.SENT
            viewModelScope.launch { countdownToClose(onFinished) }
            return
        }

        viewModelScope.launch {
            // Anchored to when the alert was raised, not to when this screen opened. An alert
            // that has been waiting eight minutes has two minutes left, however many times the
            // app was reopened in between — and the watchdog is counting from the same instant.
            val elapsedSeconds = (System.currentTimeMillis() - alert.triggeredAt) / 1000
            secondsRemaining = (AlertEscalator.windowSecondsFor(alert.triggerType) - elapsedSeconds)
                .coerceIn(0, Int.MAX_VALUE.toLong())
                .toInt()

            while (secondsRemaining > 0 && stage == PromptStage.PROMPT) {
                delay(1000)
                secondsRemaining--
            }
            // No answer within the window: escalate. This is the branch the whole product is for.
            if (stage == PromptStage.PROMPT) escalate(onFinished)
        }
    }

    /** "I'm safe" / SOS "Cancel" — closes the alert without notifying anyone. */
    fun markSafe(onFinished: () -> Unit) {
        val current = alert ?: return
        if (stage != PromptStage.PROMPT) return
        stage = PromptStage.ACKNOWLEDGED

        // Nothing is owed on this alert any more, so stop the watchdog before it spends a
        // wake-up discovering that for itself.
        EscalationWorker.cancel(getApplication(), current.alertId)

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.alertDao().updateEscalationSteps(
                current.alertId,
                AlertEscalator.appendStep(current.escalationSteps, "self_cancelled", now)
            )
            db.alertDao().updateStatus(current.alertId, "self_cancelled", now)
            delay(ACKNOWLEDGED_DISPLAY_MILLIS)
            onFinished()
        }
    }

    /** "I need help", or the response window expiring. */
    fun escalate(onFinished: () -> Unit) {
        val current = alert ?: return
        if (isSending) return
        isSending = true

        EscalationWorker.cancel(getApplication(), current.alertId)

        viewModelScope.launch {
            // Confirm to the senior BEFORE the network call, not after. The alert is already
            // recorded on this device, and the backend is a free-tier instance that can take
            // most of a minute to wake — leaving a frightened person staring at an unchanged
            // screen, unsure whether their request registered, is the wrong failure mode. The
            // sent screen shows a "notifying..." line until delivery actually resolves.
            stage = PromptStage.SENT
            isDelivering = true

            deliveryWarning = when (AlertEscalator.escalateToFamily(db, current.alertId)) {
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

    /** The countdown to close only starts once delivery has resolved, so the outcome — including
     *  a failure warning — is on screen long enough to be read. */
    private suspend fun countdownToClose(onFinished: () -> Unit) {
        secondsRemaining = SENT_DISPLAY_SECONDS
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
        onFinished()
    }

    private companion object {
        const val SENT_DISPLAY_SECONDS = 5
        const val ACKNOWLEDGED_DISPLAY_MILLIS = 1800L
    }
}
