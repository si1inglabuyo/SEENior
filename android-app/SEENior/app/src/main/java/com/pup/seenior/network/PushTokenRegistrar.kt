package com.pup.seenior.network

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.pup.seenior.network.dto.RegisterDeviceRequest
import com.pup.seenior.session.FamilySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps the backend's record of this device's FCM token current. Without it the family tier
 * only reaches someone who already has the app open, and nothing on screen would show it.
 */
object PushTokenRegistrar {

    private const val TAG = "PushTokenRegistrar"

    /** Outlives any screen, so sign-out cleanup isn't cancelled by the navigation. SupervisorJob so one failure doesn't affect later ones. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Registers the current token against the signed-in family account. Called on every
     * launch of the family dashboard and from
     * [com.pup.seenior.alerts.SeeniorMessagingService.onNewToken]. One call site can't drift
     * out of sync with the login paths, and it repairs a token that rotated while the app
     * wasn't running. Does nothing when nobody is logged in (the senior side has no account).
     */
    suspend fun syncToken(context: Context) {
        val app = context.applicationContext
        val jwt = FamilySession.getToken(app)?.takeIf { FamilySession.hasLiveSession(app) }
        if (jwt == null) return

        val token = runCatching { currentToken() }
            .onFailure { Log.w(TAG, "Could not obtain FCM token", it) }
            .getOrNull() ?: return

        // Never fatal: failing to register falls back to the 20 s polling the app already does.
        runCatching {
            RetrofitClient.api.registerDevice(RegisterDeviceRequest(token), "Bearer $jwt")
        }.onFailure {
            Log.w(TAG, "Device token registration failed", it)
        }
    }

    /**
     * Signs this device out: clears the stored login immediately, then releases the push
     * token in the background.
     *
     * The session is cleared first and synchronously, so a slow cold-start request can't leave
     * a live session. The JWT is captured before the clear because the background job still
     * needs it; dropping the token server-side stops this handset receiving the previous
     * account's alerts, which name the senior. Runs on [appScope] because the Log Out tap
     * destroys the composable and would cancel a screen-scoped job.
     */
    fun signOutAsync(context: Context) {
        val app = context.applicationContext
        val jwt = FamilySession.getToken(app)
        FamilySession.clear(app)
        appScope.launch { releaseToken(app, jwt) }
    }

    /**
     * Best-effort removal of this device's token, server-side and locally. Failures are
     * swallowed, since sign-out has already happened and the server prunes dead tokens anyway.
     */
    private suspend fun releaseToken(context: Context, jwt: String?) {
        val token = runCatching { currentToken() }.getOrNull()
        if (token != null && jwt != null) {
            runCatching {
                RetrofitClient.api.unregisterDevice(token, "Bearer $jwt")
            }.onFailure {
                Log.w(TAG, "Device token unregistration failed", it)
            }
        }
        // Deleted locally too, so the next account on this phone gets a fresh identifier.
        runCatching { deleteToken() }
            .onFailure { Log.w(TAG, "Could not delete local FCM token", it) }
    }

    /** Bridges FirebaseMessaging's Task API into a coroutine without play-services-coroutines. */
    /**
     * This device's FCM token, or null. Public because the senior side needs the same token
     * to be woken by the server and has no family JWT, so it can't use [syncToken].
     */
    suspend fun currentTokenOrNull(): String? =
        runCatching { currentToken() }
            .onFailure { Log.w(TAG, "Could not obtain FCM token", it) }
            .getOrNull()

    private suspend fun currentToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    private suspend fun deleteToken(): Unit = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().deleteToken()
            .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }
}
