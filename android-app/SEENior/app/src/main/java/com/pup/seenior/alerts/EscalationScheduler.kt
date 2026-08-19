package com.pup.seenior.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import java.util.concurrent.TimeUnit

/**
 * The response-window deadline, as a real alarm.
 *
 * This used to be a WorkManager job, and that was measurably wrong. WorkManager makes no timing
 * promise by design — Android defers its jobs during Doze and app-standby and batches them into
 * maintenance windows. Measured on an Infinix X6885 (Android 15) with the phone idle: two
 * ten-minute windows escalated **25 minutes late**, and a sixty-second fall window did not
 * escalate *at all* until the device left Doze. The one alert that fired on time did so only
 * because someone happened to be using the phone at that moment.
 *
 * That is exactly backwards for this product. An unanswered alert is *most* likely to matter
 * when the phone has been left untouched — which is precisely the state in which the old
 * mechanism stopped working.
 *
 * [AlarmManager.setExactAndAllowWhileIdle] is the only API that promises to fire at a wall-clock
 * moment through Doze, so the deadline lives here now. WorkManager is still used, but only for
 * what it is genuinely good at: retrying a *delivery* that failed for want of a network, with
 * backoff, after the deadline itself has already been honoured.
 */
object EscalationScheduler {

    private const val TAG = "EscalationScheduler"

    const val ACTION_ESCALATE = "com.pup.seenior.action.ESCALATE"
    const val EXTRA_ALERT_ID = "alert_id"

    /**
     * Arms the deadline for [alert], anchored to when it was raised rather than to now, so a
     * device that was asleep or rebooting cannot hand the senior a fresh countdown.
     */
    fun arm(context: Context, alert: Alert) {
        val windowMillis = TimeUnit.SECONDS.toMillis(
            AlertEscalator.windowSecondsFor(alert.triggerType).toLong()
        )
        val dueAt = alert.triggeredAt + windowMillis
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = pendingIntent(context, alert.alertId, PendingIntent.FLAG_UPDATE_CURRENT)

        // A deadline already in the past fires immediately, which is correct: it means the phone
        // was off or Doze held us past the window, and the family is overdue being told.
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, intent)
        } catch (e: SecurityException) {
            // USE_EXACT_ALARM is declared, so this should not happen — but an OEM or a future
            // policy change could still refuse. Degrading to an inexact alarm keeps the chain
            // alive (late is better than never); it is logged because the degradation is
            // invisible otherwise and would look exactly like the bug this class replaced.
            Log.w(TAG, "Exact alarm denied; falling back to inexact for alert ${alert.alertId}", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, intent)
        }
    }

    /** Called when the senior self-cancels, so no wake-up is spent on a closed alert. */
    fun cancel(context: Context, alertId: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context, alertId, PendingIntent.FLAG_UPDATE_CURRENT))
        // The retry job is a separate mechanism and outlives the alarm, so it has to go too.
        EscalationWorker.cancel(context, alertId)
    }

    /**
     * Re-arms every still-open alert. Called from [com.pup.seenior.sensors.BootReceiver].
     *
     * Alarms do not survive a reboot. Without this, an alert that was open when the phone
     * restarted would sit `pending` forever, and the senior would never be escalated for it —
     * a silent failure in the one direction that matters.
     */
    suspend fun rearmAll(context: Context) {
        val db = SeniorAppDatabase.getInstance(context.applicationContext)
        val pending = db.alertDao().getPendingAlerts()
        pending.forEach { arm(context.applicationContext, it) }
        if (pending.isNotEmpty()) {
            Log.i(TAG, "Re-armed ${pending.size} open alert(s) after boot")
        }
    }

    private fun pendingIntent(context: Context, alertId: Int, flags: Int): PendingIntent {
        val intent = Intent(context, EscalationReceiver::class.java)
            .setAction(ACTION_ESCALATE)
            // The action alone does not distinguish two alerts; filterEquals() ignores extras,
            // so without a per-alert data URI the second arm() would overwrite the first.
            .setData(android.net.Uri.parse("seenior://alert/$alertId"))
            .putExtra(EXTRA_ALERT_ID, alertId)

        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(context, alertId, intent, flags or immutable)
    }
}
