package com.pup.seenior.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.ui.onboarding.components.OnboardingHeading
import com.pup.seenior.ui.onboarding.components.OnboardingTopBar
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton
import com.pup.seenior.ui.theme.SeniorColors

private data class PermissionRow(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String
)

private val permissionRows = listOf(
    PermissionRow(Icons.AutoMirrored.Filled.DirectionsRun, "Motion & Activity", "Detects movement to track routine"),
    PermissionRow(Icons.Filled.LocationOn, "Location (Alerts)", "Shared only when alert triggers"),
    PermissionRow(Icons.Filled.Notifications, "Notifications", "Check-in prompts & SOS alerts"),
    PermissionRow(Icons.Filled.BatteryChargingFull, "Battery & screen", "Tracks charging & screen use")
)

private val runtimePermissions: List<String> = buildList {
    if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    onAllGranted: () -> Unit
) {
    var showRationale by remember { mutableStateOf(false) }
    var showDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) onAllGranted() else showDenied = true
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            OnboardingTopBar(currentStep = 4, onBack = onBack)
            OnboardingHeading(
                title = "Allow Permissions",
                subtitle = "SEENior needs these to monitor quietly in the background. All data stays on your phone."
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .border(1.dp, SeniorColors.GreenBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                permissionRows.forEachIndexed { index, row ->
                    PermissionItem(row)
                    if (index != permissionRows.lastIndex) Spacer(modifier = Modifier.padding(top = 20.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Protected under RA 10173 · Data Privacy Act",
                color = SeniorColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            PrimaryPillButton(
                text = "ALLOW PERMISSIONS NOW",
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
                launcher.launch(runtimePermissions.toTypedArray())
            }
        )
    }

    if (showDenied) {
        PermissionDeniedDialog(onClose = { showDenied = false })
    }
}

@Composable
private fun PermissionItem(row: PermissionRow) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier
                .size(48.dp)
                .background(SeniorColors.Green, RoundedCornerShape(14.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = row.icon, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(row.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
            Text(row.description, fontSize = 14.sp, color = SeniorColors.TextSecondary)
        }
    }
}

@Composable
private fun PermissionRationaleDialog(onDeny: () -> Unit, onAllow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDeny,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        icon = { Icon(Icons.Filled.Notifications, contentDescription = null, tint = SeniorColors.Green, modifier = Modifier.size(40.dp)) },
        title = null,
        text = {
            Text(
                text = "Allow SEENior to access your device's activity, notifications, battery usage, and location during emergency alerts to support routine monitoring and emergency assistance?",
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = SeniorColors.TextPrimary
            )
        },
        confirmButton = {
            TextButton(onClick = onAllow) { Text("ALLOW", color = SeniorColors.Green, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDeny) { Text("DENY", color = SeniorColors.Green, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun PermissionDeniedDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        icon = { Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = SeniorColors.Green, modifier = Modifier.size(40.dp)) },
        title = {
            Text(
                "App was denied access",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = SeniorColors.TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                "It is possible the app won't work properly without this restricted permission.",
                fontSize = 15.sp,
                color = SeniorColors.TextPrimary
            )
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("CLOSE", color = SeniorColors.Green, fontWeight = FontWeight.Bold) }
        }
    )
}
