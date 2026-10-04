package com.pup.seenior.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Whether this install has ever put the location question to the senior. Not holding precise
 * location has two causes that look the same at runtime: the senior chose Approximate (respect
 * it), or was never asked because they onboarded before location capture existed. Only the
 * second is worth repairing, so onboarding records that it asked and the dashboard repairs
 * only installs with no such record.
 */
object LocationPermissionState {

    private const val PREFS = "location_permission"
    private const val KEY_ASKED = "asked"

    /** Called wherever the senior is actually shown the system dialog. */
    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }

    fun wasAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    /**
     * Whether the precise permission is held. Coarse alone doesn't count: Android 12+ fuzzes
     * it to a ~1-2 km grid, and the cell is encoded at ~5 m either way, so a coarse fix would
     * be drawn as a confident pin that could be a kilometre out. Coarse still produces a
     * usable cell, which is why [AlertLocationCapture] accepts it.
     */
    fun hasPrecise(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
