package com.pup.seenior.network

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.network.dto.HeartbeatRequest

/**
 * Tells the server this phone is still running, and how much charge it has left.
 *
 * Between alerts the senior's phone said nothing to the server at all, so a phone quietly
 * monitoring and a phone that was flat, switched off, or no longer running the app after a
 * reboot looked identical from the family's side — indistinguishable from a senior who simply
 * had an uneventful day. Reboot testing on the Infinix showed that is not hypothetical:
 * monitoring stayed off until somebody opened the app by hand, and nothing told anyone.
 *
 * The timestamp is the substance of this. The battery reading rides along because it answers
 * the obvious next question — a phone at 3% is about to stop monitoring whatever else is true —
 * and because the family Home tab has had a Battery tile showing "—" since it was built.
 *
 * **Current reading only, never a history** (CLAUDE.md §11). Each call overwrites the last on
 * the server. A *series* of charge readings would describe when the senior plugs their phone in,
 * and therefore roughly when they sleep, which is the behavioural data §11 keeps on the device.
 * A single current value only describes whether the device can keep working. Do not turn this
 * into a log.
 */
object HeartbeatReporter {

    private const val TAG = "HeartbeatReporter"

    /**
     * Sends one check-in. Never throws.
     *
     * The caller is a background watchdog whose real job is keeping monitoring alive; a flat
     * network must not stop it doing that, and a missed heartbeat costs nothing — the next one
     * is fifteen minutes away, and the server reads absence as "no contact since", which is
     * exactly what a phone with no signal should look like.
     */
    suspend fun report(context: Context, db: SeniorAppDatabase) {
        val app = context.applicationContext
        try {
            val reading = readBattery(app)
            SeniorCloudSync(db).withSyncId { syncId ->
                RetrofitClient.api.sendHeartbeat(syncId, reading)
            }
            Log.i(TAG, "Heartbeat sent (battery=${reading.batteryPercent}, charging=${reading.isCharging})")
        } catch (e: Exception) {
            // Logged at INFO, not WARN: a senior's phone being off the network for a while is
            // ordinary, and this firing every quarter hour would drown the log that matters.
            Log.i(TAG, "Heartbeat not delivered (${e.javaClass.simpleName})")
        }
    }

    /**
     * Reads the charge from the sticky battery broadcast — the same source the senior's own Home
     * tab uses, so the two never disagree about what the phone is showing.
     *
     * Either field can come back null. A phone that cannot report its charge has still checked
     * in, and the check-in itself is the part the family needs; the server leaves the previous
     * figure alone rather than overwriting it with nothing.
     */
    private fun readBattery(context: Context): HeartbeatRequest {
        val status: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else null

        // EXTRA_PLUGGED is 0 when running on battery and non-zero for AC, USB or wireless.
        // -1 means the broadcast did not carry it, which is a different thing from "not
        // charging" and is reported as unknown rather than guessed at.
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val charging = if (plugged >= 0) plugged != 0 else null

        return HeartbeatRequest(batteryPercent = percent, isCharging = charging)
    }
}
