package com.pup.seenior.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAddAlt1
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
import com.pup.seenior.ui.contacts.InviteScreen
import com.pup.seenior.ui.contacts.SeniorContactsScreen
import com.pup.seenior.ui.home.HomeScreen
import com.pup.seenior.ui.theme.SeniorColors

private enum class SeniorTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    INVITE("Invite", Icons.Filled.PersonAddAlt1),
    CONTACTS("Contacts", Icons.Filled.Contacts),
    PROFILE("Profile", Icons.Filled.Person)
}

@Composable
fun SeniorDashboard() {
    var tab by remember { mutableStateOf(SeniorTab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                SeniorTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = SeniorColors.Green,
                            indicatorColor = SeniorColors.Green,
                            unselectedIconColor = SeniorColors.TextSecondary,
                            unselectedTextColor = SeniorColors.TextSecondary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                SeniorTab.HOME -> HomeScreen()
                SeniorTab.INVITE -> InviteScreen()
                SeniorTab.CONTACTS -> SeniorContactsScreen(onGoToInvite = { tab = SeniorTab.INVITE })
                SeniorTab.PROFILE -> Placeholder("Profile")
            }
        }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name, color = SeniorColors.TextSecondary)
    }
}
