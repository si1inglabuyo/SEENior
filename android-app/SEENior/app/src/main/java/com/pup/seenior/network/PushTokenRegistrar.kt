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
 * Keeps the backend's record of this device's FCM token in step with reality.
 *
 * Without a current token on the server, the family tier of the escalation chain is back
 * to only reaching someone who already has the app open — the exact gap push exists to
 * close, and one that fails silently, since nothing on screen looks different.
 */
object PushTokenRegistrar {

    private const val TAG = "PushTokenRegistrar"

    /** Outlives any screen, so sign-out cleanup is not cancelled by the navigation that
     *  triggers it. SupervisorJob so one failed cleanup cannot poison later ones. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Registers the current token against the signed-in family account.
     *
     * Called on every launch of the family dashboard and from
     * [com.pup.seenior.alerts.SeeniorMessagingService.onNewToken]. Registering on launch
     * rather than at each of the three login paths (email register, email login, Google)
     * is deliberate: one call site cannot drift out of sync with the others, and it also
     * repairs the case where a token rotated while the app was not running.
     *
     * Safe to call when nobody is logged in — the senior side of this single-module app
     * has no account at all — in which case it does nothing.
     */
    suspend fun syncToken(context: Context) {
        val app = context.applicationContext
        val jwt = FamilySession.getToken(app)?.takeIf { FamilySession.hasLiveSession(app) }
        if (jwt == null) return

        val token = runCatching { currentToken() }
            .onFailure { Log.w(TAG, "Could not obtain FCM token", it) }
            .getOrNull() ?: return

        // Never fatal. Failing to register means falling back to the 20s polling the app
        // already does, which is a degraded experience — not a broken one — and must not
        // take down whatever screen triggered this.
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
     * The ordering is the whole point, in both directions.
     *
     * The session is cleared FIRST and synchronously, because the moment the user taps Log
     * Out they are logged out. Waiting on the network first would leave a live session for
     * as long as the request takes — and against Render's free tier a cold start is ~40s,
     * easily long enough for the user to reopen the app and be routed straight back into
     * the dashboard they just left.
     *
     * The JWT is therefore captured BEFORE the clear and handed to the background job,
     * which still needs it: dropping the token server-side is what stops this handset
     * receiving the previous account's alerts, and those name the senior, so leaving it
     * behind is a disclosure and not merely untidy (CLAUDE.md §11).
     *
     * Runs on [appScope], not the caller's: the Log Out tap navigates away and destroys
     * the composable immediately, which would cancel a screen-scoped job before the
     * request ever left the device.
     */
    fun signOutAsync(context: Context) {
        val app = context.applicationContext
        val jwt = FamilySession.getToken(app)
        FamilySession.clear(app)
        appScope.launch { releaseToken(app, jwt) }
    }

    /**
     * Best-effort removal of this device's token, server-side and locally.
     *
     * Every failure is swallowed. Sign-out has already happened from the user's point of
     * view, there is no screen left to report into, and the server prunes tokens it finds
     * dead on its next send anyway.
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
        // Deleted locally too, so the next account on this phone gets a fresh identifier
        // rather than inheriting one the server may still associate with someone else.
        runCatching { deleteToken() }
            .onFailure { Log.w(TAG, "Could not delete local FCM token", it) }
    }

    /** Bridges FirebaseMessaging's Task API into a coroutine without pulling in the
     *  play-services-coroutines artifact for one call. */
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
