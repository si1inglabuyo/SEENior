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
import androidx.compose.runtime.remember
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
 * Family landing screen. Shows every linked senior with status tiles and a merged recent
 * alerts feed, both from [FamilyHomeViewModel] using the same alerts the Alerts tab acts on.
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

    // Poll while this tab is on screen and the app is in the foreground. A single fetch on
    // resume left "All clear" showing while an alert sat on the server.
    LifecycleResumeEffect(seniorsViewModel.contacts) {
        homeViewModel.startPolling(seniorsViewModel.contacts)
        onPauseOrDispose { homeViewModel.stopPolling() }
    }

    val copy = LocalFamilyCopy.current
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
            Text(copy.homeRoleBadge, color = Color.White, fontSize = 16.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                copy.greeting(firstName),
                color = FamilyColors.Blue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(greetingSubtitle(homeViewModel, copy), color = FamilyColors.TextSecondary, fontSize = 15.sp)

            // Read from the per-senior status, not the recent-alerts feed, which includes
            // resolved alerts and would keep claiming someone needs attention.
            val needsAttention = seniorsViewModel.contacts.filter {
                homeViewModel.statuses[it.senior.syncId]?.hasOpenAlert == true
            }
            if (needsAttention.isNotEmpty()) {
                OngoingAlertBanner(
                    names = needsAttention.map { it.senior.firstName },
                    onOpenAlerts = onSeeAllAlerts
                )
            }

            SectionHeader(copy.sectionMySeniors, onSeeAll = onSeeAllSeniors.takeIf { hasSeniors })

            // Loading and failure are checked before the empty case, or the user would be told
            // "no one linked yet" while seniors were just slow to load.
            if (seniorsViewModel.isLoading) {
                LoadingCard(copy.loadingSeniors)
            } else if (seniorsViewModel.loadFailed) {
                CouldNotLoadCard(
                    title = copy.couldNotLoadSeniorsTitle,
                    message = copy.errorMessage(seniorsViewModel.error) ?: copy.couldNotReachServer,
                    reassurance = copy.stillLinkedReassurance,
                    onRetry = { seniorsViewModel.refresh() }
                )
            } else if (seniorsViewModel.contacts.isEmpty()) {
                EmptyLinkCard(onLinkSenior)
            } else {
                seniorsViewModel.contacts.forEach { contact ->
                    SeniorCard(
                        contact = contact,
                        // Null whenever the figures aren't trustworthy yet; tiles show "—".
                        status = if (homeViewModel.loadFailed) null
                        else homeViewModel.statuses[contact.senior.syncId]
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (hasSeniors) {
                SectionHeader(
                    copy.sectionRecentAlerts,
                    onSeeAll = onSeeAllAlerts.takeIf { homeViewModel.recent.isNotEmpty() }
                )
                when {
                    homeViewModel.isLoading && !homeViewModel.loaded ->
                        LoadingCard(copy.loadingRecentAlerts)
                    homeViewModel.loadFailed -> CouldNotLoadCard(
                        title = copy.couldNotLoadAlertsTitle,
                        message = copy.errorMessage(homeViewModel.error) ?: copy.couldNotReachServer,
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

/** The line under the greeting. "You're all set today" is only said once a fetch confirmed nothing is open. */
private fun greetingSubtitle(homeViewModel: FamilyHomeViewModel, copy: FamilyStrings.Copy): String = when {
    !homeViewModel.loaded || homeViewModel.loadFailed -> copy.greetingCheckingOnSeniors
    homeViewModel.statuses.values.any { it.hasOpenAlert } -> copy.greetingSomeoneNeedsAttention
    else -> copy.greetingAllSetToday
}

/**
 * Home's replacement for the modal alert popup. It states that an alert is open and hands
 * off to the Alerts tab, which picks the alert itself, instead of blocking Home.
 */
@Composable
private fun OngoingAlertBanner(names: List<String>, onOpenAlerts: () -> Unit) {
    val message = LocalFamilyCopy.current.ongoingAlertMessage(names)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FamilyColors.AlertRedBg)
            .clickable(onClick = onOpenAlerts)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Notifications,
            contentDescription = null,
            tint = FamilyColors.AlertRed,
            modifier = Modifier.size(18.dp)
        )
        Text(
            message,
            color = FamilyColors.AlertRed,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
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
                Text(LocalFamilyCopy.current.seeAll, color = FamilyColors.Blue, fontSize = 14.sp)
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
    val copy = LocalFamilyCopy.current
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
                    copy.seniorRelationshipLabel(contact.relationshipLabel, contact.senior.gender),
                    color = FamilyColors.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.size(8.dp))
            StatusChip(status)
        }

        SeniorPresenceLine(senior.lastSeenAt)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                icon = Icons.Filled.MonitorHeart,
                value = status?.riskLevel?.replaceFirstChar { it.uppercase() } ?: copy.unknownDash,
                label = copy.riskLevelLabel,
                background = riskTileColor(status?.riskLevel),
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            StatTile(
                icon = Icons.Outlined.Notifications,
                value = status?.alertsToday?.toString() ?: copy.unknownDash,
                label = copy.alertsTodayLabel,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            // The senior's phone reports its current charge on each heartbeat and the server
            // overwrites the previous value. A single reading is device health; a series would
            // reveal when the senior sleeps, which stays on the device. Shows "—" if the phone
            // has never checked in, rather than 0%.
            StatTile(
                icon = Icons.Filled.BatteryChargingFull,
                value = senior.batteryPercent?.let { "$it%" } ?: copy.unknownDash,
                label = if (senior.isCharging == true) copy.chargingLabel else copy.batteryLabel,
                background = batteryTileColor(senior.batteryPercent, senior.isCharging == true),
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

/**
 * How recently the senior's phone checked in: a green "Phone active" when the last heartbeat
 * (`seniors.last_seen_at`) was within ~20 minutes, otherwise how long ago.
 */
@Composable
private fun SeniorPresenceLine(lastSeenAt: String?) {
    val copy = LocalFamilyCopy.current
    val lastSeenMinutesAgo = remember(lastSeenAt) {
        lastSeenAt?.let(::parseServerTime)?.let {
            java.time.Duration.between(it.toInstant(), java.time.Instant.now())
                .toMinutes().coerceAtLeast(0)
        }
    }
    val recent = lastSeenMinutesAgo != null && lastSeenMinutesAgo < 20L
    val color = if (recent) FamilyColors.SuccessGreen else FamilyColors.TextSecondary
    val text = when {
        lastSeenMinutesAgo == null -> copy.noCheckInYet
        recent -> copy.phoneActive
        else -> copy.lastCheckIn(copy.relativeTimeAgo(lastSeenMinutesAgo))
    }
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(text, color = color, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The alert-status chip. The senior's device presence is a separate line (see [SeniorPresenceLine]). */
@Composable
private fun StatusChip(status: SeniorStatus?) {
    val copy = LocalFamilyCopy.current
    // Kept short because the chip shares its row with the senior's name.
    val (text, color, background) = when {
        status == null -> Triple(copy.statusChecking, FamilyColors.TextSecondary, FamilyColors.FieldBackground)
        status.hasOpenAlert -> Triple(copy.statusAlert, FamilyColors.AlertRed, FamilyColors.AlertRedBg)
        else -> Triple(copy.statusAllClear, FamilyColors.SuccessGreen, FamilyColors.SafeGreenBg)
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

/** High/medium open risk breaks out of the flat blue palette so a problem is noticeable at a glance. */
/**
 * Colours the battery tile only when the charge is actionable. A phone on the charger is
 * fine whatever the number; a low and falling charge means monitoring is about to stop, so
 * it gets the same red as the high-risk tile.
 */
private fun batteryTileColor(percent: Int?, charging: Boolean): Color = when {
    percent == null || charging -> FamilyColors.Blue
    percent <= 15 -> FamilyColors.AlertRed
    percent <= 30 -> FamilyColors.Orange
    else -> FamilyColors.Blue
}

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
        // One line always, since the three tiles share a row and a wrapping label makes all taller.
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentAlertRow(item: RecentAlert, onClick: () -> Unit) {
    val copy = LocalFamilyCopy.current
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
            // Name first, status in the chip, so the headline isn't ellipsized on small screens.
            Text(
                item.seniorName,
                color = FamilyColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // The relative time sits in this line, not a right-hand column, which would
            // ellipsize the headline. The exact time is on the Alerts tab's detail screen.
            Text(
                // "17 hr" not "17 hr ago": the extra word would push this line into an ellipsis.
                "${copy.triggerShortLabel(item.alert.triggerType)} · ${copy.relativeTimeAgoShort(minutesAgo(item.alert.createdAt))}",
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
            Text(copy.recentAlertChipLabel(item.alert.status), color = accent, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun NoAlertsCard() {
    val copy = LocalFamilyCopy.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(18.dp))
            .padding(vertical = 24.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.Notifications, null, tint = FamilyColors.TextSecondary, modifier = Modifier.size(26.dp))
        Text(copy.noAlertsYetTitle, color = FamilyColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(
            copy.noAlertsYetBody,
            color = FamilyColors.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun EmptyLinkCard(onLinkSenior: () -> Unit) {
    val copy = LocalFamilyCopy.current
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
        Text(copy.noOneLinkedTitle, color = FamilyColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(
            copy.noOneLinkedBody,
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
            Text(copy.linkASeniorNow, color = FamilyColors.Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
