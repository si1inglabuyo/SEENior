package com.pup.seenior.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pup.seenior.database.SeniorAppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when an alert's response window runs out, woken by [EscalationScheduler]'s exact alarm.
 *
 * This is the moment the escalation chain moves past the senior and reaches the family
 * (CLAUDE.md §7), and it has to work with the phone asleep on a table and nobody in the room —
 * which is why the deadline is an alarm rather than a deferrable background job.
 */
class EscalationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EscalationScheduler.ACTION_ESCALATE) return
        val alertId = intent.getIntExtra(EscalationScheduler.EXTRA_ALERT_ID, -1)
        if (alertId <= 0) return

        // goAsync buys this receiver time to finish a network call. It is a limited budget
        // (~10s), which is exactly why a failed delivery is handed to WorkManager below rather
        // than retried inline.
        val pendingResult = goAsync()
        val app = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SeniorAppDatabase.getInstance(app)
                val alert = db.alertDao().getById(alertId)
                if (alert == null) {
                    Log.w(TAG, "Alert $alertId no longer exists")
                    return@launch
                }
                // The senior answered, or the chain already moved on without us.
                if (alert.status != "pending") return@launch

                when (val outcome = AlertEscalator.escalateToFamily(db, alertId)) {
                    AlertEscalator.Outcome.Delivered ->
                        Log.i(TAG, "Alert $alertId escalated to family")
                    // The audit entry is written either way; only the cloud copy is missing.
                    // Hand it to WorkManager, which can wait for a network and back off —
                    // the deadline itself has already been honoured on time, which is the part
                    // an alarm was needed for.
                    else -> {
                        Log.w(TAG, "Alert $alertId escalation not delivered ($outcome); queueing retry")
                        EscalationWorker.enqueueRetry(app, alertId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Escalation failed for alert $alertId", e)
                EscalationWorker.enqueueRetry(app, alertId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "EscalationReceiver"
    }
}
