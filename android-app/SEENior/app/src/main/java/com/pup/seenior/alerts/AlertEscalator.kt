package com.pup.seenior.alerts

import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import com.pup.seenior.network.dto.CancelAlertRequest
import com.pup.seenior.network.dto.CreateAlertRequest
import com.pup.seenior.network.dto.UpdateSeverityRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * The family tier of the escalation chain (CLAUDE.md §7), independent of any screen.
 *
 * It lives here rather than inside the wellness prompt because the case the product exists for
 * is the senior *not* answering — and a senior who has collapsed is not looking at the app. The
 * prompt calls this when they tap "I need help"; [EscalationWorker] calls it when nobody answers
 * at all. Both paths must produce the same audit trail and the same cloud row.
 *
 * The two callers race by design — the senior can tap "I need help" in the same second the
 * response window expires, and the alarm fires on the same deadline the on-screen countdown is
 * counting to — and neither may produce a duplicate timeline entry or a second cloud alert.
 */
object AlertEscalator {

    /**
     * Serialises the whole read-post-mark sequence in [escalateToFamily].
     *
     * The `isSynced` check alone was not enough, and this was not theoretical: one SOS swipe
     * produced two cloud alerts 4 ms apart. Both callers read `isSynced == false` before either
     * had finished POSTing, so both posted, and the family was notified twice for one emergency.
     *
     * It only started happening once the deadline moved to setAlarmClock. While the alarm was
     * being deferred ten minutes by Doze it could not possibly land on the same instant as the
     * on-screen countdown; firing on time put it exactly there.
     *
     * Mirrors [AlertResponder.raiseLock], which guards the equivalent check-then-insert.
     */
    private val escalateLock = Mutex()

    /** Marks the point in the audit timeline where the family tier was notified. */
    /** Written once the cloud has accepted that the senior closed this alert. Its absence is
     *  what makes a failed cancel retryable rather than lost. */
    private const val STEP_CANCEL_SYNCED = "cancel_synced"

    private const val STEP_FAMILY = "escalated_family"

    /**
     * Marks the point where the cloud actually accepted the alert.
     *
     * Deliberately distinct from [STEP_FAMILY], which records only that this device decided to
     * escalate. Offline the two can be minutes or hours apart, and the senior's Home tab needs
     * to tell those states apart to say "waiting for signal" rather than "your family knows".
     */
    private const val STEP_DELIVERED = "delivered_family"

    /**
     * Written once the cloud has accepted a raised risk level, carrying the level it accepted.
     *
     * Its absence — or a level that no longer matches the local row — is what makes an upgrade
     * retryable rather than lost, exactly as [STEP_CANCEL_SYNCED] does for a self-cancel.
     */
    private const val STEP_SEVERITY_SYNCED = "severity_synced"

    sealed interface Outcome {
        /** The cloud row exists; the family app can see it. */
        data object Delivered : Outcome
        /** No connectivity. The alert is recorded locally and still needs to go out. */
        data object Offline : Outcome
        /** The backend was reachable but refused or failed. */
        data object Failed : Outcome
    }

    /**
     * How long the senior gets to answer before this fires, by what raised the alert.
     *
     * The single source of truth for both the on-screen countdown and the background watchdog —
     * if these two ever disagreed, an alert could escalate while its own countdown was still
     * visibly running.
     */
    fun windowSecondsFor(triggerType: String): Int = when (triggerType) {
        // A conscious cry for help. Only long enough to catch a pocket press (CLAUDE.md §7).
        "sos" -> 10
        // Layer 0 gets a compressed window (CLAUDE.md §5): a detected fall already carries its
        // own evidence that something happened, so waiting the full ten minutes for an answer
        // that may never come costs the one thing an injured person does not have.
        "fall_pattern" -> 60
        else -> 600
    }

    /**
     * How long an open alert keeps absorbing fresh detections of the same kind instead of letting
     * them raise a new one.
     *
     * This is a different question from [windowSecondsFor] and must not be folded into it. That
     * one asks how long the senior has to answer; this one asks how long two detections should be
     * treated as one event. They only look similar for falls by coincidence.
     */
    fun dedupeSecondsFor(triggerType: String): Int = when (triggerType) {
        // Someone swiping again while help is already on the way is repeating themselves, not
        // reporting a second emergency. The response window is only ten seconds, so without a
        // longer horizon here a frightened senior would file an alert per swipe.
        "sos" -> 600
        // Matches FallDetector's own cooldown. Inside it, one fall cannot be reported twice;
        // past it, the senior has fallen again — which is new information and the family needs
        // to hear it, even though the first alert is still open.
        "fall_pattern" -> 60
        else -> 600
    }

    /** Whether the family tier has already been notified for this alert. */
    fun hasEscalatedToFamily(alert: Alert): Boolean = steps(alert.escalationSteps)
        .let { steps -> (0 until steps.length()).any { steps.optJSONObject(it)?.optString("step") == STEP_FAMILY } }

