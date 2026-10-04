package com.pup.seenior.alerts

import android.content.Context
import android.content.Intent
import com.pup.seenior.AppForeground
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.MainActivity
import com.pup.seenior.location.AlertLocationCapture
import com.pup.seenior.ui.wellness.WellnessMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * What happens when an alert is raised on this device, from any source (Median-MAD, fall
 * detection or SOS): it gets in front of the senior and starts its own clock. Keeping both
 * here means a new detection layer only has to say what it found.
 */
object AlertResponder {

    /** Serialises check-then-insert so two detections at once can't both pass the duplicate check. */
    private val raiseLock = Mutex()

    /** Outlives the caller on purpose: see [captureLocationCluster]. */
    private val locationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Raises an alert with no deviation score (something happened or didn't). Returns null if
     * an alert of the same kind is already in the escalation chain, or the phone has no
     * onboarded senior. Median-MAD alerts don't come through here; the detector inserts them
     * and hands them to [onAlertCreated].
     */
    suspend fun raise(
        context: Context,
        db: SeniorAppDatabase,
        triggerType: String,
        riskLevel: String
    ): Alert? = raiseLock.withLock {
        val senior = db.seniorDao().getOnboardedSenior() ?: return null
        val onboarding = db.seniorOnboardingDao().getBySeniorId(senior.seniorId) ?: return null

        val now = System.currentTimeMillis()
        // Bounded (see AlertDao.getRecentActiveAlert): an open alert absorbs repeats of the
        // same event but mustn't silence the next real one.
        val notBefore = now - AlertEscalator.dedupeSecondsFor(triggerType) * 1_000L
        if (db.alertDao().getRecentActiveAlert(senior.seniorId, triggerType, notBefore) != null) return null

        val alert = Alert(
            seniorId = senior.seniorId,
            syncId = UUID.randomUUID().toString(),
            triggerType = triggerType,
            riskLevel = riskLevel,
            timeBlock = SeedBaselineGenerator
                .resolveTimeBlock(now, onboarding.wakeTime, onboarding.sleepTime)
                .name.lowercase(),
            // That column belongs to Median-MAD; a fall or button press has no z-score.
            deviationScore = null,
            triggeredAt = now
        )
        val stored = alert.copy(alertId = db.alertDao().insert(alert).toInt())
        onAlertCreated(context, db, stored)
        stored
    }

    suspend fun onAlertCreated(context: Context, db: SeniorAppDatabase, alert: Alert) {
        // Armed first: the deadline is the alert's hard guarantee and nothing added later may delay it.
        EscalationScheduler.arm(context, alert)
        captureLocationCluster(context, db, alert)

        // After the deadline is armed. Low-risk anomalies never reach this function, so
        // anything here is owed an answer and may make noise asking for one.
        AlertAlarm.start(context, alert.alertId)

        // With the app open the wellness prompt takes over by itself; a notification would be noise.
        if (AppForeground.isForeground) return

        // Two mechanisms: the full-screen intent on the notification covers a locked or dark
        // screen, and this covers a senior inside another app, where a full-screen intent
        // degrades to a banner. Both need grants Android won't give from the manifest alone
        // (see [AlertPermissions]). Best effort: if the launch is refused the notification
        // still posts.
        if (AlertPermissions.canDrawOverlays(context)) {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }

        val senior = db.seniorDao().getOnboardedSenior()
        val language = senior
            ?.let { db.seniorOnboardingDao().getBySeniorId(it.seniorId)?.languagePreference }
            ?: WellnessMessages.ENGLISH

        AlertNotifier.notify(context, alert, language, senior?.firstName.orEmpty())
    }

    /**
     * Asks where the phone is and stores the result as this alert's location cell. Launched
     * rather than awaited, since a fix can take 20 s. The fix can arrive after the alert was
     * sent (an SOS posts at the end of its 10 s cancel window), which is why
     * [AlertEscalator.syncLocation] is called here and not left to the watchdog. Best effort:
     * a missing cell costs a precise map, not an alert, and the family's map falls back to
     * the registered address.
     */
    private fun captureLocationCluster(context: Context, db: SeniorAppDatabase, alert: Alert) {
        val appContext = context.applicationContext
        locationScope.launch {
            val cell = runCatching {
                AlertLocationCapture.capture(appContext, locationTimeoutMsFor(alert.triggerType))
            }.getOrNull() ?: return@launch
            db.alertDao().updateLocationCluster(alert.alertId, cell)
            AlertEscalator.syncLocation(db, alert.alertId)
        }
    }

    /**
     * How long this alert can wait for a fix, by trigger. An SOS or fall notifies everyone in
     * seconds, so a late cell would miss the message. A Median-MAD alert has ten minutes
     * before the family tier starts, and the first real one went out with no location because
     * 20 s wasn't enough for a cold GPS indoors. Still best effort; nothing waits on this.
     */
    private fun locationTimeoutMsFor(triggerType: String): Long = when (triggerType) {
        "sos", "fall_pattern" -> 20_000L
        else -> 120_000L
    }
}
