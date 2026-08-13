package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.network.dto.ContactDto

/**
 * Family landing screen (designs/family_contact/home_screen_with_linked_senior). Shows every
 * linked senior with their status tiles and a merged recent-alerts feed, both fed by
 * [FamilyHomeViewModel] from the same alerts the Alerts tab acts on — Home used to hardcode
 * "0 alerts today / no alerts yet" while an open HIGH alert sat one tab away.
 */
@Composable
fun FamilyHomeScreen(
    seniorsViewModel: FamilySeniorsViewModel,
    homeViewModel: FamilyHomeViewModel = viewModel(),
    profileViewModel: FamilyProfileViewModel = viewModel(),
    onLinkSenior: () -> Unit,
    onSeeAllSeniors: () -> Unit,
    onSeeAllAlerts: () -> Unit
) {
    LaunchedEffect(Unit) {
        profileViewModel.refresh()
    }

    // Re-fetch whenever the tab is entered/resumed as well as when the linked-senior list
    // changes. Keying on contacts alone would leave a family member looking at a stale "All
    // clear" after a new alert arrived, since the senior list itself rarely changes.
    LifecycleResumeEffect(seniorsViewModel.contacts) {
        homeViewModel.refresh(seniorsViewModel.contacts)
        onPauseOrDispose { }
    }

    val firstName = profileViewModel.fullName.split(" ").firstOrNull() ?: ""
    val hasSeniors = !seniorsViewModel.isLoading && !seniorsViewModel.loadFailed &&
        seniorsViewModel.contacts.isNotEmpty()

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
            Text(
                if (firstName.isNotBlank()) "Hi there, $firstName" else "Hi there,",
                color = FamilyColors.Blue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(greetingSubtitle(homeViewModel), color = FamilyColors.TextSecondary, fontSize = 15.sp)

            SectionHeader("MY SENIORS", onSeeAll = onSeeAllSeniors.takeIf { hasSeniors })

            // Loading and failure are both checked before the empty case — rendering either as
            // EmptyLinkCard told the user "no one linked yet" when their seniors were still
            // linked, just slow to fetch or unreachable.
            if (seniorsViewModel.isLoading) {
                LoadingCard("Loading your seniors…")
            } else if (seniorsViewModel.loadFailed) {
                CouldNotLoadCard(
                    title = "Could not load your seniors",
                    message = seniorsViewModel.error ?: "Could not reach the server.",
                    reassurance = "They are still linked to your account.",
                    onRetry = { seniorsViewModel.refresh() }
                )
            } else if (seniorsViewModel.contacts.isEmpty()) {
                EmptyLinkCard(onLinkSenior)
            } else {
                seniorsViewModel.contacts.forEach { contact ->
                    SeniorCard(
                        contact = contact,
                        // Null whenever the figures aren't trustworthy yet — the tiles show
                        // "—" rather than a stale or invented value.
                        status = if (homeViewModel.loadFailed) null
                        else homeViewModel.statuses[contact.senior.syncId]
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (hasSeniors) {
                SectionHeader(
                    "RECENT ALERTS",
                    onSeeAll = onSeeAllAlerts.takeIf { homeViewModel.recent.isNotEmpty() }
                )
                when {
                    homeViewModel.isLoading && !homeViewModel.loaded ->
                        LoadingCard("Loading recent alerts…")
                    homeViewModel.loadFailed -> CouldNotLoadCard(
                        title = "Could not load recent alerts",
                        message = homeViewModel.error ?: "Could not reach the server.",
                        onRetry = { homeViewModel.refresh(seniorsViewModel.contacts) }
                    )
                    homeViewModel.recent.isEmpty() -> NoAlertsCard()
                    else -> homeViewModel.recent.forEach { item ->
                        RecentAlertRow(item, onClick = onSeeAllAlerts)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The line under the greeting. "You're all set today" is a claim about the seniors' safety,
 *  so it is only made once a fetch has confirmed nothing is open. */
private fun greetingSubtitle(homeViewModel: FamilyHomeViewModel): String = when {
    !homeViewModel.loaded || homeViewModel.loadFailed -> "Checking on your seniors…"
    homeViewModel.statuses.values.any { it.hasOpenAlert } -> "Someone needs your attention"
    else -> "You're all set today"
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = FamilyColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("See all", color = FamilyColors.Blue, fontSize = 14.sp)
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = FamilyColors.Blue,
                    modifier = Modifier.padding(start = 4.dp).size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SeniorCard(contact: ContactDto, status: SeniorStatus?) {
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
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.size(8.dp))
            StatusChip(status)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                icon = Icons.Filled.MonitorHeart,
                value = status?.riskLevel?.replaceFirstChar { it.uppercase() } ?: "—",
                label = "Risk Level",
                background = riskTileColor(status?.riskLevel),
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            StatTile(
                icon = Icons.Outlined.Notifications,
                value = status?.alertsToday?.toString() ?: "—",
                label = "Alerts Today",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            // Battery stays "—": no battery telemetry exists in the cloud schema, and charge
            // level is device sensor data, which CLAUDE.md §11 keeps on the senior's phone.
            // Surfacing it needs an explicit privacy decision, not a quiet new field.
            StatTile(
                icon = Icons.Filled.BatteryChargingFull,
                value = "—",
                label = "Battery",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

/** Replaces the mock's hardcoded "Online" badge. There is no presence/heartbeat channel in the
 *  system, so online-ness cannot be known; whether an alert is open can, and is what a family
 *  member actually opens this screen to find out. */
@Composable
private fun StatusChip(status: SeniorStatus?) {
    // Kept short on purpose: this chip shares its row with the senior's name, and a longer
    // label ("Needs attention") squeezed the name into "Revi …" on a 720px screen.
    val (text, color, background) = when {
        status == null -> Triple("Checking", FamilyColors.TextSecondary, FamilyColors.FieldBackground)
        status.hasOpenAlert -> Triple("Alert", FamilyColors.AlertRed, FamilyColors.AlertRedBg)
        else -> Triple("All clear", FamilyColors.SuccessGreen, FamilyColors.SafeGreenBg)
    }
    Row(
        modifier = Modifier
            .background(background, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, color = color, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

/** High/medium open risk breaks out of the flat blue palette — a family member scanning Home
 *  should not have to read the tile's text to notice something is wrong. */
private fun riskTileColor(riskLevel: String?): Color = when (riskLevel) {
    "high" -> FamilyColors.AlertRed
    "medium" -> FamilyColors.Orange
    else -> FamilyColors.Blue
}

@Composable
private fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    background: Color = FamilyColors.Blue
) {
    Column(
        modifier = modifier
            .background(background, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecentAlertRow(item: RecentAlert, onClick: () -> Unit) {
    val (accent, background) = when (item.alert.status) {
        "pending" -> FamilyColors.AlertRed to FamilyColors.AlertRedBg
        "escalated" -> FamilyColors.Orange to FamilyColors.OrangeBg
        "acknowledged" -> FamilyColors.Blue to FamilyColors.BlueLightBg
        else -> FamilyColors.SuccessGreen to FamilyColors.SafeGreenBg
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Notifications, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            // Name first, status in the chip. Spelling the status out in the headline instead
            // ("You acknowledged Revi Ocasion") duplicated the chip and, on a 720px screen, was
            // the thing that got ellipsized by it.
            Text(
                item.seniorName,
                color = FamilyColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // The relative time rides in this line rather than in its own right-hand column:
            // stacking it above the chip made the right side wide enough to ellipsize the
            // headline ("You acknowledged R…"), losing the part that says what happened.
            // The exact clock time is still on the Alerts tab's detail screen.
            Text(
                // "17 hr" not "17 hr ago": next to the widest chip ("Acknowledged") the extra
                // word was exactly what pushed this line into an ellipsis.
                "${triggerShortLabel(item.alert.triggerType)} · ${relativeTimeAgo(item.alert.createdAt).removeSuffix(" ago")}",
                color = FamilyColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .background(background, RoundedCornerShape(20.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(recentAlertChipLabel(item.alert.status), color = accent, fontSize = 10.sp, maxLines = 1)
        }
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
