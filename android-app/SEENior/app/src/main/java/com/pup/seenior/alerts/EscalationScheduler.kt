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
 * [AlarmManager.setExactAndAllowWhileIdle] was the first attempt and was not enough. Measured on
 * the same handset under natural deep Doze — whitelisted, exact-alarm permission granted, standby
 * bucket EXEMPTED — a ten-minute window escalated **10.7 minutes late**, and `dumpsys alarm` never
 * listed the package under `Allow while idle history` at all: the alarm was not dispatched through
 * that path, it simply waited for the next Doze maintenance window. Battery Saver was on, as it
 * will be on a senior's phone at 15% — which is exactly when an unanswered alert matters most.
 *
 * [AlarmManager.setAlarmClock] is the one alarm Android exempts from *both* Doze and Battery
 * Saver, because it is the contract behind a morning alarm actually going off. It is therefore
 * the only mechanism that can hold the §10 target of a 30-second delivery on an idle phone, so
 * the deadline lives here now. WorkManager is still used, but only for what it is genuinely good
 * at: retrying a *delivery* that failed for want of a network, with backoff, after the deadline
 * itself has already been honoured.
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
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(dueAt, showIntent(context, alert.alertId)),
                intent
            )
        } catch (e: SecurityException) {
            // USE_EXACT_ALARM is declared and cannot be revoked by the user, so this should not
            // happen — but an OEM or a future policy change could still refuse. Both rungs below
            // are subject to the Doze deferral this class exists to fix, so they are a last
            // resort, not an equivalent: late is better than never. Logged because the
            // degradation is otherwise invisible and would look exactly like the original bug.
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

    /**
     * What opens when the senior taps the alarm Android now shows while an alert is open.
     *
     * [AlarmManager.setAlarmClock] makes the pending deadline visible — a status-bar alarm icon
     * and an entry in the clock app — and that visibility is the price of the Doze and Battery
     * Saver exemption. It is spent usefully rather than merely tolerated: the tap lands on the
     * wellness prompt, so the icon is a second route for the senior to answer and self-cancel
     * before anyone else is told (CLAUDE.md §7). It is only ever on screen while an alert is
     * genuinely open, alongside the prompt and the ongoing notification.
     *
     * Deliberately the same target and request code as [AlertNotifier]'s content intent: both
     * mean "open the app for this alert", and Android should treat them as one.
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
