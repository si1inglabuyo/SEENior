package com.pup.seenior.alerts

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import java.util.concurrent.TimeUnit

/**
 * The response window, running outside the app.
 *
 * Without this, silence only escalates while the senior happens to be looking at the wellness
 * prompt — the countdown lives in a ViewModel, so backgrounding the app or letting Android kill
 * the process would quietly abandon an open alert. That is precisely backwards: an unanswered
 * alert is *more* likely to matter when the phone has been left untouched.
 *
 * Scheduled for every alert this device raises, deliberately including SOS. Cancelled the moment
 * the senior says they are safe.
 */
class EscalationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val alertId = inputData.getInt(KEY_ALERT_ID, -1)
        if (alertId <= 0) return Result.success()

        val db = SeniorAppDatabase.getInstance(applicationContext)
        val alert = db.alertDao().getById(alertId) ?: return Result.success()

        // The senior answered, or the chain has already moved on without us.
        if (alert.status != "pending") return Result.success()

        return when (AlertEscalator.escalateToFamily(db, alertId)) {
            AlertEscalator.Outcome.Delivered -> Result.success()
            // Recorded locally, not delivered. Retry with WorkManager's backoff rather than
            // dropping it — SMS fallback (CLAUDE.md §7) is not built yet, so this push is
            // currently the only way the family ever hears about it.
            AlertEscalator.Outcome.Offline -> Result.retry()
            AlertEscalator.Outcome.Failed -> Result.retry()
        }
    }

    companion object {
        private const val KEY_ALERT_ID = "alert_id"

        private fun workName(alertId: Int) = "escalation_$alertId"

        /**
         * Arms the window for [alert], anchored to when it was raised rather than to now, so a
         * device that was asleep or rebooting does not hand the senior a fresh countdown.
         */
        fun schedule(context: Context, alert: Alert) {
            val windowMillis = TimeUnit.SECONDS.toMillis(
                AlertEscalator.windowSecondsFor(alert.triggerType).toLong()
            )
            val remaining = (alert.triggeredAt + windowMillis - System.currentTimeMillis())
                .coerceAtLeast(0)

            val request = OneTimeWorkRequestBuilder<EscalationWorker>()
                .setInputData(workDataOf(KEY_ALERT_ID to alert.alertId))
                .setInitialDelay(remaining, TimeUnit.MILLISECONDS)
                // No network constraint on purpose: the audit entry must be written on time even
                // with no signal, and the worker retries the delivery itself.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(alert.alertId),
                // KEEP, not REPLACE: re-scheduling an already-armed alert must never restart
                // its countdown.
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /** Called when the senior self-cancels, so no wake-up is spent on a closed alert. */
        fun cancel(context: Context, alertId: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(alertId))
        }
    }
}
