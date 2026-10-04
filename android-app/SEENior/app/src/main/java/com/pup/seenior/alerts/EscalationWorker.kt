package com.pup.seenior.alerts

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pup.seenior.database.SeniorAppDatabase
import java.util.concurrent.TimeUnit

/**
 * Retries an escalation whose delivery failed, after [EscalationScheduler] met the deadline.
 * WorkManager has no timing guarantee, so the deadline belongs to an exact alarm; this does
 * what WorkManager is good at: waiting for a network and retrying with backoff. Enqueued only
 * by [EscalationReceiver].
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
            // Recorded locally, not delivered. Retry with backoff; this push is still the first attempt to reach the family.
            AlertEscalator.Outcome.Offline -> Result.retry()
            AlertEscalator.Outcome.Failed -> Result.retry()
        }
    }

    companion object {
        private const val KEY_ALERT_ID = "alert_id"

        private fun workName(alertId: Int) = "escalation_$alertId"

        /**
         * Queues a retry for an escalation that was due but couldn't be delivered. Requires
         * connectivity; the local audit entry was already written by
         * [AlertEscalator.escalateToFamily].
         */
        fun enqueueRetry(context: Context, alertId: Int) {
            val request = OneTimeWorkRequestBuilder<EscalationWorker>()
                .setInputData(workDataOf(KEY_ALERT_ID to alertId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                // LINEAR, not the default EXPONENTIAL: doubling from 30 s put the 7th retry ~31
                // minutes in, on the job whose purpose is a 30-second delivery target. Linear
                // (30 s, 60 s, 90 s...) lands it at ~10.5 minutes.
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(alertId),
                // KEEP: an already-queued retry is still valid and replacing it would restart its backoff.
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /** Called when the senior self-cancels, so no retry is spent on a closed alert. */
        fun cancel(context: Context, alertId: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(alertId))
        }
    }
}
