package com.pup.seenior.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.pup.seenior.alerts.AlertPermissions
import com.pup.seenior.location.LocationPermissionState
import com.pup.seenior.sensors.DeviceCapabilities
import com.pup.seenior.ui.alerts.AlertsScreen
import com.pup.seenior.ui.contacts.InviteScreen
import com.pup.seenior.ui.contacts.SeniorContactsScreen
import com.pup.seenior.ui.home.HomeScreen
import com.pup.seenior.ui.home.HomeViewModel
import com.pup.seenior.ui.profile.SeniorProfileScreen
import com.pup.seenior.ui.theme.SeniorColors
import com.pup.seenior.ui.wellness.WellnessPromptScreen
import com.pup.seenior.ui.wellness.WellnessPromptViewModel
import com.pup.seenior.ui.LocalInfoCopy
import com.pup.seenior.ui.LocalOnboardingCopy
import com.pup.seenior.ui.LocalProfileCopy
import com.pup.seenior.ui.InfoStrings
import com.pup.seenior.ui.OnboardingStrings
import com.pup.seenior.ui.ProfileStrings
import com.pup.seenior.ui.SeniorStrings

/**
 * Lets the wellness prompt appear over the lock screen and wake the screen, only while it is
 * showing. Set here, not as `showWhenLocked` in the manifest, which would expose the whole
 * app to anyone holding the locked phone.
 */
@Composable
private fun ShowOverLockScreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()

        // Window flags are the only option on minSdk 26 (the Activity methods arrived in 27).
        if (activity != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                // setTurnScreenOn wakes the screen once and lets it time out; this keeps it on
                // while the prompt counts down.
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
        }
        onDispose {
            if (activity != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    activity.setShowWhenLocked(false)
                    activity.setTurnScreenOn(false)
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    @Suppress("DEPRECATION")
                    activity.window.clearFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                }
            }
        }
    }
}

/** The icon slot every bottom-tab icon is centered in, so the bigger Alerts glyph doesn't shift its label. */
private val TAB_ICON_SLOT = 34.dp
private val TAB_ICON_DEFAULT_SIZE = 24.dp
private val TAB_ICON_ALERTS_SIZE = 32.dp

private enum class SeniorTab(val icon: ImageVector) {
    HOME(Icons.Outlined.Home),
    INVITE(Icons.Outlined.PersonAddAlt1),
    ALERTS(Icons.Filled.NotificationsActive),
    CONTACTS(Icons.Outlined.Contacts),
    PROFILE(Icons.Outlined.Person)
}

/** Looked up here because the label changes with the senior's language. */
private fun SeniorTab.label(copy: SeniorStrings.Copy): String = when (this) {
    SeniorTab.HOME -> copy.tabHome
    SeniorTab.INVITE -> copy.tabInvite
    SeniorTab.ALERTS -> copy.tabAlerts
    SeniorTab.CONTACTS -> copy.tabContacts
    SeniorTab.PROFILE -> copy.tabProfile
}

/**
 * The tabs this senior gets. A senior who lives alone has no use for Invite or Contacts, so
 * they get Home, Alerts and Profile; both screens stay reachable from Profile -> Family
 * contacts. When someone pairs, HomeViewModel.restoreFamilyTabsIfPaired() brings all five
 * tabs back. Alerts is always the middle tab.
 */
private fun tabsFor(livesAlone: Boolean): List<SeniorTab> =
    if (livesAlone) listOf(SeniorTab.HOME, SeniorTab.ALERTS, SeniorTab.PROFILE)
    else SeniorTab.entries

/**
 * Asks for location once on an install that was upgraded rather than onboarded, since
 * [com.pup.seenior.ui.onboarding.PermissionsScreen] only runs during onboarding. Asked at most
 * once, and only if [LocationPermissionState] has no record of the question. Placed after the
 * wellness prompt's early return so a dialog never covers a counting-down alert.
 */
