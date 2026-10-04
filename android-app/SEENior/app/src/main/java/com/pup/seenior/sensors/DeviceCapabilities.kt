package com.pup.seenior.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * What this handset can and may measure. Two questions, answered separately because only one
 * is fixable:
 *
 * - Permission is revocable and repairable. Android can take ACTIVITY_RECOGNITION back after
 *   onboarding (revoked by hand, a storage sweep, or auto-revoke). [missingRequiredPermissions]
 *   finds that.
 * - Hardware isn't repairable. A handset with no `TYPE_STEP_COUNTER` never will have one.
 *   [hasStepCounter] stops the app mistaking this for the first case.
 *
 * Seen on a realme RMP2204 tablet (2026-09-23): five days of zero steps looked like a denied
 * permission, but it was granted and the device simply has no step counter.
 */
object DeviceCapabilities {

    /**
     * Whether this handset has a step counter at all. The counter is also the witness
     * [SensorCollectionService] uses to tell a frozen gap from stillness, so without one the
     * inactivity figures lose their only independent check.
     */
    fun hasStepCounter(context: Context): Boolean {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return false
        return sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    /**
     * The runtime permissions monitoring needs that this install doesn't hold. Uses the same
     * rule as [com.pup.seenior.ui.onboarding.PermissionsScreen.requiredPermissionsGranted],
     * including that the two location permissions count as one answer. Returns the list so the
     * caller can show the system dialog instead of sending the senior to Settings.
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
            // Both, so the system offers the Precise/Approximate choice.
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
