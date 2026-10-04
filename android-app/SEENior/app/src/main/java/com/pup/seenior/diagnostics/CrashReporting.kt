package com.pup.seenior.diagnostics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pup.seenior.sensors.DeviceCapabilities

/**
 * Sends crashes from testers' phones somewhere they can be read. A crash on a phone nobody
 * has to hand was invisible: the foreground-service crash fixed on 2026-09-23 had been live
 * since the v1.8 rollout and was found by chance.
 *
 * Privacy boundary: Crashlytics sends a stack trace, device model, OS and app version and its
 * own install id. It doesn't read logcat and is given nothing from
 * [com.pup.seenior.database.SeniorAppDatabase]. No name, number, address, location or sensor
 * reading is attached, and `setUserId` is never called, so a report says what broke on which
 * phone, not who used it. Raw behavioural data still never leaves the device (spec section 11).
 *
 * The custom keys below are three device facts that explain most failures on handsets we
 * can't inspect. All describe the device, not its owner.
 */
object CrashReporting {

    fun start(context: Context) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        // On by default; stated here so turning it off is a visible edit.
        crashlytics.isCrashlyticsCollectionEnabled = true

        // Whether the handset can count steps. Zero steps means a missing sensor on one phone
        // and a revoked permission on another (see [DeviceCapabilities]).
        crashlytics.setCustomKey("has_step_counter", DeviceCapabilities.hasStepCounter(context))

        // Whether the OS has agreed not to doze the app. False is the best predictor of a handset that loses samples.
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        crashlytics.setCustomKey(
            "battery_exempt",
            power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        )

        // The vendor skin decides whether a foreground service survives; Build.MANUFACTURER alone can't tell them apart.
        crashlytics.setCustomKey("os_build", Build.DISPLAY)
    }
}
