package com.pup.seenior.alerts

import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import com.pup.seenior.network.dto.CreateAlertRequest
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
    private const val STEP_FAMILY = "escalated_family"

    /**
     * Marks the point where the cloud actually accepted the alert.
     *
     * Deliberately distinct from [STEP_FAMILY], which records only that this device decided to
     * escalate. Offline the two can be minutes or hours apart, and the senior's Home tab needs
     * to tell those states apart to say "waiting for signal" rather than "your family knows".
     */
    private const val STEP_DELIVERED = "delivered_family"

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
            val cloudAlert = SeniorCloudSync(db).withSyncId { seniorSyncId ->
                RetrofitClient.api.postAlert(
                    CreateAlertRequest(
                        seniorSyncId = seniorSyncId,
                        riskLevel = alert.riskLevel,
                        triggerType = alert.triggerType,
                        // Location is captured at alert-trigger time only, as an anonymous
                        // cluster id, never coordinates (CLAUDE.md §11). No cluster engine
                        // exists yet, so this stays null rather than sending a fake.
                        locationClusterId = null
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

    /** When the cloud accepted this alert, or null if it has not yet. */
    fun deliveredAt(alert: Alert): Long? {
        val arr = steps(alert.escalationSteps)
        return (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .lastOrNull { it.optString("step") == STEP_DELIVERED }
            ?.optString("at")
            ?.toLongOrNull()
    }

    /** Appends one entry to the alert's audit timeline (CLAUDE.md §8 `escalation_steps`). */
    fun appendStep(existing: String, step: String, at: Long): String =
        steps(existing)
            .put(JSONObject().put("step", step).put("at", at.toString()))
            .toString()

    private fun steps(raw: String): JSONArray = try {
        JSONArray(raw)
    } catch (e: Exception) {
        JSONArray()
    }
}
