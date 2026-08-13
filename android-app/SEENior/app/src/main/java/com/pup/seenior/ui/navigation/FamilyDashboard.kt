package com.pup.seenior.ui.navigation

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.pup.seenior.ui.family.FamilyPairingViewModel
import com.pup.seenior.ui.family.FamilyProfileScreen
import com.pup.seenior.ui.family.FamilySeniorsViewModel
import com.pup.seenior.ui.family.LinkScreen
import com.pup.seenior.ui.family.MonitoringLimitCard
import com.pup.seenior.session.SessionState

private enum class FamilyTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    LINK("Link", Icons.Outlined.Link),
    ALERTS("Alerts", Icons.Outlined.Notifications),
    CONTACTS("Contacts", Icons.Outlined.Contacts),
    PROFILE("Profile", Icons.Outlined.Person)
}

@Composable
fun FamilyDashboard(onLoggedOut: () -> Unit) {
    var tab by remember { mutableStateOf(FamilyTab.HOME) }
    // Shared across all tabs so a link/unlink on one tab is reflected on the others
    // without a re-fetch.
    val seniorsViewModel: FamilySeniorsViewModel = viewModel()
    val alertsViewModel: FamilyAlertsViewModel = viewModel()
    // Re-fetch on every resume and on every tab change. The senior can unlink from their own
    // phone at any time and there is no push channel to tell us, so a once-per-login fetch
    // leaves this list frozen at whatever it was when the session started. The senior's own
    // Contacts screen gets this for free — its LaunchedEffect lives inside the screen, which
    // leaves composition on each tab switch. This one is hoisted to the dashboard, which never does.
    LifecycleResumeEffect(tab) {
        seniorsViewModel.refresh()
        onPauseOrDispose { }
    }

    // The token expires server-side and there is no refresh flow, so any 401 raised by a tab
    // means this login is finished. Leave the dashboard instead of sitting here rendering
    // "server error 401" on every tab — Log Out in the Profile tab used to be the only way out.
    LaunchedEffect(SessionState.expired) {
        if (SessionState.expired) {
            SessionState.consume()
            onLoggedOut()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                FamilyTab.entries.forEach { entry ->
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
                    onLinkSenior = { tab = FamilyTab.LINK },
                    onSeeAllSeniors = { tab = FamilyTab.CONTACTS },
                    onSeeAllAlerts = { tab = FamilyTab.ALERTS },
                    // "View" on the Home popup must open the alert it was advertising, not
                    // whatever the Alerts tab would otherwise settle on by itself.
                    onViewAlert = { syncId ->
                        alertsViewModel.focusAlert(syncId)
                        tab = FamilyTab.ALERTS
                    }
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
                FamilyTab.PROFILE -> FamilyProfileScreen(onLoggedOut = onLoggedOut)
            }
        }
    }
}

/**
 * The pairing flow (code entry -> relationship -> paired), reachable any time to link up to
 * MAX_LINKED_SENIORS seniors. Reusing the same account for senior #2/#3 is handled by
 * FamilyPairingViewModel.pair() sending the existing token.
 */
@Composable
private fun LinkTab(
    seniorsViewModel: FamilySeniorsViewModel,
    onManageContacts: () -> Unit,
    onDone: () -> Unit
) {
    if (!seniorsViewModel.canLinkMore) {
        Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
            BlueHeader(Icons.Outlined.Link, "Link")
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
                    Text("Manage linked seniors", color = FamilyColors.Blue, fontWeight = FontWeight.Bold)
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
