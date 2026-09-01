package com.pup.seenior.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The one place this app asks where the senior's phone is.
 *
 * Called at alert-trigger time and nowhere else, which is the whole of CLAUDE.md §11's location
 * rule: no continuous tracking, no location history, nothing recorded on an ordinary day. The fix
 * is turned into a [Geohash] cell immediately and the [Location] object is dropped — the raw
 * coordinates exist only as locals inside [capture] and are never written anywhere.
 *
 * Returns null whenever a cell cannot be produced (permission declined, every provider off, no fix
 * inside the timeout). Null is a normal outcome, not a failure: the alert still escalates, and the
 * family's map falls back to the senior's registered address.
 */
object AlertLocationCapture {

    /** A stored fix older than this describes where the phone was, not where it is. */
    private const val MAX_FIX_AGE_MS = 5 * 60 * 1000L

    /**
     * Default wait for a live fix, sized for alerts whose response window is short.
     *
     * Bounded by the shortest window in the escalation chain: an SOS notifies everyone almost at
     * once (CLAUDE.md §7), and a cell that arrives after the alert has already been sent is of no
     * use to anyone. Callers with a longer window pass a longer budget — see
     * `AlertResponder.locationTimeoutMsFor`.
     */
    private const val LIVE_FIX_TIMEOUT_MS = 20_000L

    /**
     * Captures one fix and reduces it to a geohash cell.
     *
     * Cheap sources first: a fix another app requested moments ago is as good as one asked for
     * here and costs no radio time, so the providers' stored fixes are checked before anything is
     * powered up.
     *
     * [timeoutMs] is how long the caller can afford to wait for a live fix — see
     * `AlertResponder.locationTimeoutMsFor`, which sizes it by what raised the alert.
     */
    suspend fun capture(context: Context, timeoutMs: Long = LIVE_FIX_TIMEOUT_MS): String? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val recent = freshestStoredFix(manager)
        if (recent != null) return Geohash.encode(recent.latitude, recent.longitude)

        val live = awaitLiveFix(manager, timeoutMs) ?: return null
        return Geohash.encode(live.latitude, live.longitude)
    }

    /**
     * Either location permission will do.
     *
     * The app asks for both so Android 12+ offers the senior "Precise", but a senior who answers
     * "Approximate" grants only the coarse one -- and coarse is still enough to place an alert,
     * just to a wider area than the drawn cell suggests. Refusing to capture anything in that
     * case would punish the more privacy-conscious answer.
     */
    private fun hasPermission(context: Context): Boolean =
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * The most recent stored fix across every enabled provider, if any is recent enough.
     *
     * Age is measured on the elapsed-realtime clock rather than [Location.getTime], so a fix does
     * not appear to be from the future — or from hours ago — after the handset syncs its wall
     * clock, which it does on every network change.
     */
    private fun freshestStoredFix(manager: LocationManager): Location? {
        val now = SystemClock.elapsedRealtimeNanos()
        return manager.runCatching { getProviders(true) }.getOrNull()
            .orEmpty()
            .mapNotNull { provider ->
                // Providers can be revoked between being listed and being read, and GPS is
                // refused outright on some versions when only the coarse permission is held.
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { (now - it.elapsedRealtimeNanos) / 1_000_000L <= MAX_FIX_AGE_MS }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    /** Asks the providers for a new fix, giving up after [timeoutMs]. */
    private suspend fun awaitLiveFix(manager: LocationManager, timeoutMs: Long): Location? {
        val providers = manager.runCatching { getProviders(true) }.getOrNull().orEmpty()
        if (providers.isEmpty()) return null

        // Held out here so all three exits — a fix arriving, the timeout, no provider accepting
        // the request — unregister the same instance. removeUpdates needs the object that
        // registered, and an alert that leaves the radio requesting updates forever would cost
        // far more battery than the sampling this app is careful about everywhere else.
        var listener: LocationListener? = null
        try {
            return withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val fixListener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            // The providers keep reporting until unregistered, and a second
                            // report would resume a continuation that is already finished.
                            if (continuation.isActive) continuation.resume(location)
                        }

                        // Present for API 26: the platform's default implementations arrived in
                        // 30, and without these the listener will not compile against minSdk.
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    listener = fixListener

                    // Every enabled provider is asked at once and the first answer wins. Asking
                    // in sequence would mean a phone indoors spends the whole budget waiting on
                    // a GPS lock that is not coming, while the network provider that would have
                    // answered at once is never reached.
                    val accepted = providers.count { provider ->
                        runCatching {
                            manager.requestLocationUpdates(
                                provider,
                                0L,
                                0f,
                                fixListener,
                                // The callback needs a prepared Looper and this runs on an IO
                                // thread, which has none. The main thread is the one Looper
                                // always alive; the work done in the callback is a resume.
                                Looper.getMainLooper()
                            )
                            true
                        }.getOrDefault(false)
                    }
                    if (accepted == 0 && continuation.isActive) continuation.resume(null)
                }
            }
        } finally {
            listener?.let { runCatching { manager.removeUpdates(it) } }
        }
    }
}
