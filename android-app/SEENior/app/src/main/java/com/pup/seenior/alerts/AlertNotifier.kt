package com.pup.seenior.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pup.seenior.MainActivity
import com.pup.seenior.R
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.ui.wellness.WellnessMessages

/**
 * Brings an alert to the senior's attention when the app is not on screen.
 *
 * The wellness prompt can only be answered by someone already inside the app, so without this an
 * alert raised while the phone sat locked on a table would run its whole response window unseen.
 * A high-risk alert therefore asks to take over the screen outright (a full-screen intent, the
 * same mechanism an alarm clock uses) rather than settling for a banner an unconscious or
 * distressed person will never tap.
 *
 * The notification says what happened and why, in the senior's own language, because it may be
 * the only thing they read before deciding whether to open the app at all.
 */
object AlertNotifier {

    private const val CHANNEL_ID = "senior_alerts_channel"

    /** Offset keeps these clear of the sensor service's own ongoing notification (id 1001). */
    private const val NOTIFICATION_ID_BASE = 2000

    private const val COLOR_HIGH = 0xFFC62828.toInt()
    private const val COLOR_DEFAULT = 0xFF7FBF7A.toInt()

    fun notify(context: Context, alert: Alert, language: String, seniorFirstName: String) {
        if (!canPostNotifications(context)) return

        createChannel(context)

        val copy = WellnessMessages.forAlert(language, seniorFirstName, alert.triggerType, alert.timeBlock)
        val isHighRisk = alert.riskLevel == "high"

        val openApp = PendingIntent.getActivity(
            context,
            alert.alertId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seenior)
            .setContentTitle(copy.headerSubtitle)
            .setContentText(copy.reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.reason))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isHighRisk) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setColor(if (isHighRisk) COLOR_HIGH else COLOR_DEFAULT)
            .setContentIntent(openApp)
            // Never auto-cancel: the alert is closed by answering it, not by brushing the
            // notification away. AlertNotifier.cancel does that once the prompt is on screen.
            .setAutoCancel(false)
            .setOngoing(isHighRisk)

        if (isHighRisk) {
            // Asks to open the prompt immediately over the lock screen. Android may decline —
            // from API 34 this is reserved for calling and alarm apps — in which case it
            // degrades to a heads-up banner backed by the same content intent, which is why
            // setContentIntent above is not conditional.
            builder.setFullScreenIntent(openApp, true)
        }

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_BASE + alert.alertId, builder.build())
    }

    /** Called once the senior is actually looking at the prompt. */
    fun cancel(context: Context, alertId: Int) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_BASE + alertId)
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SEENior Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Wellness checks that need your answer."
            enableVibration(true)
            // Alarm usage on purpose: this is the one thing the app sends that must be heard
            // through a phone left face-down across the room.
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}
