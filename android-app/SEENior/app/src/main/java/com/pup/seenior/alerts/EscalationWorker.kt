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
 * Retries an escalation whose *delivery* failed, after [EscalationScheduler] has already met the
 * deadline.
 *
 * This class used to own the deadline itself, and that was the wrong tool: WorkManager offers no
 * timing guarantee, and Doze was measured deferring a sixty-second fall window indefinitely. The
 * deadline now belongs to an exact alarm; what is left here is the job WorkManager is genuinely
 * good at — waiting for a network to come back and retrying with backoff, across process death.
 *
 * Enqueued only by [EscalationReceiver], never on the happy path.
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
         * Queues a retry for an escalation that was due now but could not be delivered.
         *
         * Requires connectivity, unlike the old deadline job: there is nothing to do without a
         * network, and the local audit entry has already been written by
         * [AlertEscalator.escalateToFamily] regardless of whether the cloud copy landed.
         */
        fun enqueueRetry(context: Context, alertId: Int) {
            val request = OneTimeWorkRequestBuilder<EscalationWorker>()
                .setInputData(workDataOf(KEY_ALERT_ID to alertId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(alertId),
                // KEEP: an already-queued retry is still valid; replacing it would restart its
                // backoff and delay the delivery further.
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
