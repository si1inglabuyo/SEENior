package com.pup.seenior.alerts

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * The two grants that decide whether an alert can put itself in front of the senior. Both are
 * special permissions granted from a settings page, not a runtime dialog, and declaring them in
 * the manifest does nothing (a fall alert was refused both on 2026-09-04 despite the manifest
 * line). Neither is required: without them the alert still posts, counts down and escalates,
 * but the senior is less likely to see it in time.
 */
object AlertPermissions {

    private const val PREFS = "alert_permissions"
    private const val KEY_ASKED = "asked"

    /** Whether an alert may take over a locked or dark screen. Granted on install below Android 14; from 14 it must be turned on by hand. */
    fun canUseFullScreenIntent(context: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) true
        else (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .canUseFullScreenIntent()

    /**
     * Whether the app may raise the prompt while another app is in the foreground. Also the
     * background-activity-start exemption: from Android 10 a background app can't launch an
     * Activity without it.
     */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** True once both grants are in place and there is nothing left to ask for. */
    fun allGranted(context: Context): Boolean =
        canUseFullScreenIntent(context) && canDrawOverlays(context)

    /** The settings page for the full-screen grant, or null below Android 14, where it is already held. */
    fun fullScreenIntentSettings(context: Context): Intent? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) null
        else Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:" + context.packageName)
        )

    /** The "Display over other apps" page. */
    fun overlaySettings(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )

    /**
     * Whether this install has ever asked the senior these two questions. Mirrors
     * [com.pup.seenior.location.LocationPermissionState]: an install from before they were
     * asked looks the same as one where the senior said no, and only the first is worth repairing.
     */
    fun wasAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    /** Called wherever the senior is actually sent to the settings pages. */
    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
