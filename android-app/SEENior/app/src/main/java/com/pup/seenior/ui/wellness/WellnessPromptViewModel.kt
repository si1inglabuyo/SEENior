package com.pup.seenior.ui.wellness

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.alerts.AlertEscalator
import com.pup.seenior.alerts.AlertNotifier
import com.pup.seenior.alerts.EscalationScheduler
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
 * the only thing that can escalate: [EscalationScheduler] does the same job when the senior never
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
            // Escalated is not delivered. The audit step is written before the cloud accepts the
            // alert, so on its own it would have this screen tell the senior their contacts have
            // been notified while the alert is still sitting on this phone waiting for a network.
            // That is the one claim this screen must never make falsely.
            if (!alert.isSynced) {
                deliveryWarning =
                    "You are offline. Your family will be notified once this phone reconnects."
            }
            viewModelScope.launch { countdownToClose(onFinished) }
            return
        }

        viewModelScope.launch {
            // Anchored to when the alert was raised, not to when this screen opened — and
            // re-derived from the clock on every tick rather than counted down. A decremented
            // counter is only honest while the process keeps running: Doze freezes the app, the
            // ticks stop, the deadline does not, and the screen comes back showing a countdown
            // minutes behind the alarm it is supposed to mirror. Reading the clock each time
            // makes the display agree with EscalationScheduler by construction.
            val windowSeconds = AlertEscalator.windowSecondsFor(alert.triggerType).toLong()
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
        if (stage != PromptStage.PROMPT) return
        stage = PromptStage.ACKNOWLEDGED

        // Nothing is owed on this alert any more, so stop the watchdog before it spends a
        // wake-up discovering that for itself.
        EscalationScheduler.cancel(getApplication(), current.alertId)

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

        EscalationScheduler.cancel(getApplication(), current.alertId)

        viewModelScope.launch {
            // Confirm to the senior BEFORE the network call, not after. The alert is already
            // recorded on this device, and the backend is a free-tier instance that can take
            // most of a minute to wake — leaving a frightened person staring at an unchanged
            // screen, unsure whether their request registered, is the wrong failure mode. The
            // sent screen shows a "notifying..." line until delivery actually resolves.
            stage = PromptStage.SENT
            isDelivering = true

            val outcome = AlertEscalator.escalateToFamily(db, current.alertId)

            // Make the promise on the next screen true. Until now nothing did: this method
            // cancels the deadline alarm before it posts, and EscalationWorker.enqueueRetry was
            // only ever called from EscalationReceiver — so a senior who tapped "I need help"
            // with no signal was told "your family will be notified once this phone reconnects"
            // while the alert sat pending and unsynced forever, with nothing left to retry it.
            // On the one path where they consciously asked for help.
            //
            // Enqueued for Failed as well as Offline: a backend that refused once may well
            // accept the retry, and the worker already treats both as retryable. The alert is
            // recorded locally either way, so a retry can only add delivery, never a duplicate
            // — escalateToFamily short-circuits on isSynced.
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
