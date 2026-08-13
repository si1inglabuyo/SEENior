package com.pup.seenior.ui.wellness

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import com.pup.seenior.network.dto.CreateAlertRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

enum class PromptStage { PROMPT, ACKNOWLEDGED, SENT }

/**
 * Drives one wellness check from the moment an alert appears to the moment the senior is done
 * with it — the human-in-the-loop step of CLAUDE.md §7's escalation chain.
 *
 * Three ways out, and they are not symmetrical:
 * - "I'm safe" closes the alert locally and nothing ever leaves the phone.
 * - "I need help" escalates immediately.
 * - Letting the timer run out escalates too. Silence is the case the whole system exists for.
 */
class WellnessPromptViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SeniorAppDatabase.getInstance(application)
    private val cloudSync = SeniorCloudSync(db)

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
    private var isSos = false

    /**
     * Starts the response window. [onFinished] fires once the senior is done and the screen
     * should close, whichever way it ended.
     */
    fun begin(alert: Alert, onFinished: () -> Unit) {
        if (this.alert?.alertId == alert.alertId && stage != PromptStage.PROMPT) return
        this.alert = alert
        this.isSos = alert.triggerType == "sos"
        stage = PromptStage.PROMPT
        deliveryWarning = null

        viewModelScope.launch {
            // SOS is a conscious cry for help, so the window is only long enough to catch a
            // pocket-press. A detected anomaly gets a real chance to be answered first
            // (CLAUDE.md §7 — the senior always gets first refusal on a false alarm).
            secondsRemaining = if (isSos) SOS_CANCEL_WINDOW_SECONDS else RESPONSE_WINDOW_SECONDS
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
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.alertDao().updateEscalationSteps(
                current.alertId,
                appendStep(current.escalationSteps, "self_cancelled", now)
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

        viewModelScope.launch {
            val now = System.currentTimeMillis()

            // Status deliberately stays "pending". Per CLAUDE.md §7 a pending alert IS one
            // awaiting the family tier, and there is no status meaning "escalated to family"
            // — "escalated_barangay" is the tier after this one. Moving it would also drop the
            // alert out of AlertDao.getUnacknowledgedAlerts, which the escalation chain reads.
            db.alertDao().updateEscalationSteps(
                current.alertId,
                appendStep(current.escalationSteps, "escalated_family", now)
            )

            // Confirm to the senior BEFORE the network call, not after. The alert is already
            // recorded on this device, and the backend is a free-tier instance that can take
            // most of a minute to wake — leaving a frightened person staring at an unchanged
            // screen, unsure whether their request registered, is the wrong failure mode. The
            // sent screen shows a "notifying..." line until delivery actually resolves.
            stage = PromptStage.SENT
            isDelivering = true

            try {
                val cloudAlert = cloudSync.withSyncId { seniorSyncId ->
                    RetrofitClient.api.postAlert(
                        CreateAlertRequest(
                            seniorSyncId = seniorSyncId,
                            riskLevel = current.riskLevel,
                            triggerType = current.triggerType,
                            // Location is captured at alert-trigger time only and stored as an
                            // anonymous cluster id, never coordinates (CLAUDE.md §11). No cluster
                            // engine exists yet, so this stays null rather than sending a fake.
                            locationClusterId = null
                        )
                    )
                }
                // Adopt the backend's id so both sides can refer to the same alert afterwards.
                db.alertDao().updateSyncId(current.alertId, cloudAlert.syncId)
                db.alertDao().markSynced(current.alertId)
            } catch (e: IOException) {
                deliveryWarning = "You are offline. Your family will be notified once this phone reconnects."
            } catch (e: Exception) {
                deliveryWarning = "We could not reach your family's app. Please call them directly if you can."
            }

            isSending = false
            isDelivering = false

            // The countdown to close only starts once delivery has resolved, so the outcome —
            // including a failure warning — is on screen long enough to be read.
            var remaining = SENT_DISPLAY_SECONDS
            secondsRemaining = remaining
            while (remaining > 0) {
                delay(1000)
                remaining--
                secondsRemaining = remaining
            }
            onFinished()
        }
    }

    /** Appends one entry to the alert's audit timeline (CLAUDE.md §8 `escalation_steps`). */
    private fun appendStep(existing: String, step: String, at: Long): String {
        val steps = try {
            JSONArray(existing)
        } catch (e: Exception) {
            JSONArray()
        }
        steps.put(
            JSONObject()
                .put("step", step)
                .put("at", at.toString())
        )
        return steps.toString()
    }

    companion object {
        /** How long the senior has to answer before the family tier is notified. */
        const val RESPONSE_WINDOW_SECONDS = 600

        /** SOS only pauses long enough to catch an accidental press (CLAUDE.md §7). */
        const val SOS_CANCEL_WINDOW_SECONDS = 10

        private const val SENT_DISPLAY_SECONDS = 5
        private const val ACKNOWLEDGED_DISPLAY_MILLIS = 1800L
    }
}
