package com.pup.seenior.alerts

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pup.seenior.network.PushTokenRegistrar
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.sensors.SensorCollectionService
import com.pup.seenior.session.FamilySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Receives alerts pushed from the backend. Without push the family app only learned about an
 * alert by polling, which stops when the app is closed.
 */
class SeeniorMessagingService : FirebaseMessagingService() {

    // Its own scope, since this service is torn down once onMessageReceived returns.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fires for every push, foreground or background. That relies on the backend sending
     * data-only messages; a `notification` payload would be shown by the system tray when the
     * app is backgrounded and this method would never run.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        // A wake from the server, carrying nothing else (see send_wake in backend/app/core/push.py).
        // This phone can't keep its own 5-minute clock (one sample in 24 minutes on the Infinix),
        // so the server keeps it and the phone answers here.
        if (data["type"] == "wake") {
            SensorCollectionService.pollNow(applicationContext)
            return
        }

        if (data["type"] != "alert") {
            Log.d(TAG, "Ignoring push of unknown type: ${data["type"]}")
            return
        }

        // Drop a malformed push quietly; a crash loop here would block every later alert.
        val alertSyncId = data["alert_sync_id"]
        val seniorSyncId = data["senior_sync_id"]
        if (alertSyncId.isNullOrBlank() || seniorSyncId.isNullOrBlank()) {
            Log.w(TAG, "Alert push missing sync ids; ignoring")
            return
        }

        FamilyAlertNotifier.notify(
            context = applicationContext,
            alertSyncId = alertSyncId,
            seniorSyncId = seniorSyncId,
            seniorName = data["senior_name"].orEmpty().ifBlank { "Your senior" },
            riskLevel = data["risk_level"].orEmpty(),
            triggerType = data["trigger_type"].orEmpty()
        )

        confirmReceipt(alertSyncId)
    }

    /**
     * Tells the server this phone got the push, so it skips the SMS it would send after the
     * grace period. Only an online phone can do this, which is the point. Two attempts; never fatal.
     */
    private fun confirmReceipt(alertSyncId: String) {
        val app = applicationContext
        val jwt = FamilySession.getToken(app)?.takeIf { FamilySession.hasLiveSession(app) } ?: return
        scope.launch {
            repeat(2) { attempt ->
                val ok = runCatching {
                    RetrofitClient.api.confirmAlertPushReceived(alertSyncId, "Bearer $jwt")
                }.onFailure { Log.w(TAG, "Push receipt failed (attempt ${attempt + 1})", it) }
                    .isSuccess
                if (ok) return@launch
                delay(2_000)
            }
        }
    }

    /**
     * FCM rotates tokens on its own. The backend is told immediately, since a stale token means
     * a family member silently stops receiving alerts.
     */
    override fun onNewToken(token: String) {
        scope.launch { PushTokenRegistrar.syncToken(applicationContext) }
    }

    private companion object {
        const val TAG = "SeeniorMessaging"
    }
}