@Composable
private fun RepairLocationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Either answer is final — the record of asking is written before the dialog opens. */ }

    LaunchedEffect(Unit) {
        if (LocationPermissionState.wasAsked(context)) return@LaunchedEffect
        LocationPermissionState.markAsked(context)
        if (LocationPermissionState.hasPrecise(context)) return@LaunchedEffect

        // Both, so Android 12+ offers the Precise/Approximate choice.
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

/**
 * Offers, once, to turn on the two grants that help an alert reach a senior who isn't
 * looking at the phone. Neither is required (alerts still post and escalate), so this asks
 * once and never insists. For installs that onboarded before these were asked for; same idea
 * as [RepairLocationPermission].
 */
@Composable
private fun RepairAlertPermissions(copy: SeniorStrings.Copy) {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Nothing to read back: the grants are re-checked on the next alert, not here. */ }

    LaunchedEffect(Unit) {
        if (AlertPermissions.wasAsked(context)) return@LaunchedEffect
        if (AlertPermissions.allGranted(context)) {
            // Nothing to repair, but record the asking so a later revocation doesn't reopen this.
            AlertPermissions.markAsked(context)
            return@LaunchedEffect
        }
        show = true
    }

    if (!show) return

    AlertDialog(
        onDismissRequest = {
            AlertPermissions.markAsked(context)
            show = false
        },
        title = { Text(copy.reachTitle) },
        text = { Text(copy.reachBody) },
        confirmButton = {
            TextButton(onClick = {
                AlertPermissions.markAsked(context)
                show = false
                // One page at a time; the remaining grant is reachable from the phone's settings.
                val next = AlertPermissions.fullScreenIntentSettings(context)
                    ?.takeIf { !AlertPermissions.canUseFullScreenIntent(context) }
                    ?: AlertPermissions.overlaySettings(context)
                // Some OEM builds lack these pages; don't crash the dashboard.
                runCatching { launcher.launch(next) }
            }) { Text(copy.openSettings) }
        },
        dismissButton = {
            TextButton(onClick = {
                AlertPermissions.markAsked(context)
                show = false
            }) { Text(copy.notNow) }
        }
    )
}

/**
 * Asks, and keeps asking, while monitoring is missing a permission it can't work without.
 *
 * Unlike [RepairLocationPermission] and [RepairAlertPermissions], which offer improvements,
 * this describes something broken: without ACTIVITY_RECOGNITION, notifications or location,
 * detection or alerts are degraded. Onboarding already requires these, so a missing one was
 * lost afterwards. It is not a lock: the dashboard (and SOS) stay reachable behind it.
 */
@Composable
private fun RestoreRequiredPermissions(copy: SeniorStrings.Copy) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(DeviceCapabilities.missingRequiredPermissions(context)) }
    var systemDialogExhausted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val still = DeviceCapabilities.missingRequiredPermissions(context)
        // Nothing was granted, so Android no longer shows the dialog; only the settings page is left.
        systemDialogExhausted = still.size == missing.size
        missing = still
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { missing = DeviceCapabilities.missingRequiredPermissions(context) }

    // Runs only while something is missing. Re-reads because the senior can grant it from
    // Settings, which gives this screen no callback.
    LaunchedEffect(missing.isNotEmpty()) {
        while (missing.isNotEmpty()) {
            delay(2_000)
            missing = DeviceCapabilities.missingRequiredPermissions(context)
        }
    }

    if (missing.isEmpty()) return

    AlertDialog(
        // No dismiss button: this one insists, unlike the other two.
        onDismissRequest = { },
        title = { Text(copy.permissionLostTitle) },
        text = { Text(copy.permissionLostBody) },
        confirmButton = {
            TextButton(onClick = {
                if (systemDialogExhausted) {
                    // Some OEM builds remove this screen; don't crash.
                    runCatching {
                        settingsLauncher.launch(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    }
                } else {
                    permissionLauncher.launch(missing.toTypedArray())
                }
            }) {
                Text(if (systemDialogExhausted) copy.openSettings else copy.permissionLostCta)
            }
        }
    )
}

