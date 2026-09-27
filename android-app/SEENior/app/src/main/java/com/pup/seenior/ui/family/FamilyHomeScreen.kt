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

    // Poll for as long as this tab is on screen and the app is in the foreground, stopping the
    // moment it isn't. A single fetch on resume left a family member staring at "All clear"
    // while an alert was already sitting on the server — the senior list this is keyed on
    // rarely changes, so nothing would have re-triggered it.
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

            // Read from the per-senior status rather than the recent-alerts feed: that feed
            // includes alerts that have already been resolved, so it would keep claiming
            // someone needs attention after the family member had dealt with them.
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

            // Loading and failure are both checked before the empty case — rendering either as
            // EmptyLinkCard told the user "no one linked yet" when their seniors were still
            // linked, just slow to fetch or unreachable.
            if (seniorsViewModel.isLoading) {
                LoadingCard(copy.loadingSeniors)
            } else if (seniorsViewModel.loadFailed) {
                CouldNotLoadCard(
                    title = copy.couldNotLoadSeniorsTitle,
                    message = seniorsViewModel.error ?: copy.couldNotReachServer,
                    reassurance = copy.stillLinkedReassurance,
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
                    copy.sectionRecentAlerts,
                    onSeeAll = onSeeAllAlerts.takeIf { homeViewModel.recent.isNotEmpty() }
                )
                when {
                    homeViewModel.isLoading && !homeViewModel.loaded ->
                        LoadingCard(copy.loadingRecentAlerts)
                    homeViewModel.loadFailed -> CouldNotLoadCard(
                        title = copy.couldNotLoadAlertsTitle,
                        message = homeViewModel.error ?: copy.couldNotReachServer,
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
private fun greetingSubtitle(homeViewModel: FamilyHomeViewModel, copy: FamilyStrings.Copy): String = when {
    !homeViewModel.loaded || homeViewModel.loadFailed -> copy.greetingCheckingOnSeniors
    homeViewModel.statuses.values.any { it.hasOpenAlert } -> copy.greetingSomeoneNeedsAttention
    else -> copy.greetingAllSetToday
}

/**
 * Home's replacement for the modal alert popup that used to sit over this tab.
 *
 * The popup was a second surface for an alert the Alerts tab already opens straight onto, and
 * being modal it blocked the whole of Home until it was answered — on the one screen a family
 * member opens for a quick read on how everyone is doing. This states the same fact in a line
 * and hands off to the tab that can act on it.
 *
 * Deliberately routed through onSeeAllAlerts rather than naming an alert id the way the popup's
 * "View" did: the popup was advertising one specific alert and had to open that one, while this
 * banner only reports that something is open, so the Alerts tab's own pick is the right one.
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
                    contact.relationshipLabel?.replaceFirstChar { it.uppercase() } ?: copy.familyFallbackLabel,
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
            // The privacy decision the earlier note here asked for was taken deliberately:
            // the senior's phone reports its *current* charge on each heartbeat and the server
            // overwrites the previous value. A single reading is device health — whether the
            // phone can keep monitoring at all — while a series of readings would describe when
            // the senior charges their phone, and so roughly when they sleep, which is the
            // behavioural data CLAUDE.md §11 keeps on the device. Nothing accumulates.
            //
            // Still "—" for a senior whose phone has never checked in. That is a real state
            // worth showing as itself rather than dressing up as 0%.
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
 * How recently the senior's phone checked in — a green "Phone active" when the last heartbeat
 * was within ~20 minutes (the sensor loop sends one every ~15), otherwise how long ago.
 *
 * This is the honest version of the mock's old "Online" badge: the heartbeat
 * (`seniors.last_seen_at`) is a real signal now, unlike when [StatusChip] was written.
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

/** The alert-status chip. Whether an alert is open is what a family member opens this screen
 *  to find out; the senior's device presence is a separate line (see [SeniorPresenceLine]). */
@Composable
private fun StatusChip(status: SeniorStatus?) {
    val copy = LocalFamilyCopy.current
    // Kept short on purpose: this chip shares its row with the senior's name, and a longer
    // label ("Needs attention") squeezed the name into "Revi …" on a 720px screen.
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

/** High/medium open risk breaks out of the flat blue palette — a family member scanning Home
 *  should not have to read the tile's text to notice something is wrong. */
/**
 * Colours the battery tile only when the charge is something a family member could act on.
 *
 * A phone on the charger is being dealt with, whatever the number says — 8% and climbing needs
 * nobody's attention, so it stays the ordinary blue. It is 8% and *falling* that means monitoring
 * is about to stop, and that is worth the same red the risk tile uses for a high-risk alert.
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
        Text(label, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
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
