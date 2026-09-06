package com.pup.seenior.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pup.seenior.alerts.AlertPermissions
import com.pup.seenior.location.LocationPermissionState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.ui.LocalOnboardingCopy
import com.pup.seenior.ui.onboarding.components.OnboardingHeading
import com.pup.seenior.ui.onboarding.components.OnboardingTopBar
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton
import com.pup.seenior.ui.theme.SeniorColors

/**
 * Icons only. The title and description of each row are translated copy and live in
 * [com.pup.seenior.ui.OnboardingStrings]; this list is zipped with that one by position, so the
 * two must stay in the same order — motion, location, notifications, battery, background, wake,
 * overlay.
 */
private val permissionIcons = listOf(
    Icons.AutoMirrored.Filled.DirectionsRun,
    Icons.Filled.LocationOn,
    Icons.Filled.Notifications,
    Icons.Filled.BatteryChargingFull,
    Icons.Filled.Alarm,
    Icons.Filled.ScreenLockPortrait,
    Icons.Filled.Layers
)

private val runtimePermissions: List<String> = buildList {
    if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
    // Both location permissions are requested together so that Android 12+ shows the senior the
    // Precise/Approximate choice at all. Which one they pick is up to them -- see
    // [requiredPermissionsGranted], which accepts either.
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

private val locationPermissions = setOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

/**
 * Whether onboarding may continue.
 *
 * Every permission is required except that the two location ones count as *one* answer. Android
 * 12+ offers "Precise" or "Approximate" in a single dialog, and choosing Approximate returns
 * ACCESS_FINE_LOCATION as denied. Requiring all of them would therefore trap a senior who
 * answered the dialog perfectly reasonably on a screen they cannot skip -- and approximate
 * location is still enough to place an alert, just to a wider area.
 */
private fun requiredPermissionsGranted(results: Map<String, Boolean>): Boolean {
    val location = results.filterKeys { it in locationPermissions }
    val everythingElse = results.filterKeys { it !in locationPermissions }
    return everythingElse.values.all { it } && (location.isEmpty() || location.values.any { it })
}

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    onAllGranted: () -> Unit
) {
    val copy = LocalOnboardingCopy.current
    var showRationale by remember { mutableStateOf(false) }
    var showDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current

    /**
     * Asked after the runtime permissions, and deliberately NOT gating onboarding on the answer.
     *
     * Exact alarms already survive stock Android's Doze, but OEM skins (XOS on Infinix, MIUI,
     * EMUI) run their own battery killers on top, and this exemption is the only defence against
     * those. A senior who declines still gets a working app — just one whose escalation can be
     * delayed by their manufacturer — so refusing must not trap them on this screen.
     */
    /**
     * The last two asks, and the two the app cannot make for itself.
     *
     * Neither is a runtime permission: both are settings pages the senior has to visit, and from
     * Android 14 the full-screen one is refused outright unless they do. Measured on the pilot
     * handset 2026-09-04 — a fall alert had both refused at 13:04:04 with the manifest lines
     * already in place, so the prompt had never once taken over the screen on its own.
     *
     * Chained one page at a time and, like the battery exemption below, never gating onboarding
     * on the answer. A senior who declines still gets a working app: the alert still posts, still
     * counts down and still escalates. They are simply likelier to miss it, and trapping them on
     * this screen over a settings toggle would be the worse outcome.
     */
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onAllGranted() }

    fun requestOverlayThenContinue() {
        if (AlertPermissions.canDrawOverlays(context)) {
            onAllGranted()
            return
        }
        // Some OEM builds ship without this settings activity; onboarding must never dead-end
        // because a manufacturer removed a screen.
        runCatching { overlayLauncher.launch(AlertPermissions.overlaySettings(context)) }
            .onFailure { onAllGranted() }
    }

    val fullScreenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { requestOverlayThenContinue() }

    fun requestFullScreenThenContinue() {
        // Marked here, not at the rationale dialog several steps up: this is the first of the
        // two settings-page questions AlertPermissions tracks, and only reaching here means the
        // senior actually got to them (see the comment on that removed call for why).
        AlertPermissions.markAsked(context)
        val intent = AlertPermissions.fullScreenIntentSettings(context)
        if (intent == null || AlertPermissions.canUseFullScreenIntent(context)) {
            requestOverlayThenContinue()
            return
        }
        runCatching { fullScreenLauncher.launch(intent) }
            .onFailure { requestOverlayThenContinue() }
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { requestFullScreenThenContinue() }

    fun requestBatteryExemptionThenContinue() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            requestFullScreenThenContinue()
            return
        }
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        // Some OEM builds ship without this settings activity; onboarding must not dead-end
        // because a manufacturer removed a screen.
        runCatching { batteryLauncher.launch(intent) }.onFailure { requestFullScreenThenContinue() }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (requiredPermissionsGranted(results)) requestBatteryExemptionThenContinue() else showDenied = true
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            OnboardingTopBar(currentStep = 4, onBack = onBack)

            // Five permissions, each with a sentence of justification, is more than a short
            // display holds — and this is the one screen a senior cannot skip past, so the
            // button below must stay reachable. The heading and the list scroll; the top bar
            // keeps its back arrow and the footer keeps the button, both pinned.
            //
            // The weight lives here now rather than on a spacer: a weighted child inside a
            // scrolling Column is measured against an unbounded height and throws.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                OnboardingHeading(
                    title = copy.permissionsTitle,
                    subtitle = copy.permissionsSubtitle
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .border(1.dp, SeniorColors.GreenBorder, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    copy.permissionRows.forEachIndexed { index, row ->
                        PermissionItem(permissionIcons[index], row.first, row.second)
                        if (index != copy.permissionRows.lastIndex) Spacer(modifier = Modifier.padding(top = 20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                text = copy.permissionsPrivacyNote,
                color = SeniorColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            PrimaryPillButton(
                text = copy.permissionsCta,
                onClick = { showRationale = true },
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }

    if (showRationale) {
        PermissionRationaleDialog(
            onDeny = { showRationale = false },
            onAllow = {
                showRationale = false
                // Recorded before the dialog, not after: what matters is that the senior was
                // put in front of the question at all, so that the dashboard's repair pass
                // never second-guesses an answer they already gave.
                //
                // AlertPermissions.markAsked() does NOT belong here too, despite looking
                // symmetric to the line above -- that was the actual bug once. Its two grants
                // (full-screen intent, overlay) are separate settings-page questions, asked
                // several steps further down this chain in requestFullScreenThenContinue(),
                // reached only if the runtime dialog below is actually granted. Marking it here
                // meant a senior who denied the runtime dialog -- and so never reached those two
                // settings pages at all -- still had AlertPermissions.wasAsked() return true
                // forever, permanently skipping RepairAlertPermissions on the dashboard for
                // exactly the senior it existed to catch. It is marked where it belongs, in
                // requestFullScreenThenContinue() below.
                LocationPermissionState.markAsked(context)
                launcher.launch(runtimePermissions.toTypedArray())
            }
        )
    }

    if (showDenied) {
        PermissionDeniedDialog(onClose = { showDenied = false })
    }
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier
                .size(48.dp)
                .background(SeniorColors.Green, RoundedCornerShape(14.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
            Text(description, fontSize = 14.sp, color = SeniorColors.TextSecondary)
        }
    }
}

@Composable
private fun PermissionRationaleDialog(onDeny: () -> Unit, onAllow: () -> Unit) {
    val copy = LocalOnboardingCopy.current
    AlertDialog(
        onDismissRequest = onDeny,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        icon = { Icon(Icons.Filled.Notifications, contentDescription = null, tint = SeniorColors.Green, modifier = Modifier.size(40.dp)) },
        title = null,
        text = {
            Text(
                text = copy.permissionsDialogBody,
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = SeniorColors.TextPrimary
            )
        },
        confirmButton = {
            TextButton(onClick = onAllow) { Text(copy.allow, color = SeniorColors.Green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDeny) { Text(copy.deny, color = SeniorColors.Green, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun PermissionDeniedDialog(onClose: () -> Unit) {
    val copy = LocalOnboardingCopy.current
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        icon = { Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = SeniorColors.Green, modifier = Modifier.size(40.dp)) },
        title = {
            Text(
                copy.deniedTitle,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = SeniorColors.TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                copy.deniedBody,
                fontSize = 15.sp,
                color = SeniorColors.TextPrimary
            )
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(copy.close, color = SeniorColors.Green, fontWeight = FontWeight.Bold) }
        }
    )
}
