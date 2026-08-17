package com.pup.seenior.alerts

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pup.seenior.network.PushTokenRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives alerts pushed from the backend (CLAUDE.md §13 step 11).
 *
 * Before this existed the family app only learned about an alert by polling, which stops
 * the moment the app is closed — so an alert raised while nobody was looking at their
 * phone reached no one. This is what makes the family tier of the escalation chain work
 * when it matters.
 */
class SeeniorMessagingService : FirebaseMessagingService() {

    // Its own scope, not lifecycleScope: this service is torn down as soon as
    // onMessageReceived returns, and the registration call must be allowed to finish.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fires for every push, foreground or background.
     *
     * That is only true because the backend sends DATA-ONLY messages — had it attached a
     * `notification` payload, Android would render it from the system tray whenever the
     * app was backgrounded and this method would never run, taking the alarm sound and
     * the alert-specific tap target with it.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "alert") {
            Log.d(TAG, "Ignoring push of unknown type: ${data["type"]}")
            return
        }

        // A malformed push must be dropped quietly rather than crash the receiver — this
        // runs in the background with no UI to report into, and a crash loop here would
        // take out delivery of every subsequent alert too.
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
    }

    /**
     * FCM rotates tokens on its own schedule — a reinstall, cleared data, or Google's own
     * decision. The backend is told immediately, because a stale token is a family member
     * who has silently stopped receiving alerts with nothing on screen to show for it.
     */
    override fun onNewToken(token: String) {
        scope.launch { PushTokenRegistrar.syncToken(applicationContext) }
    }

    private companion object {
        const val TAG = "SeeniorMessaging"
    }
}
