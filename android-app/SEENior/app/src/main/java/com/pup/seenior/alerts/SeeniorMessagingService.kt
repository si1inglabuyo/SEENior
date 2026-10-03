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
 * Receives alerts pushed from the backend (spec §13 step 11).
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

        // A tap on the shoulder from the server, carrying nothing else (see send_wake in
        // backend/app/core/push.py). This handset cannot keep its own five-minute clock --
        // measured on the Infinix on 2026-08-29, the sensor loop produced one sample in
        // twenty-four minutes and the persisted watchdog job left a twelve-hour hole
        // overnight -- so the server keeps it and this is where the phone answers.
        if (data["type"] == "wake") {
            SensorCollectionService.pollNow(applicationContext)
            return
        }

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

        confirmReceipt(alertSyncId)
    }

    /**
     * Tells the server this phone got the push, so it skips the SMS it would otherwise send
     * once its grace period runs out. Only a phone that is online can do this, which is the
     * point: no receipt means no data, and the server texts instead.
     *
     * Two attempts, because a receipt that fails on a flaky connection only costs a
     * redundant text, but one retry is cheap. Never fatal.
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
