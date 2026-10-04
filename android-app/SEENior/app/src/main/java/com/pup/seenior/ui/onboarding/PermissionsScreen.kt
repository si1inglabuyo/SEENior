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
import com.pup.seenior.sensors.DeviceCapabilities
import com.pup.seenior.ui.LocalOnboardingCopy
import com.pup.seenior.ui.onboarding.components.OnboardingHeading
import com.pup.seenior.ui.onboarding.components.OnboardingTopBar
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton
import com.pup.seenior.ui.theme.SeniorColors

/**
 * Icons only. Titles and descriptions are in [com.pup.seenior.ui.OnboardingStrings]; the
 * two lists are zipped by position, so keep the same order: motion, location, notifications,
 * battery, background, wake, overlay.
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
    // Both location permissions are requested together so Android 12+ shows the Precise/Approximate choice.
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

private val locationPermissions = setOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

/**
 * Whether onboarding may continue. Every permission is required, except the two location
 * ones count as one answer: choosing Approximate returns ACCESS_FINE_LOCATION as denied, and
 * approximate is still enough to place an alert.
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
     * Asked after the runtime permissions, and does not gate onboarding. OEM battery killers
     * sit on top of stock Doze handling, and this exemption is the only defence. Declining
     * still gives a working app, just with escalation that can be delayed.
     */
    /**
     * The last two asks, which are settings pages the senior has to visit. From Android 14 the
     * full-screen one is refused unless they do. Chained one page at a time, and like the
     * battery exemption they don't gate onboarding: alerts still post and escalate.
     */
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onAllGranted() }

    fun requestOverlayThenContinue() {
        if (AlertPermissions.canDrawOverlays(context)) {
            onAllGranted()
            return
        }
        // Some OEM builds lack this settings activity; onboarding must not dead-end.
        runCatching { overlayLauncher.launch(AlertPermissions.overlaySettings(context)) }
            .onFailure { onAllGranted() }
    }

    val fullScreenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { requestOverlayThenContinue() }

    fun requestFullScreenThenContinue() {
        // Marked here, not at the rationale dialog above: this is the first of the two
        // settings-page questions AlertPermissions tracks.
        AlertPermissions.markAsked(context)
        val intent = AlertPermissions.fullScreenIntentSettings(context)
        if (intent == null || AlertPermissions.canUseFullScreenIntent(context)) {
            requestOverlayThenContinue()
            return
        }
        runCatching { fullScreenLauncher.launch(intent) }
            .onFailure { requestOverlayThenContinue() }
    }

    /**
     * Transsion's background-app killer ("Hiber") ignores the stock exemption; 5-minute
     * sampling only held once the per-app "No restrictions" toggle in Phone Master was set.
     * There is no API for it, so this sends the senior to the two Phone Master pages (app
     * power and Auto-start, the latter likely why BootReceiver doesn't fire after a reboot).
     * Skipped on other phones, and never gates onboarding.
     */
    var showManufacturerDialog by remember { mutableStateOf(false) }

    fun isPhoneMasterInstalled(): Boolean =
        runCatching { context.packageManager.getPackageInfo("com.transsion.phonemaster", 0) }.isSuccess

    val autoStartLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { requestFullScreenThenContinue() }

    val appAccelerateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val autoStartIntent = Intent(Intent.ACTION_VIEW).setClassName(
            "com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"
        )
        runCatching { autoStartLauncher.launch(autoStartIntent) }
            .onFailure { requestFullScreenThenContinue() }
    }

    fun requestManufacturerExemptionThenContinue() {
        if (!isPhoneMasterInstalled()) {
            requestFullScreenThenContinue()
            return
        }
        showManufacturerDialog = true
    }

    fun openPhoneMasterAppPower() {
        showManufacturerDialog = false
        val intent = Intent(Intent.ACTION_VIEW).setClassName(
            "com.transsion.phonemaster",
            "com.transsion.phonemaster.appaccelerate.view.AppAccelerateActivity"
        )
        runCatching { appAccelerateLauncher.launch(intent) }.onFailure { requestFullScreenThenContinue() }
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { requestManufacturerExemptionThenContinue() }

    fun requestBatteryExemptionThenContinue() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            requestManufacturerExemptionThenContinue()
            return
        }
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        // Some OEM builds lack this settings activity; don't dead-end.
        runCatching { batteryLauncher.launch(intent) }.onFailure { requestFullScreenThenContinue() }
    }

    /**
     * Says so, once, when the handset has no step counter (for example a tablet). It isn't a
     * permission problem, so "check the permission" can't fix it. The counter is also the
     * witness [com.pup.seenior.sensors.SensorCollectionService] uses to tell a frozen gap from
     * stillness, so such a device has no independent check on inactivity. Asked, not enforced:
     * this is the only point where the choice is still open.
     */
    var showNoStepSensor by remember { mutableStateOf(false) }

    fun warnIfNoStepSensorThenContinue() {
        if (DeviceCapabilities.hasStepCounter(context)) {
            requestBatteryExemptionThenContinue()
            return
        }
        showNoStepSensor = true
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (requiredPermissionsGranted(results)) warnIfNoStepSensorThenContinue() else showDenied = true
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            OnboardingTopBar(currentStep = 4, onBack = onBack)

            // The heading and list scroll, while the top bar and footer button stay pinned, so
            // the button is always reachable. The weight is here because a weighted child in a
            // scrolling Column is measured against unbounded height and throws.
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
                // Recorded before the dialog: what matters is that the senior was asked, so the
                // dashboard's repair pass never second-guesses their answer.
                //
                // AlertPermissions.markAsked() does not belong here. Its grants are asked later in
                // requestFullScreenThenContinue(), reached only if this dialog is granted, so
                // marking it here would permanently skip RepairAlertPermissions for a senior who
                // denied this one.
                LocationPermissionState.markAsked(context)
                launcher.launch(runtimePermissions.toTypedArray())
            }
        )
    }

    if (showDenied) {
        PermissionDeniedDialog(onClose = { showDenied = false })
    }

    if (showNoStepSensor) {
        NoStepSensorDialog(onContinue = {
            showNoStepSensor = false
            requestBatteryExemptionThenContinue()
        })
    }

    if (showManufacturerDialog) {
        ManufacturerExemptionDialog(
            onSkip = {
                showManufacturerDialog = false
                requestFullScreenThenContinue()
            },
            onOpenSettings = { openPhoneMasterAppPower() }
        )
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

/** One button only: the hardware is missing and the senior can't grant it. */
@Composable
private fun NoStepSensorDialog(onContinue: () -> Unit) {
    val copy = LocalOnboardingCopy.current
    AlertDialog(
        // Dismissing is the same decision as continuing -- setup cannot stop here.
        onDismissRequest = onContinue,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.AutoMirrored.Filled.DirectionsRun,
                contentDescription = null,
                tint = SeniorColors.Green,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                copy.noStepSensorTitle,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = SeniorColors.TextPrimary
            )
        },
        text = {
            Text(
                copy.noStepSensorBody,
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = SeniorColors.TextPrimary
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(copy.noStepSensorContinue, color = SeniorColors.Green, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun ManufacturerExemptionDialog(onSkip: () -> Unit, onOpenSettings: () -> Unit) {
    val copy = LocalOnboardingCopy.current
    AlertDialog(
        onDismissRequest = onSkip,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        icon = { Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = SeniorColors.Green, modifier = Modifier.size(40.dp)) },
        title = {
            Text(
                copy.manufacturerDialogTitle,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = SeniorColors.TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                copy.manufacturerDialogBody,
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                color = SeniorColors.TextPrimary
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(copy.manufacturerDialogOpenSettings, color = SeniorColors.Green, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(copy.manufacturerDialogSkip, color = SeniorColors.Green, fontWeight = FontWeight.Bold)
            }
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
