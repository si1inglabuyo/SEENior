package com.pup.seenior.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.pup.seenior.MainActivity
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import java.util.concurrent.TimeUnit

/**
 * The response-window deadline, as a real alarm.
 *
 * This used to be a WorkManager job, which Android defers during Doze (on an Infinix X6885,
 * two 10-minute windows escalated 25 minutes late, and a 60-second fall window not until the
 * device left Doze). [AlarmManager.setExactAndAllowWhileIdle] was not enough either: it
 * escalated 10.7 minutes late under deep Doze with Battery Saver on.
 *
 * [AlarmManager.setAlarmClock] is the one alarm Android exempts from both Doze and Battery
 * Saver, so it is the only mechanism that can meet the 30-second delivery target on an idle
 * phone. WorkManager is still used, only to retry a delivery that failed for want of a
 * network after the deadline has passed.
 */
object EscalationScheduler {

    private const val TAG = "EscalationScheduler"

    const val ACTION_ESCALATE = "com.pup.seenior.action.ESCALATE"
    const val EXTRA_ALERT_ID = "alert_id"

    /**
     * Arms the deadline for [alert], anchored to when it was raised so a device that was
     * asleep or rebooting can't hand the senior a fresh countdown.
     */
    fun arm(context: Context, alert: Alert) {
        val windowMillis = TimeUnit.SECONDS.toMillis(
            AlertEscalator.windowSecondsFor(alert.triggerType).toLong()
        )
        val dueAt = alert.triggeredAt + windowMillis
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = pendingIntent(context, alert.alertId, PendingIntent.FLAG_UPDATE_CURRENT)

        // A deadline already in the past fires immediately, which is correct: the family is overdue.
        try {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(dueAt, showIntent(context, alert.alertId)),
                intent
            )
        } catch (e: SecurityException) {
            // USE_EXACT_ALARM can't be revoked, so this shouldn't happen, but an OEM could still
            // refuse. The fallbacks below are subject to Doze deferral, so they are a last
            // resort. Logged so the degradation is visible.
            Log.w(TAG, "Alarm-clock deadline denied; degrading for alert ${alert.alertId}", e)
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, intent)
            } catch (denied: SecurityException) {
                Log.w(TAG, "Exact alarm denied too; falling back to inexact", denied)
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, intent)
            }
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
     * Re-arms every open alert whose deadline could still do something. Alarms don't survive a
     * reboot, and on some handsets the boot broadcast never arrives. Called from
     * [com.pup.seenior.sensors.MonitoringWatchdogJobService] and [com.pup.seenior.sensors.BootReceiver].
     *
     * [AlertEscalator] leaves an escalated alert `pending`, so this query also returns
     * handled alerts, and re-arming one would fire at once and show an alarm icon every
     * period. So an alert is skipped only when it has reached the family and the cloud has
     * it; one that never synced is what recovery is for.
     */
    suspend fun rearmAll(context: Context) {
        val db = SeniorAppDatabase.getInstance(context.applicationContext)
        val actionable = db.alertDao().getPendingAlerts()
            .filterNot { AlertEscalator.hasEscalatedToFamily(it) && it.isSynced }
        actionable.forEach { arm(context.applicationContext, it) }
        if (actionable.isNotEmpty()) {
            Log.i(TAG, "Re-armed ${actionable.size} open alert(s)")
        }
    }

    /**
     * What opens when the senior taps the alarm Android shows while an alert is open.
     * [AlarmManager.setAlarmClock] makes the deadline visible, and the tap lands on the
     * wellness prompt, so it's a second way to answer before anyone else is told. Same target
     * and request code as [AlertNotifier]'s content intent.
     */
    private fun showIntent(context: Context, alertId: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            alertId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun pendingIntent(context: Context, alertId: Int, flags: Int): PendingIntent {
        val intent = Intent(context, EscalationReceiver::class.java)
            .setAction(ACTION_ESCALATE)
            // filterEquals() ignores extras, so without a per-alert data URI the second arm() would overwrite the first.
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
