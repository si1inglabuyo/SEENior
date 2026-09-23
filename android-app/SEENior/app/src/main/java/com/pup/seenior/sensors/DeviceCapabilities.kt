package com.pup.seenior.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * What this particular handset can and may actually measure.
 *
 * Two different questions live here and they are answered separately on purpose, because the
 * remedy is different and only one of them has one:
 *
 *  - **Permission** is revocable and repairable. Android can take ACTIVITY_RECOGNITION back long
 *    after onboarding granted it — the senior revokes it, a "free up space" sweep revokes it, or
 *    the OS auto-revokes it for an app it thinks is unused. [missingRequiredPermissions] finds
 *    that, and the senior can fix it in one tap.
 *  - **Hardware** is neither. A handset with no `TYPE_STEP_COUNTER` will never have one, and no
 *    amount of prompting changes that. [hasStepCounter] exists so the app stops mistaking the
 *    second case for the first.
 *
 * Measured on the realme RMP2204 tester device, 2026-09-23: every step reading zero across five
 * days, which read as a denied permission and was not one. ACTIVITY_RECOGNITION was granted the
 * whole time; `dumpsys sensorservice` lists 18 hardware sensors and no step counter or step
 * detector among them, because the device is a tablet (`ro.build.characteristics=tablet`). The
 * old advice — "check the permission" — was unfollowable, and the honest answer is that this
 * device cannot do the measurement at all.
 */
object DeviceCapabilities {

    /**
     * Whether this handset has a step counter at all.
     *
     * Matters beyond the step column itself. The counter is the witness
     * [SensorCollectionService] uses to tell a gap the OS froze apart from a senior who genuinely
     * did not move, so a device without one loses the only independent check on its own
     * inactivity figures — on exactly the cheap handsets most likely to freeze.
     */
    fun hasStepCounter(context: Context): Boolean {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return false
        return sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    /**
     * The runtime permissions monitoring needs that this install does not currently hold.
     *
     * The rule is deliberately the same one onboarding applies in
     * [com.pup.seenior.ui.onboarding.PermissionsScreen.requiredPermissionsGranted], including its
     * one concession: the two location permissions count as a single answer, because Android 12+
     * offers Precise or Approximate in one dialog and a senior who chose Approximate answered it
     * perfectly reasonably. Any other rule here would have the Home screen nag about a choice
     * onboarding had already accepted.
     *
     * Returns the permissions to re-request, so the caller can put the system dialog in front of
     * the senior rather than sending them into Settings to hunt for a toggle.
     */
    fun missingRequiredPermissions(context: Context): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 29 && !granted(context, Manifest.permission.ACTIVITY_RECOGNITION)) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val hasSomeLocation = granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasSomeLocation) {
            // Both, so the system offers the Precise/Approximate choice rather than silently
            // treating this as a coarse-only request.
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