    /**
     * Records the family escalation and pushes the alert's metadata to the cloud.
     *
     * The status deliberately stays "pending". Per CLAUDE.md §7 a pending alert IS one awaiting
     * the family tier — there is no status meaning "escalated to family" ("escalated_barangay" is
     * the tier after this one), and moving it would drop the alert out of
     * [com.pup.seenior.database.dao.AlertDao.getUnacknowledgedAlerts], which drives the chain.
     */
    suspend fun escalateToFamily(db: SeniorAppDatabase, alertId: Int): Outcome = escalateLock.withLock {
        val alert = db.alertDao().getById(alertId) ?: return Outcome.Failed

        // Carried forward rather than re-read from `alert` each time: appending the delivery
        // step to the stale snapshot below would silently drop the escalation step written here.
        var steps = alert.escalationSteps
        if (!hasEscalatedToFamily(alert)) {
            steps = appendStep(steps, STEP_FAMILY, System.currentTimeMillis())
            db.alertDao().updateEscalationSteps(alert.alertId, steps)
        }

        // Already up. A retry after a network failure must not create a second cloud alert.
        if (alert.isSynced) return Outcome.Delivered

        return try {
            // Re-read immediately before posting. The snapshot at the top of this function can be
            // stale by now in two different ways: the location capture is asynchronous and usually
            // lands after it, and Layer 1 can upgrade the severity in between. On 2026-09-01 the
            // upgrade and this POST happened within the same second, and the family were shown
            // Medium for an alert the phone had already called High.
            val current = db.alertDao().getById(alert.alertId)
            val cloudAlert = SeniorCloudSync(db).withSyncId { seniorSyncId ->
                RetrofitClient.api.postAlert(
                    CreateAlertRequest(
                        seniorSyncId = seniorSyncId,
                        riskLevel = current?.riskLevel ?: alert.riskLevel,
                        triggerType = alert.triggerType,
                        // Captured at alert-trigger time only, as a geohash cell, never
                        // coordinates (CLAUDE.md §11). Null when no fix could be had, which is a
                        // normal outcome.
                        locationClusterId = current?.locationClusterId
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
            Outcome.Offline
        } catch (e: Exception) {
            Outcome.Failed
        }
    }

    /**
     * Tells the cloud the senior answered the prompt themselves, so the alert stops sitting in
     * the family app as pending.
     *
     * Only alerts that reached the cloud have anything to close — one dismissed before it ever
     * escalated has no cloud row, which is why this is a no-op for them rather than an error.
     *
     * Returns true when the cloud is known to agree, so the caller can tell "done" from "try
     * again later". The step it writes on success is the only record that the two databases are
     * in step; without it a phone that was offline at the moment of the cancel would have no way
     * to know it still owed the server an update.
     */
    suspend fun cancelInCloud(db: SeniorAppDatabase, alertId: Int): Boolean {
        val alert = db.alertDao().getById(alertId) ?: return false
        if (!alert.isSynced) return true
        if (hasStep(alert, STEP_CANCEL_SYNCED)) return true

        return try {
            SeniorCloudSync(db).withSyncId { seniorSyncId ->
                RetrofitClient.api.cancelAlert(alert.syncId, CancelAlertRequest(seniorSyncId))
            }
            db.alertDao().updateEscalationSteps(
                alert.alertId,
                appendStep(alert.escalationSteps, STEP_CANCEL_SYNCED, System.currentTimeMillis())
            )
            true
        } catch (e: Exception) {
            // Never rethrown: the senior has already been told they are safe and the local record
            // is correct. The only casualty is the family's view being briefly stale, which the
            // watchdog's next pass repairs.
            false
        }
    }

    /**
     * Retries every self-cancel the cloud was never told about.
     *
     * Called from the watchdog. A senior who dismisses a prompt in a dead spot would otherwise
     * leave that alert open in the family app permanently — the one moment it was possible to
     * send the update having passed and nothing remembering it was owed.
     */
    suspend fun reconcileCancelledAlerts(db: SeniorAppDatabase, seniorId: Int) {
        db.alertDao().getSelfCancelledSyncedAlerts(seniorId)
            .filterNot { hasStep(it, STEP_CANCEL_SYNCED) }
            .forEach { cancelInCloud(db, it.alertId) }
    }

    /**
     * Tells the cloud that an already-sent alert has been re-classified as more serious.
     *
     * Layer 1 re-scores on every sample, so an alert posted as Medium can become High while it is
     * still open. [com.pup.seenior.detection.MedianMadDetector] raises the local row; this is the
     * only thing that carries the change to the copy the family app and the barangay dashboard
     * actually read. Measured missing on 2026-09-01: alert 20 was posted Medium at 10:21 and
     * upgraded to High in the same second, and the cloud still said Medium five hours later.
     *
     * A no-op for an alert that never reached the cloud. [escalateToFamily] reads the level at the
     * moment it posts, so one that has not gone up yet carries the current level when it does.
     *
     * Returns true when the cloud is known to agree, so [reconcileSeverity] can tell "done" from
     * "try again later".
     */
    suspend fun syncSeverity(db: SeniorAppDatabase, alertId: Int): Boolean {
        val alert = db.alertDao().getById(alertId) ?: return false
        if (!alert.isSynced) return true
        if (lastSyncedSeverity(alert) == alert.riskLevel) return true

        return try {
            SeniorCloudSync(db).withSyncId { seniorSyncId ->
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
            // Never rethrown, for the same reason as cancelInCloud: the local record is right and
            // the alert is already being acted on. The only casualty is a stale severity in the
            // family's view, which the watchdog's next pass repairs.
            false
        }
    }

    /**
     * Retries every severity upgrade the cloud was never told about.
     *
     * Called from the watchdog, for the same reason [reconcileCancelledAlerts] is: an upgrade that
     * happened in a dead spot has exactly one chance to be sent, and nothing else remembers it was
     * owed.
     */
    suspend fun reconcileSeverity(db: SeniorAppDatabase, seniorId: Int) {
        db.alertDao().getOpenSyncedAlerts(seniorId).forEach { syncSeverity(db, it.alertId) }
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
     * Appends one entry to the alert's audit timeline (CLAUDE.md §8 `escalation_steps`).
     *
     * [extra] carries any keys beyond step/at — the server's own entries already use free-form
     * keys like `reason`, and the risk level the cloud accepted is the same kind of fact.
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
