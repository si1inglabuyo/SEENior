package com.pup.seenior.alerts

import android.util.Log
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import com.pup.seenior.network.dto.CancelAlertRequest
import com.pup.seenior.network.dto.CreateAlertRequest
import com.pup.seenior.network.dto.UpdateLocationRequest
import com.pup.seenior.network.dto.UpdateSeverityRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

/**
 * The family tier of the escalation chain, independent of any screen.
 *
 * The wellness prompt calls this when the senior taps "I need help", and [EscalationWorker]
 * calls it when nobody answers. Both must produce the same audit trail and cloud row, and
 * they can race, so neither may create a duplicate timeline entry or a second cloud alert.
 */
object AlertEscalator {

    private const val TAG = "AlertEscalator"

    /**
     * Serialises the read-post-mark sequence in [escalateToFamily]. The `isSynced` check alone
     * let one SOS swipe create two cloud alerts 4 ms apart. Mirrors [AlertResponder.raiseLock].
     */
    private val escalateLock = Mutex()

    /** Marks the point in the audit timeline where the family tier was notified. */
    /** Written once the cloud accepted that the senior closed this alert; its absence makes a failed cancel retryable. */
    private const val STEP_CANCEL_SYNCED = "cancel_synced"

    private const val STEP_FAMILY = "escalated_family"

    /**
     * Marks the point where the cloud accepted the alert. Distinct from [STEP_FAMILY], which
     * only records that this device decided to escalate; offline the two can be hours apart.
     */
    private const val STEP_DELIVERED = "delivered_family"

    /** Written once the cloud accepted a raised risk level (carrying that level), so a failed upgrade is retryable. */
    private const val STEP_SEVERITY_SYNCED = "severity_synced"

    /** Written once the cloud accepted this alert's location cell, so a late fix is retryable. */
    private const val STEP_LOCATION_SYNCED = "location_synced"

    sealed interface Outcome {
        /** The cloud row exists; the family app can see it. */
        data object Delivered : Outcome
        /** No connectivity. The alert is recorded locally and still needs to go out. */
        data object Offline : Outcome
        /** The backend was reachable but refused or failed. */
        data object Failed : Outcome
    }

    /**
     * How long the senior gets to answer before this fires, by trigger type. Used by both the
     * on-screen countdown and the background watchdog so they never disagree.
     */
    fun windowSecondsFor(triggerType: String): Int = when (triggerType) {
        // A conscious cry for help. Only long enough to catch a pocket press (spec §7).
        "sos" -> 10
        // Layer 0 gets a compressed window, since a detected fall already shows something happened.
        "fall_pattern" -> 60
        else -> 600
    }

    /**
     * How long an open alert absorbs new detections of the same kind instead of raising
     * another. Different from [windowSecondsFor], which is how long the senior has to answer.
     */
    fun dedupeSecondsFor(triggerType: String): Int = when (triggerType) {
        // Repeated swipes while help is coming are one emergency, not several.
        "sos" -> 600
        // Matches FallDetector's cooldown. Past it, a new fall is new information.
        "fall_pattern" -> 60
        else -> 600
    }

    /** Whether the family tier has already been notified for this alert. */
    fun hasEscalatedToFamily(alert: Alert): Boolean = steps(alert.escalationSteps)
        .let { steps -> (0 until steps.length()).any { steps.optJSONObject(it)?.optString("step") == STEP_FAMILY } }

