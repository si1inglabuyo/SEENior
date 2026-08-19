package com.pup.seenior.alerts

import android.content.Context
import com.pup.seenior.AppForeground
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.ui.wellness.WellnessMessages
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * What happens the moment an alert is raised on this device, wherever it came from — Median-MAD,
 * Layer 0 fall detection, or the SOS swipe.
 *
 * Every alert has to do two things beyond existing as a row: get in front of the senior, and
 * start its own clock. Keeping both here means a new detection layer only has to say what it
 * found, instead of re-implementing the response chain and getting it subtly wrong.
 */
object AlertResponder {

    /** Serialises check-then-insert so two detections in the same instant cannot both pass the
     *  duplicate check. Falls in particular can be reported by the live sensor stream and a demo
     *  trigger at once. */
    private val raiseLock = Mutex()

    /**
     * Raises an alert that carries no deviation score — one where something either happened or
     * did not, rather than being a matter of degree. Returns null when an alert of the same kind
     * is already working its way through the escalation chain, or when this phone has no
     * onboarded senior.
     *
     * Median-MAD alerts do not come through here: they arrive already scored and are inserted by
     * the detector itself, which then hands them to [onAlertCreated].
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
        // Bounded on purpose — see AlertDao.getRecentActiveAlert. An open alert absorbs repeats of
        // the same event, but must not silence the next real one once that event is over.
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
            // The column belongs to Median-MAD. A fall and a button press are events, not
            // deviations, so there is no z-score to record.
            deviationScore = null,
            triggeredAt = now
        )
        val stored = alert.copy(alertId = db.alertDao().insert(alert).toInt())
        onAlertCreated(context, db, stored)
        stored
    }

    suspend fun onAlertCreated(context: Context, db: SeniorAppDatabase, alert: Alert) {
        EscalationScheduler.arm(context, alert)

        // With the app open the wellness prompt takes over the screen by itself; a notification
        // on top of it would only be noise.
        if (AppForeground.isForeground) return

        val senior = db.seniorDao().getOnboardedSenior()
        val language = senior
            ?.let { db.seniorOnboardingDao().getBySeniorId(it.seniorId)?.languagePreference }
            ?: WellnessMessages.ENGLISH

        AlertNotifier.notify(context, alert, language, senior?.firstName.orEmpty())
    }
}
