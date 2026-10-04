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
 * The one place this app asks where the senior's phone is. Called at alert time and nowhere
 * else: no continuous tracking, no history. The fix becomes a [Geohash] cell immediately and
 * the [Location] is dropped, so raw coordinates only exist as locals inside [capture].
 *
 * Returns null when no cell can be produced (permission declined, providers off, no fix in
 * time). That's a normal outcome: the alert still escalates and the family's map falls back
 * to the registered address.
 */
object AlertLocationCapture {

    /** A stored fix older than this describes where the phone was, not where it is. */
    private const val MAX_FIX_AGE_MS = 5 * 60 * 1000L

    /**
     * Default wait for a live fix, sized for alerts with a short response window. A cell that
     * arrives after the alert is sent helps no one. Callers with a longer window pass a
     * longer budget (see `AlertResponder.locationTimeoutMsFor`).
     */
    private const val LIVE_FIX_TIMEOUT_MS = 20_000L

    /**
     * Captures one fix and reduces it to a geohash cell. Stored fixes are checked first, since
     * a fix another app requested moments ago is as good and costs no radio time. [timeoutMs]
     * is how long the caller can wait for a live fix.
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
     * Either location permission will do. The app asks for both so Android 12+ offers
     * "Precise", but a senior who answers "Approximate" grants only coarse, which still places
     * an alert. Refusing would punish the more privacy-conscious answer.
     */
    private fun hasPermission(context: Context): Boolean =
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * The most recent stored fix across enabled providers, if recent enough. Age uses the
     * elapsed-realtime clock, not [Location.getTime], which jumps when the phone syncs its clock.
     */
    private fun freshestStoredFix(manager: LocationManager): Location? {
        val now = SystemClock.elapsedRealtimeNanos()
        return manager.runCatching { getProviders(true) }.getOrNull()
            .orEmpty()
            .mapNotNull { provider ->
                // Providers can be revoked after being listed, and GPS is refused on some versions with only coarse permission.
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { (now - it.elapsedRealtimeNanos) / 1_000_000L <= MAX_FIX_AGE_MS }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    /** Asks the providers for a new fix, giving up after [timeoutMs]. */
    private suspend fun awaitLiveFix(manager: LocationManager, timeoutMs: Long): Location? {
        val providers = manager.runCatching { getProviders(true) }.getOrNull().orEmpty()
        if (providers.isEmpty()) return null

        // Held out here so all three exits (a fix, the timeout, no provider accepting) unregister
        // the same instance. A listener left registered would drain the battery.
        var listener: LocationListener? = null
        try {
            return withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val fixListener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            // Providers keep reporting until unregistered; a second report would resume a finished continuation.
                            if (continuation.isActive) continuation.resume(location)
                        }

                        // Needed for API 26: the platform's default implementations arrived in 30.
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    listener = fixListener

                    // Every enabled provider is asked at once and the first answer wins. In
                    // sequence, an indoor phone would spend the budget waiting on a GPS lock
                    // while the network provider would have answered at once.
                    val accepted = providers.count { provider ->
                        runCatching {
                            manager.requestLocationUpdates(
                                provider,
                                0L,
                                0f,
                                fixListener,
                                // The callback needs a prepared Looper, which IO threads lack.
                                // The main Looper always exists, and the callback only resumes.
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
