package com.pup.seenior.ui.navigation

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.pup.seenior.ui.contacts.InviteScreen
import com.pup.seenior.ui.contacts.SeniorContactsScreen
import com.pup.seenior.ui.home.HomeScreen
import com.pup.seenior.ui.home.HomeViewModel
import com.pup.seenior.ui.profile.SeniorProfileScreen
import com.pup.seenior.ui.theme.SeniorColors
import com.pup.seenior.ui.wellness.WellnessPromptScreen
import com.pup.seenior.ui.wellness.WellnessPromptViewModel

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

private enum class SeniorTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    INVITE("Invite", Icons.Outlined.PersonAddAlt1),
    CONTACTS("Contacts", Icons.Outlined.Contacts),
    PROFILE("Profile", Icons.Outlined.Person)
}

@Composable
fun SeniorDashboard() {
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
        WellnessPromptScreen(
            alert = alert,
            seniorFirstName = homeViewModel.firstName,
            language = homeViewModel.language,
            willAlertContacts = homeViewModel.willAlertContacts,
            barangay = homeViewModel.barangay,
            viewModel = promptViewModel,
            onFinished = {
                homeViewModel.onAlertAnswered()
                homeViewModel.refreshBattery()
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                SeniorTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = {
                            Text(
                                entry.label,
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
            when (tab) {
                SeniorTab.HOME -> HomeScreen(homeViewModel)
                SeniorTab.INVITE -> InviteScreen()
                SeniorTab.CONTACTS -> SeniorContactsScreen(onGoToInvite = { tab = SeniorTab.INVITE })
                SeniorTab.PROFILE -> SeniorProfileScreen()
            }
        }
    }
}
