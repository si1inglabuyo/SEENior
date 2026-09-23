package com.pup.seenior.diagnostics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pup.seenior.sensors.DeviceCapabilities

/**
 * Sends crashes from a tester's phone to somewhere they can actually be read.
 *
 * Until this existed, a crash on a phone nobody owns was invisible. The app closed, monitoring
 * stopped, and the senior's family had no way of knowing and no reason to suspect. The
 * foreground-service crash fixed on 2026-09-23 had been live since the v1.8 rollout on 09-18 and
 * was found only because a laptop happened to be plugged into that handset five days later; on
 * the pilot Infinix it had never fired once, so no amount of testing here would have surfaced it.
 * A pilot that runs on handsets you cannot inspect needs crashes to travel on their own.
 *
 * **The privacy boundary, which this does not move.** Crashlytics sends a stack trace, the device
 * model, the OS and app version, and an install identifier of its own. It does not read logcat,
 * and it is given nothing from [com.pup.seenior.database.SeniorAppDatabase]. No senior's name,
 * number, address, location or sensor reading is attached, and `setUserId` is deliberately never
 * called -- a crash report says *what broke on which model of phone*, never *who was using it*.
 * CLAUDE.md §11 is intact: raw behavioural data still never leaves the device.
 *
 * The keys below are the three facts that cost a USB cable and an afternoon to establish on each
 * handset this week, and that between them explain most of what goes wrong on a phone that is not
 * the pilot. Carrying them on every report turns "it crashed on someone's phone" into a report
 * that already names the likely cause. All three describe the *device*, not its owner.
 */
object CrashReporting {

    fun start(context: Context) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        // On by default; stated anyway so that turning it off is a visible edit to this line
        // rather than a silent default change in a future SDK.
        crashlytics.isCrashlyticsCollectionEnabled = true

        // Whether this handset can count steps at all. Zero steps means a missing sensor on one
        // phone and a revoked permission on another, and the two need opposite responses -- see
        // [DeviceCapabilities].
        crashlytics.setCustomKey("has_step_counter", DeviceCapabilities.hasStepCounter(context))

        // Whether the OS has agreed not to doze the app. False here is the single best predictor
        // of a handset that will lose half its samples, and it is invisible from the app's own
        // behaviour until the data comes back thin a week later.
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        crashlytics.setCustomKey(
            "battery_exempt",
            power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        )

        // The vendor skin, which is what actually decides whether a foreground service survives.
        // Build.MANUFACTURER alone does not distinguish Funtouch from ColorOS from XOS.
        crashlytics.setCustomKey("os_build", Build.DISPLAY)
    }
}