    /**
     * Records the family escalation and pushes the alert's metadata to the cloud.
     *
     * The status stays "pending", since a pending alert is one awaiting the family tier and
     * changing it would drop it from [com.pup.seenior.database.dao.AlertDao.getUnacknowledgedAlerts],
     * which drives the chain.
     */
    suspend fun escalateToFamily(db: SeniorAppDatabase, alertId: Int): Outcome = escalateLock.withLock {
        val alert = db.alertDao().getById(alertId) ?: return Outcome.Failed

        // The alarm existed to get an answer from the senior; that window has closed. Other
        // alerts' alarms keep sounding.
        AlertAlarm.stop(alertId)

        // Carried forward so appending the delivery step doesn't drop the escalation step.
        var steps = alert.escalationSteps
        if (!hasEscalatedToFamily(alert)) {
            steps = appendStep(steps, STEP_FAMILY, System.currentTimeMillis())
            db.alertDao().updateEscalationSteps(alert.alertId, steps)
        }

        // Already up. A retry after a network failure must not create a second cloud alert.
        if (alert.isSynced) return Outcome.Delivered

        return try {
            // Re-read just before posting, since the severity or location may have changed
            // since the snapshot above.
            val current = db.alertDao().getById(alert.alertId)
            val cloudAlert = SeniorCloudSync(db).withSyncId { seniorSyncId ->
                RetrofitClient.api.postAlert(
                    CreateAlertRequest(
                        seniorSyncId = seniorSyncId,
                        riskLevel = current?.riskLevel ?: alert.riskLevel,
                        triggerType = alert.triggerType,
                        // Geohash cell captured at alert time, never coordinates. Null if no fix.
                        locationClusterId = current?.locationClusterId,
                        // Not re-read: the trigger moment never changes.
                        triggeredAt = Instant.ofEpochMilli(alert.triggeredAt).toString()
                    )
                )
            }
            // Adopt the id the backend minted so both sides can refer to the same alert later.
            db.alertDao().updateSyncId(alert.alertId, cloudAlert.syncId)
            db.alertDao().markSynced(alert.alertId)
            db.alertDao().updateEscalationSteps(
                alert.alertId,
                appendStep(steps, STEP_DELIVERED, System.currentTimeMillis())
            )
            Outcome.Delivered
        } catch (e: IOException) {
            // Expected and common (no signal, server waking up), so logged at a quiet level.
            Log.w(TAG, "escalateToFamily offline for alert ${alert.alertId}", e)
            Outcome.Offline
        } catch (e: Exception) {
            // Anything else is worth a stack trace, since "Failed" alone gives the retry nothing to go on.
            Log.e(TAG, "escalateToFamily failed for alert ${alert.alertId}", e)
            Outcome.Failed
        }
    }

    /**
     * Tells the cloud the senior answered the prompt themselves, so the family app stops
     * showing the alert as pending. A no-op for alerts that never reached the cloud. Returns
     * true when the cloud agrees; the step written on success is the only record that the two
     * databases are in step.
     */
    suspend fun cancelInCloud(db: SeniorAppDatabase, alertId: Int): Boolean {
        val alert = db.alertDao().getById(alertId) ?: return false
        if (!alert.isSynced) return true
        if (hasStep(alert, STEP_CANCEL_SYNCED)) return true

        return try {
            SeniorCloudSync(db).withCachedSyncId { seniorSyncId ->
                RetrofitClient.api.cancelAlert(alert.syncId, CancelAlertRequest(seniorSyncId))
            }
            db.alertDao().updateEscalationSteps(
                alert.alertId,
                appendStep(alert.escalationSteps, STEP_CANCEL_SYNCED, System.currentTimeMillis())
            )
            true
        } catch (e: Exception) {
            // Never rethrown: the local record is correct and the watchdog repairs the family's view.
            false
        }
    }

    /** Retries every self-cancel the cloud was never told about. Called from the watchdog. */
    suspend fun reconcileCancelledAlerts(db: SeniorAppDatabase, seniorId: Int) {
        db.alertDao().getSelfCancelledSyncedAlerts(seniorId)
            .filterNot { hasStep(it, STEP_CANCEL_SYNCED) }
            .forEach { cancelInCloud(db, it.alertId) }
    }

    /**
     * Tells the cloud an already-sent alert was re-classified as more serious. Layer 1
     * re-scores every sample, and [com.pup.seenior.detection.MedianMadDetector] only raises the
     * local row. A no-op for an alert that never reached the cloud, since
     * [escalateToFamily] reads the current level when it posts. Returns true when the cloud agrees.
     */
    suspend fun syncSeverity(db: SeniorAppDatabase, alertId: Int): Boolean {
        val alert = db.alertDao().getById(alertId) ?: return false
        if (!alert.isSynced) return true
        if (lastSyncedSeverity(alert) == alert.riskLevel) return true

        return try {
            SeniorCloudSync(db).withCachedSyncId { seniorSyncId ->
                RetrofitClient.api.updateAlertSeverity(
                    alert.syncId,
                    UpdateSeverityRequest(seniorSyncId, alert.riskLevel)
                )
            }
            db.alertDao().updateEscalationSteps(
                alert.alertId,
                appendStep(
                    alert.escalationSteps,
                    STEP_SEVERITY_SYNCED,
                    System.currentTimeMillis(),
                    mapOf("level" to alert.riskLevel)
                )
            )
            true
        } catch (e: Exception) {
            // Never rethrown, same as cancelInCloud; the watchdog repairs a stale severity.
            false
        }
    }

