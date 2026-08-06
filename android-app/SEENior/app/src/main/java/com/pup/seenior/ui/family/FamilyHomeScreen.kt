package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.network.dto.ContactDto

/**
 * Family landing screen (designs/family_contact/home_screen_with_linked_senior). Shows every
 * linked senior (up to MAX_LINKED_SENIORS) with their three status tiles (risk / alerts today /
 * battery) and the recent alerts feed. Tile values and alerts are placeholders until the
 * alert-sync pipeline feeds them.
 */
@Composable
fun FamilyHomeScreen(viewModel: FamilySeniorsViewModel, onLinkSenior: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FamilyColors.HeaderBlue)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SEENior ", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Family", color = Color.White, fontSize = 16.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text("Hi there,", color = FamilyColors.Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
            Text("You're all set today", color = FamilyColors.TextSecondary, fontSize = 15.sp)

            Text("MY SENIORS", color = FamilyColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))

            // Loading and failure are both checked before the empty case — rendering either as
            // EmptyLinkCard told the user "no one linked yet" when their seniors were still
            // linked, just slow to fetch or unreachable.
            if (viewModel.isLoading) {
                LoadingCard("Loading your seniors…")
            } else if (viewModel.loadFailed) {
                CouldNotLoadCard(
                    title = "Could not load your seniors",
                    message = viewModel.error ?: "Could not reach the server.",
                    reassurance = "They are still linked to your account.",
                    onRetry = { viewModel.refresh() }
                )
            } else if (viewModel.contacts.isEmpty()) {
                EmptyLinkCard(onLinkSenior)
            } else {
                viewModel.contacts.forEach { contact ->
                    SeniorCard(contact)
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (!viewModel.loadFailed && !viewModel.isLoading) {
                Text("RECENT ALERTS", color = FamilyColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
                NoAlertsCard()
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SeniorCard(contact: ContactDto) {
    val senior = contact.senior
    val seniorName = "${senior.firstName} ${senior.lastName}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FamilyColors.BlueBorder, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(FamilyColors.Blue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(seniorName), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    seniorName,
                    color = FamilyColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    contact.relationshipLabel?.replaceFirstChar { it.uppercase() } ?: "Family",
                    color = FamilyColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.size(8.dp))
            Row(
                modifier = Modifier.background(Color(0xFFE7F5E6), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(FamilyColors.SuccessGreen, CircleShape))
                Text("Online", color = FamilyColors.SuccessGreen, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }

        // Status tiles — placeholder values until the alert/status sync pipeline provides real data.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(Icons.Filled.MonitorHeart, "—", "Risk Level", Modifier.weight(1f))
            StatTile(Icons.Outlined.Notifications, "0", "Alerts Today", Modifier.weight(1f))
            StatTile(Icons.Filled.BatteryChargingFull, "—", "Battery", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(FamilyColors.Blue, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NoAlertsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(18.dp))
            .padding(vertical = 24.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Notifications, null, tint = FamilyColors.TextSecondary, modifier = Modifier.size(26.dp))
        Text("No alerts yet", color = FamilyColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(
            "Alerts about your senior will show up here.",
            color = FamilyColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun EmptyLinkCard(onLinkSenior: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(18.dp))
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(60.dp).background(FamilyColors.FieldBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Favorite, null, tint = FamilyColors.TextSecondary, modifier = Modifier.size(28.dp))
        }
        Text("No one linked yet", color = FamilyColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Link your senior family member so you can keep an eye on them and receive alerts.",
            color = FamilyColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(50.dp)
                .border(1.dp, FamilyColors.BlueBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onLinkSenior),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Link, null, tint = FamilyColors.Blue, modifier = Modifier.size(20.dp))
            Text("Link a senior now", color = FamilyColors.Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
