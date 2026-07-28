package com.pup.seenior.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.pup.seenior.ui.family.FamilyColors
import com.pup.seenior.ui.family.FamilyHomeScreen

private enum class FamilyTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    LINK("Link", Icons.Filled.Link),
    ALERTS("Alerts", Icons.Filled.Notifications),
    CONTACTS("Contacts", Icons.Filled.Contacts),
    PROFILE("Profile", Icons.Filled.Person)
}

@Composable
fun FamilyDashboard() {
    var tab by remember { mutableStateOf(FamilyTab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                FamilyTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = FamilyColors.Blue,
                            indicatorColor = FamilyColors.Blue,
                            unselectedIconColor = FamilyColors.TextSecondary,
                            unselectedTextColor = FamilyColors.TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                FamilyTab.HOME -> FamilyHomeScreen(onLinkSenior = { tab = FamilyTab.LINK })
                FamilyTab.LINK -> Placeholder("Link — pairing is done from onboarding")
                FamilyTab.ALERTS -> Placeholder("Alerts")
                FamilyTab.CONTACTS -> Placeholder("Contacts")
                FamilyTab.PROFILE -> Placeholder("Profile")
            }
        }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name, color = FamilyColors.TextSecondary)
    }
}