@Composable
fun SeniorDashboard(onAccountDeleted: () -> Unit) {
    var tab by remember { mutableStateOf(SeniorTab.HOME) }
    val homeViewModel: HomeViewModel = viewModel()
    val promptViewModel: WellnessPromptViewModel = viewModel()

    LaunchedEffect(Unit) { homeViewModel.start() }

    // An unanswered alert replaces the whole dashboard, so the senior can't wander off the
    // screen that needs an answer.
    val alert = homeViewModel.activeAlert
    if (alert != null) {
        ShowOverLockScreen()
        // Back would drop the senior onto the launcher with the alert still counting down.
        // Home can't be intercepted. Walking away doesn't stop the chain either way.
        BackHandler(enabled = true) { }
        WellnessPromptScreen(
            alert = alert,
            seniorFirstName = homeViewModel.firstName,
            language = homeViewModel.language,
            willAlertContacts = homeViewModel.willAlertContacts,
            willAlertContactsKnown = homeViewModel.willAlertContactsKnown,
            barangay = homeViewModel.barangay,
            viewModel = promptViewModel,
            onFinished = {
                homeViewModel.onAlertAnswered()
                homeViewModel.refreshBattery()
            }
        )
        return
    }

    val copy = SeniorStrings.forLanguage(homeViewModel.language)
    // Screens behind the tabs read their copy from these, not a language parameter each.
    // OnboardingStrings is provided too because Edit profile reuses the sign-up labels.
    val profileCopy = ProfileStrings.forLanguage(homeViewModel.language)
    val formCopy = OnboardingStrings.forLanguage(homeViewModel.language)
    val infoCopy = InfoStrings.forLanguage(homeViewModel.language)

    // First of the three: the other two offer improvements, this one reports a fault.
    RestoreRequiredPermissions(copy)
    RepairLocationPermission()
    RepairAlertPermissions(copy)

    val tabs = tabsFor(homeViewModel.livesAlone)

    // The tab list can shrink under a selected tab as the senior's data loads. Derived, not
    // written back, to avoid recomposition loops. Home is always present.
    val activeTab = if (tab in tabs) tab else SeniorTab.HOME

    CompositionLocalProvider(
        LocalProfileCopy provides profileCopy,
        LocalOnboardingCopy provides formCopy,
        LocalInfoCopy provides infoCopy
    ) {
    // Whether Alerts' icon has the red dot: the same fact Home's status card reads.
    val hasOpenAlert = homeViewModel.helpDelivery != null

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEach { entry ->
                    val highlighted = entry == SeniorTab.ALERTS
                    val alertsSelected = highlighted && activeTab == entry
                    NavigationBarItem(
                        selected = activeTab == entry,
                        onClick = { tab = entry },
                        icon = {
                            // Every icon sits in a fixed box so a bigger Alerts glyph doesn't move its label.
                            Box(modifier = Modifier.size(TAB_ICON_SLOT), contentAlignment = Alignment.Center) {
                                if (highlighted) {
                                    // Bigger than the others and always green (darker when
                                    // pressed or selected), so it catches the eye first.
                                    Icon(
                                        entry.icon,
                                        contentDescription = entry.label(copy),
                                        tint = if (alertsSelected) SeniorColors.GreenDark else SeniorColors.Green,
                                        modifier = Modifier.size(TAB_ICON_ALERTS_SIZE)
                                    )
                                    if (hasOpenAlert) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(9.dp)
                                                .background(Color(0xFFC62828), CircleShape)
                                        )
                                    }
                                } else {
                                    Icon(
                                        entry.icon,
                                        contentDescription = entry.label(copy),
                                        modifier = Modifier.size(TAB_ICON_DEFAULT_SIZE)
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                entry.label(copy),
                                fontWeight = FontWeight.Bold,
                                fontSize = if (highlighted) 13.sp else 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.Black,
                            // Alerts tints itself, so no pill behind it (two signals would be redundant).
                            indicatorColor = if (highlighted) Color.Transparent else SeniorColors.Green,
                            unselectedIconColor = SeniorColors.Green,
                            unselectedTextColor = Color.Black
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (activeTab) {
                SeniorTab.HOME -> HomeScreen(homeViewModel)
                SeniorTab.INVITE -> InviteScreen()
                SeniorTab.ALERTS -> AlertsScreen(homeViewModel)
                SeniorTab.CONTACTS -> SeniorContactsScreen(
                    onGoToInvite = { tab = SeniorTab.INVITE },
                    inviteActionLabel = profileCopy.inviteTabLabel
                )
                SeniorTab.PROFILE -> SeniorProfileScreen(onAccountDeleted = onAccountDeleted)
            }
        }
    }
    }
}
