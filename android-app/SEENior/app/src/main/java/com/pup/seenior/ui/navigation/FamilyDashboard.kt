package com.pup.seenior.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pup.seenior.alerts.PendingAlertNavigation
import com.pup.seenior.network.PushTokenRegistrar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.ui.family.BlueHeader
import com.pup.seenior.ui.family.ConnectedScreen
import com.pup.seenior.ui.family.FamilyAlertsScreen
import com.pup.seenior.ui.family.FamilyAlertsViewModel
import com.pup.seenior.ui.family.FamilyColors
import com.pup.seenior.ui.family.FamilyContactsScreen
import com.pup.seenior.ui.family.FamilyHomeScreen
import com.pup.seenior.ui.family.FamilyInfoStrings
import com.pup.seenior.ui.family.FamilyPairingViewModel
import com.pup.seenior.ui.family.FamilyProfileScreen
import com.pup.seenior.ui.family.FamilyProfileViewModel
import com.pup.seenior.ui.family.FamilySeniorsViewModel
import com.pup.seenior.ui.family.FamilyStrings
import com.pup.seenior.ui.family.LinkScreen
import com.pup.seenior.ui.family.LocalFamilyCopy
import com.pup.seenior.ui.family.LocalFamilyInfoCopy
import com.pup.seenior.ui.family.MonitoringLimitCard
import com.pup.seenior.session.SessionState

private enum class FamilyTab(val icon: ImageVector) {
    HOME(Icons.Outlined.Home),
    LINK(Icons.Outlined.Link),
    ALERTS(Icons.Outlined.Notifications),
    CONTACTS(Icons.Outlined.Contacts),
    PROFILE(Icons.Outlined.Person)
}

/** Resolves a tab's translated label from the account's language, like SeniorTab.label(copy). */
private fun FamilyTab.label(copy: FamilyStrings.Copy): String = when (this) {
    FamilyTab.HOME -> copy.tabHome
    FamilyTab.LINK -> copy.tabLink
    FamilyTab.ALERTS -> copy.tabAlerts
    FamilyTab.CONTACTS -> copy.tabContacts
    FamilyTab.PROFILE -> copy.tabProfile
}

@Composable
fun FamilyDashboard(onLoggedOut: () -> Unit) {
    var tab by remember { mutableStateOf(FamilyTab.HOME) }
    // Shared across tabs so a link or unlink on one is reflected on the others without a re-fetch.
    val seniorsViewModel: FamilySeniorsViewModel = viewModel()
    val alertsViewModel: FamilyAlertsViewModel = viewModel()
    // Hoisted so the account's language_preference is fetched once and every tab reads the
    // same instance. Home's LaunchedEffect below fires the fetch.
    val profileViewModel: FamilyProfileViewModel = viewModel()
    LaunchedEffect(Unit) { profileViewModel.refresh() }
    val copy = FamilyStrings.forLanguage(profileViewModel.language)
    val infoCopy = FamilyInfoStrings.forLanguage(profileViewModel.language)
    // Re-fetch on every resume and tab change. A senior can unlink from their own phone at any
    // time with no push to tell us, and this list is hoisted to the dashboard, which (unlike a
    // screen's own LaunchedEffect) never leaves composition.
    LifecycleResumeEffect(tab) {
        seniorsViewModel.refresh()
        onPauseOrDispose { }
    }

    // The token expires server-side and there is no refresh flow, so a 401 means this login is
    // finished. Leave instead of showing "server error 401" on every tab.
    LaunchedEffect(SessionState.expired) {
        if (SessionState.expired) {
            SessionState.consume()
            onLoggedOut()
        }
    }

    val context = LocalContext.current

    // Android 13+ drops notifications until this is granted, and the only existing request is
    // in the senior onboarding flow. Asked here as the first screen that shows alerts. Guarded
    // by SDK version because the permission doesn't exist below 33 and reads as denied.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Declining is allowed; polling still works, it is just slower and app-only. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Re-asserted on every dashboard entry rather than at each login path, which also repairs a rotated token.
        PushTokenRegistrar.syncToken(context)
    }

    // A tapped notification names the alert to open, since "newest open" can be a different alert.
    LaunchedEffect(PendingAlertNavigation.alertSyncId) {
        PendingAlertNavigation.consume()?.let { syncId ->
            alertsViewModel.focusAlert(syncId)
            tab = FamilyTab.ALERTS
        }
    }

    CompositionLocalProvider(LocalFamilyCopy provides copy, LocalFamilyInfoCopy provides infoCopy) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                FamilyTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
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
                            indicatorColor = FamilyColors.Blue,
                            unselectedIconColor = FamilyColors.Blue,
                            unselectedTextColor = Color.Black
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                FamilyTab.HOME -> FamilyHomeScreen(
                    seniorsViewModel = seniorsViewModel,
                    profileViewModel = profileViewModel,
                    onLinkSenior = { tab = FamilyTab.LINK },
                    onSeeAllSeniors = { tab = FamilyTab.CONTACTS },
                    onSeeAllAlerts = { tab = FamilyTab.ALERTS }
                )
                FamilyTab.LINK -> LinkTab(
                    seniorsViewModel = seniorsViewModel,
                    onManageContacts = { tab = FamilyTab.CONTACTS },
                    onDone = { tab = FamilyTab.HOME }
                )
                FamilyTab.ALERTS -> FamilyAlertsScreen(
                    viewModel = alertsViewModel,
                    contacts = seniorsViewModel.contacts,
                    seniorsLoading = seniorsViewModel.isLoading,
                    seniorsLoadFailed = seniorsViewModel.loadFailed,
                    onRetrySeniors = { seniorsViewModel.refresh() }
                )
                FamilyTab.CONTACTS -> FamilyContactsScreen(seniorsViewModel, onLinkSenior = { tab = FamilyTab.LINK })
                FamilyTab.PROFILE -> FamilyProfileScreen(viewModel = profileViewModel, onLoggedOut = onLoggedOut)
            }
        }
    }
    }
}

/**
 * The pairing flow (code entry, relationship, paired), reachable any time to link up to
 * MAX_LINKED_SENIORS seniors. FamilyPairingViewModel.pair() reuses the existing token.
 */
@Composable
private fun LinkTab(
    seniorsViewModel: FamilySeniorsViewModel,
    onManageContacts: () -> Unit,
    onDone: () -> Unit
) {
    if (!seniorsViewModel.canLinkMore) {
        val copy = LocalFamilyCopy.current
        Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
            BlueHeader(Icons.Outlined.Link, copy.tabLink)
            Column(modifier = Modifier.padding(24.dp)) {
                MonitoringLimitCard()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(50.dp)
                        .clickable(onClick = onManageContacts),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(copy.manageLinkedSeniors, color = FamilyColors.Blue, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val pairingViewModel: FamilyPairingViewModel = viewModel()
    var verified by remember { mutableStateOf(false) }

    if (!verified) {
        LinkScreen(pairingViewModel, onVerified = { verified = true })
    } else {
        ConnectedScreen(
            viewModel = pairingViewModel,
            onGoHome = {
                seniorsViewModel.refresh()
                pairingViewModel.resetForNewLink()
                onDone()
            },
            onAddAnother = if (seniorsViewModel.canLinkMore) {
                {
                    seniorsViewModel.refresh()
                    pairingViewModel.resetForNewLink()
                    verified = false
                }
            } else null
        )
    }
}
