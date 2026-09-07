package com.pup.seenior.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.alerts.AlertPermissions
import com.pup.seenior.location.LocationPermissionState
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
 * Lets the wellness prompt appear over the keyguard and wake the screen, for as long as it is on
 * screen and no longer.
 *
 * Set here rather than as `showWhenLocked` in the manifest deliberately: that would put the whole
 * app — the senior's name, contacts and alert history — in front of anyone who picks up the
 * locked phone. An unanswered alert is worth bypassing the lock screen for; the Contacts tab is
 * not.
 */
@Composable
private fun ShowOverLockScreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()

        // The window flags are the only mechanism that exists on this project's minSdk (26);
        // the Activity methods replaced them in 27. Without the older path a fall detected
        // while the phone slept would launch the prompt behind a screen that never lit up.
        if (activity != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                // Not covered by setTurnScreenOn, which wakes the screen once and then lets it
                // sleep again on the normal timeout with the prompt still up and still counting
                // down. The legacy branch below has always set this; the modern one did not.
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

private enum class SeniorTab(val icon: ImageVector) {
    HOME(Icons.Outlined.Home),
    INVITE(Icons.Outlined.PersonAddAlt1),
    CONTACTS(Icons.Outlined.Contacts),
    PROFILE(Icons.Outlined.Person)
}

/** The label is looked up rather than held on the entry: it changes with the senior's chosen
 *  language, and an enum constant is created once per process. */
private fun SeniorTab.label(copy: SeniorStrings.Copy): String = when (this) {
    SeniorTab.HOME -> copy.tabHome
    SeniorTab.INVITE -> copy.tabInvite
    SeniorTab.CONTACTS -> copy.tabContacts
    SeniorTab.PROFILE -> copy.tabProfile
}

/**
 * The tabs this senior actually gets.
 *
 * A senior who told us at sign-up that they live alone has no use for Invite (a code for
 * nobody) or Contacts (a list that stays empty) — two of their four tabs would be dead
 * weight on a screen designed to be scanned quickly. They get Home and Profile.
 *
 * Nothing is deleted: both screens still exist and are reachable from Profile → Family
 * contacts, so a senior whose situation changes can pair without reinstalling anything.
 * And the moment someone does pair, HomeViewModel.restoreFamilyTabsIfPaired() flips the
 * stored answer and all four tabs come back on their own.
 */
private fun tabsFor(livesAlone: Boolean): List<SeniorTab> =
    if (livesAlone) listOf(SeniorTab.HOME, SeniorTab.PROFILE)
    else SeniorTab.entries

/**
 * Asks for location once on an install that was upgraded rather than onboarded.
 *
 * [com.pup.seenior.ui.onboarding.PermissionsScreen] runs only during onboarding, so a senior who
 * set the app up before it captured an alert's location is never asked for it — installing a new
 * APK does not re-open that screen. Their alerts then arrive with no cluster and the family's map
 * silently falls back to the registered address, with nothing on any screen to explain why.
 *
 * Asked at most once, and only where [LocationPermissionState] holds no record of the question
 * ever being put — so a senior who was asked and chose Approximate is left alone. Nagging on
 * every launch would be a poor trade for a map pin in an app whose whole promise is to sit
 * quietly, and nothing in the escalation chain depends on the answer.
 *
 * Deliberately placed after the wellness prompt's early return: a permission dialog must never
 * appear on top of an alert that is counting down for an answer.
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

        // Both, so Android 12+ offers the Precise/Approximate choice rather than silently
        // treating this as a coarse-only request.
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

/**
 * Offers, once, to turn on the two grants that let an alert reach a senior who is not already
 * looking at the phone.
 *
 * Neither can be granted from inside the app — both need the senior to visit a settings page —
 * and neither is required for the system to work: an alert without them still posts, still counts
 * down and still escalates on time. What they buy is the senior seeing it in time to answer, so
 * this asks rather than insists, and never asks twice.
 *
 * Exists for installs that onboarded before these were asked for. Same shape and same reasoning
 * as [RepairLocationPermission] directly above.
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
            // Nothing to repair, but record the asking anyway so a later revocation does not
            // reopen this dialog on a senior who has already dealt with it once.
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
                // One page at a time. Sending the senior straight on to the second would look
                // like the first had failed; the repair pass will not fire again, and the
                // remaining grant is reachable from the phone's own settings.
                val next = AlertPermissions.fullScreenIntentSettings(context)
                    ?.takeIf { !AlertPermissions.canUseFullScreenIntent(context) }
                    ?: AlertPermissions.overlaySettings(context)
                // Some OEM builds ship without one of these pages. A missing settings screen
                // must not crash the dashboard.
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

@Composable
fun SeniorDashboard(onAccountDeleted: () -> Unit) {
    var tab by remember { mutableStateOf(SeniorTab.HOME) }
    val homeViewModel: HomeViewModel = viewModel()
    val promptViewModel: WellnessPromptViewModel = viewModel()

    LaunchedEffect(Unit) { homeViewModel.start() }

    // An unanswered alert replaces the whole dashboard, bottom navigation included. Leaving the
    // tabs reachable would let the senior wander off the one screen that needs an answer, and
    // the alert would still be counting down unseen behind them.
    val alert = homeViewModel.activeAlert
    if (alert != null) {
        ShowOverLockScreen()
        // Back would otherwise drop the senior onto the launcher with the alert still open and
        // still counting down — the tabs are hidden just below for the same reason. Home cannot
        // be intercepted by any app at any permission level, so this closes the one exit Android
        // does let us hold. Walking away does not stop the chain either way.
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
    // Everything behind the tabs reads its own copy from these two, rather than being handed a
    // language parameter screen by screen. OnboardingStrings is provided here as well because
    // Edit profile reuses the sign-up form's field labels — one translation, two places.
    val profileCopy = ProfileStrings.forLanguage(homeViewModel.language)
    val formCopy = OnboardingStrings.forLanguage(homeViewModel.language)
    val infoCopy = InfoStrings.forLanguage(homeViewModel.language)

    RepairLocationPermission()
    RepairAlertPermissions(copy)

    val tabs = tabsFor(homeViewModel.livesAlone)

    // The senior's own data arrives asynchronously, so the tab list can shrink under a tab
    // that is already selected — pairing flips it the other way too. Derived rather than
    // written back into `tab`: assigning state during composition is how recomposition
    // loops start, and there is nothing to persist here. Home is always present, so this
    // always resolves to something in the bar.
    val activeTab = if (tab in tabs) tab else SeniorTab.HOME

    CompositionLocalProvider(
        LocalProfileCopy provides profileCopy,
        LocalOnboardingCopy provides formCopy,
        LocalInfoCopy provides infoCopy
    ) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEach { entry ->
                    NavigationBarItem(
                        selected = activeTab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label(copy)) },
                        label = {
                            Text(
                                entry.label(copy),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.Black,
                            indicatorColor = SeniorColors.Green,
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
