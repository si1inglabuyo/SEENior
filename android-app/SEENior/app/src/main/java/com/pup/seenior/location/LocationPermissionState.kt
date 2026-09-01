package com.pup.seenior.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Whether this install has ever put the location question to the senior.
 *
 * Exists because "the app does not hold precise location" has two completely different causes and
 * they are indistinguishable at runtime:
 *
 *  - the senior was asked and chose Approximate, which is a decision to respect, or
 *  - the senior was never asked at all, because they onboarded before this app captured location
 *    and installing a new APK does not re-open [com.pup.seenior.ui.onboarding.PermissionsScreen].
 *
 * Only the second is a gap worth repairing, and without a record of the asking there is no way to
 * tell them apart — so onboarding records that it asked, and the dashboard repairs only the
 * installs that carry no such record.
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
     * Whether the precise permission is held.
     *
     * Coarse alone is deliberately not enough to count here: Android 12+ fuzzes it to a ~1-2 km
     * grid, and the cell is now encoded at ~5 m either way — so a coarse fix is drawn as a
     * confident pin standing on a spot that could be a kilometre out, which is a worse lie than
     * the old 150 m square ever was. Coarse still produces a usable cell, which is why
     * [AlertLocationCapture] accepts it — this asks the narrower question of whether there is
     * anything left to ask for.
     */
    fun hasPrecise(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
