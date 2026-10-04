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
import com.pup.seenior.ui.family.alertReasonText

/**
 * Puts an incoming alert in front of the family contact when the app isn't on screen.
 * Separate from [AlertNotifier], which speaks to the senior: this tells someone else that a
 * person they care for may need help, with its own wording and channel. No full-screen
 * intent, since from API 34 Android would almost certainly refuse it and show a banner anyway.
 */
object FamilyAlertNotifier {

    private const val CHANNEL_ID = "family_alerts_channel"

    const val EXTRA_ALERT_SYNC_ID = "alert_sync_id"
    const val EXTRA_SENIOR_SYNC_ID = "senior_sync_id"

    private const val COLOR_HIGH = 0xFFC62828.toInt()
    private const val COLOR_DEFAULT = 0xFF1565C0.toInt()

    fun notify(
        context: Context,
        alertSyncId: String,
        seniorSyncId: String,
        seniorName: String,
        riskLevel: String,
        triggerType: String
    ) {
        if (!canPostNotifications(context)) return
        createChannel(context)

        val isHighRisk = riskLevel == "high" || triggerType == "sos"
        val firstName = seniorName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: seniorName

        // Same wording as the Alerts tab, so the notification and the screen never disagree.
        val title = if (triggerType == "sos") "$firstName pressed the SOS button."
        else "$firstName may need your attention."

        // Reuses the Alerts screen's own copy, for the same reason.
        val body = alertReasonText(triggerType)

        val openAlert = PendingIntent.getActivity(
            context,
            alertSyncId.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_ALERT_SYNC_ID, alertSyncId)
                .putExtra(EXTRA_SENIOR_SYNC_ID, seniorSyncId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seenior)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(
                if (isHighRisk) NotificationCompat.CATEGORY_ALARM
                else NotificationCompat.CATEGORY_MESSAGE
            )
            .setColor(if (isHighRisk) COLOR_HIGH else COLOR_DEFAULT)
            .setContentIntent(openAlert)
            // Swiping it away doesn't count as dealing with it; acknowledge, dispatch or resolve in the app.
            .setAutoCancel(false)
            .setOngoing(isHighRisk)
            .build()

        // Keyed on the alert, so a re-delivery replaces the banner instead of stacking, while a different alert gets its own.
        NotificationManagerCompat.from(context).notify(alertSyncId.hashCode(), notification)
    }

    /** Called once the family member is actually looking at the alert. */
    fun cancel(context: Context, alertSyncId: String) {
        NotificationManagerCompat.from(context).cancel(alertSyncId.hashCode())
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
            "Senior Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts about a senior you are monitoring."
            enableVibration(true)
            // Alarm usage on purpose, matching the senior side: a family member's phone face-down on silent has to be reached.
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
