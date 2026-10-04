package com.pup.seenior.network

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.network.dto.HeartbeatRequest

/**
 * Tells the server this phone is still running, and how much charge it has. Without it, a
 * phone quietly monitoring and one that was flat, off or no longer running the app after a
 * reboot looked the same to the family.
 *
 * The timestamp is the substance; the battery reading answers the obvious next question and
 * fills the family Home tab's Battery tile.
 *
 * Current reading only, never a history (spec section 11): each call overwrites the last,
 * since a series of charge readings would reveal when the senior sleeps. Do not turn this
 * into a log.
 */
object HeartbeatReporter {

    private const val TAG = "HeartbeatReporter"

    /**
     * Sends one check-in. Never throws: the caller is a background watchdog whose job is
     * keeping monitoring alive, and a missed heartbeat costs nothing since the next one is
     * 15 minutes away.
     */
    suspend fun report(context: Context, db: SeniorAppDatabase) {
        val app = context.applicationContext

        // The same APK serves both roles and this runs from the Application class, so it also
        // fires on a family contact's phone, which has no senior to report on.
        if (db.seniorDao().getOnboardedSenior() == null) return

        try {
            // Fetched on every check-in because FCM rotates tokens. Null is fine; the server
            // leaves the stored token alone.
            val reading = readBattery(app).copy(pushToken = PushTokenRegistrar.currentTokenOrNull())
            SeniorCloudSync(db).withSyncId { syncId ->
                RetrofitClient.api.sendHeartbeat(syncId, reading)
            }
            Log.i(TAG, "Heartbeat sent (battery=${reading.batteryPercent}, charging=${reading.isCharging})")
        } catch (e: Exception) {
            // INFO, not WARN: being off the network is ordinary and this fires every 15 minutes.
            Log.i(TAG, "Heartbeat not delivered (${e.javaClass.simpleName})")
        }
    }

    /**
     * Reads the charge from the sticky battery broadcast, the same source as the senior's
     * Home tab. Either field can be null; the check-in still counts and the server keeps the
     * previous figure.
     */
    private fun readBattery(context: Context): HeartbeatRequest {
        val status: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else null

        // EXTRA_PLUGGED is 0 on battery and non-zero for AC, USB or wireless. -1 means the
        // broadcast didn't carry it, which is reported as unknown rather than "not charging".
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val charging = if (plugged >= 0) plugged != 0 else null

        return HeartbeatRequest(batteryPercent = percent, isCharging = charging, pushToken = null)
    }
}