    /** Retries every severity upgrade the cloud was never told about. Called from the watchdog. */
    suspend fun reconcileSeverity(db: SeniorAppDatabase, seniorId: Int) {
        db.alertDao().getOpenSyncedAlerts(seniorId).forEach { syncSeverity(db, it.alertId) }
    }

    /**
     * Sends a location cell that arrived after its alert had been posted. An SOS goes out at
     * the end of its 10 s cancel window while GPS gets up to 20 s, so a slow fix lands late.
     * A no-op until there is both a cloud row and a cell. Returns true when the cloud has it.
     */
    suspend fun syncLocation(db: SeniorAppDatabase, alertId: Int): Boolean {
        val alert = db.alertDao().getById(alertId) ?: return false
        if (!alert.isSynced) return true
        val cell = alert.locationClusterId ?: return true
        if (hasSyncedLocation(alert)) return true

        return try {
            SeniorCloudSync(db).withCachedSyncId { seniorSyncId ->
                RetrofitClient.api.updateAlertLocation(
                    alert.syncId,
                    UpdateLocationRequest(seniorSyncId, cell)
                )
            }
            db.alertDao().updateEscalationSteps(
                alert.alertId,
                appendStep(alert.escalationSteps, STEP_LOCATION_SYNCED, System.currentTimeMillis())
            )
            true
        } catch (e: Exception) {
            // Swallowed, same as syncSeverity; the watchdog retries a missing pin.
            false
        }
    }

    /** Retries every location cell the cloud was never told about. Called from the watchdog. */
    suspend fun reconcileLocation(db: SeniorAppDatabase, seniorId: Int) {
        db.alertDao().getOpenSyncedAlerts(seniorId).forEach { syncLocation(db, it.alertId) }
    }

    /** Whether the cloud has already confirmed this alert's cell. */
    private fun hasSyncedLocation(alert: Alert): Boolean {
        val arr = steps(alert.escalationSteps)
        return (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .any { it.optString("step") == STEP_LOCATION_SYNCED }
    }

    /** The risk level the cloud last confirmed, or null if it has never confirmed one. */
    private fun lastSyncedSeverity(alert: Alert): String? {
        val arr = steps(alert.escalationSteps)
        return (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .lastOrNull { it.optString("step") == STEP_SEVERITY_SYNCED }
            ?.optString("level")
    }

    private fun hasStep(alert: Alert, step: String): Boolean = steps(alert.escalationSteps)
        .let { arr -> (0 until arr.length()).any { arr.optJSONObject(it)?.optString("step") == step } }

    /** When the cloud accepted this alert, or null if it has not yet. */
    fun deliveredAt(alert: Alert): Long? {
        val arr = steps(alert.escalationSteps)
        return (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .lastOrNull { it.optString("step") == STEP_DELIVERED }
            ?.optString("at")
            ?.toLongOrNull()
    }

    /**
     * Appends one entry to the alert's audit timeline (`escalation_steps`). [extra] carries any
     * keys beyond step/at, like `reason`.
     */
    fun appendStep(
        existing: String,
        step: String,
        at: Long,
        extra: Map<String, String> = emptyMap()
    ): String =
        steps(existing)
            .put(
                JSONObject().put("step", step).put("at", at.toString()).also { entry ->
                    extra.forEach { (key, value) -> entry.put(key, value) }
                }
            )
            .toString()

    private fun steps(raw: String): JSONArray = try {
        JSONArray(raw)
    } catch (e: Exception) {
        JSONArray()
    }
}
