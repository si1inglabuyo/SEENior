package com.pup.seenior.alerts

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * The two grants that decide whether an alert can put itself in front of the senior.
 *
 * Both are "special" permissions: neither is ever granted by a runtime dialog, only by the senior
 * finding a specific settings page. Declaring them in the manifest does nothing at all, which is
 * how this went unnoticed for so long — on 2026-09-04 a fall alert was refused both at 13:04:04
 * while the app already carried the manifest line for the full-screen one. The prompt had never
 * once taken over the screen by itself; every time it appeared to, somebody had picked up the
 * phone and tapped the banner.
 *
 * Neither is required for the system to work. An alert with neither still posts a notification,
 * still counts down and still escalates on time — the senior is simply less likely to see it in
 * time to answer, which is the whole point of asking.
 */
object AlertPermissions {

    private const val PREFS = "alert_permissions"
    private const val KEY_ASKED = "asked"

    /**
     * Whether an alert may take over a locked or dark screen.
     *
     * Granted on install below Android 14. From 14 it is reserved for calling and alarm apps and
     * has to be turned on by hand, so this must be checked rather than assumed.
     */
    fun canUseFullScreenIntent(context: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) true
        else (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .canUseFullScreenIntent()

    /**
     * Whether the app may raise the prompt while another app is in the foreground.
     *
     * Doubles as the background-activity-start exemption: from Android 10 an app in the
     * background cannot launch an Activity at all without it, whatever else it holds.
     */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** True once both grants are in place and there is nothing left to ask for. */
    fun allGranted(context: Context): Boolean =
        canUseFullScreenIntent(context) && canDrawOverlays(context)

    /**
     * The settings page for the full-screen grant, or null below Android 14 where there is
     * nothing to open because the permission is already held.
     */
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
     * Whether this install has ever put these two questions to the senior.
     *
     * Mirrors [com.pup.seenior.location.LocationPermissionState] and exists for the same reason:
     * an install that onboarded before these were asked for looks identical to one whose senior
     * considered them and said no. Only the first is worth repairing, and without a record of the
     * asking the two cannot be told apart.
     */
    fun wasAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    /** Called wherever the senior is actually sent to the settings pages. */
    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
