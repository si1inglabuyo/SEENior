package com.pup.seenior.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.ui.SeniorStrings
import com.pup.seenior.ui.home.HelpDelivery
import com.pup.seenior.ui.home.HomeViewModel
import com.pup.seenior.ui.theme.SeniorColors
import com.pup.seenior.ui.wellness.WellnessMessages
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AlertRed = Color(0xFFC62828)
private val AlertRedBg = Color(0xFFFDF7F5)
private val AlertRedBorder = Color(0xFFE9BDBD)
private val AlertAmber = Color(0xFFE99A20)
private val AlertAmberBg = Color(0xFFFDF3E7)

private const val FOURTEEN_DAYS_MS = 14L * 24 * 60 * 60 * 1000

/**
 * The senior's Alerts tab (CLAUDE.md §7/§8): the current open alert, if any, at the top, and the
 * last 14 days of alert history below it.
 *
 * "Current" is read from [HomeViewModel.helpDelivery] rather than re-derived here — that
 * property already carries the careful Waiting/Delivered distinction and the "never retires on a
 * timer" rule Home's own card depends on (see its KDoc), and this tab has to agree with Home
 * about what counts as still-open or a senior would get two different answers to the same
 * question on two different tabs.
 *
 * History deliberately excludes `status = "logged"` rows: those are Low-risk anomalies the Fuzzy
 * Logic layer judged not worth telling the senior about at the time (CLAUDE.md §5), and surfacing
 * them now, after the fact, would contradict that decision.
 */
@Composable
fun AlertsScreen(homeViewModel: HomeViewModel, viewModel: AlertsViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.start() }

    val language = homeViewModel.language
    val copy = SeniorStrings.forLanguage(language)
    val delivery = homeViewModel.helpDelivery

    val cutoff = remember { System.currentTimeMillis() - FOURTEEN_DAYS_MS }
    val recent = remember(viewModel.alerts, cutoff) {
        viewModel.alerts.filter { it.triggeredAt >= cutoff && it.status != "logged" }
    }
    val history = remember(recent, delivery) {
        recent.filterNot { it.alertId == delivery?.alert?.alertId }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SeniorColors.Green)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SEENior", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(copy.tabAlerts, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
            ) {
                item {
                    Text(
                        copy.alertsCurrentHeader,
                        color = SeniorColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    if (delivery != null) {
                        CurrentAlertCard(
                            delivery = delivery,
                            language = language,
                            copy = copy,
                            onStandDown = { homeViewModel.standDown(delivery.alert) }
                        )
                    } else {
                        EmptyNotice(copy.alertsNoActive)
                    }

                    Spacer(Modifier.height(22.dp))
                    HorizontalDivider(color = SeniorColors.FieldBorder)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        copy.alertsHistoryHeader,
                        color = SeniorColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                }

                if (history.isEmpty()) {
                    item { EmptyNotice(copy.alertsNoHistory) }
                } else {
                    items(history, key = { it.alertId }) { alert ->
                        HistoryAlertCard(alert = alert, language = language, copy = copy)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * The open alert, shown the same way [com.pup.seenior.ui.home.HomeScreen]'s HelpDeliveryCard
 * shows it — red once the family has actually been told, amber while it's still only on this
 * phone — but with the full why-and-what-the-numbers-found description instead of Home's
 * one-line summary, since this tab is where a senior comes to read the detail.
 */
@Composable
private fun CurrentAlertCard(
    delivery: HelpDelivery,
    language: String,
    copy: SeniorStrings.Copy,
    onStandDown: () -> Unit
) {
    val alert = delivery.alert
    val waiting = delivery is HelpDelivery.Waiting
    val accent = if (waiting) AlertAmber else AlertRed
    val bg = if (waiting) AlertAmberBg else AlertRedBg
    val border = if (waiting) AlertAmber else AlertRedBorder

    val wellnessCopy = WellnessMessages.forAlert(language, "", alert.triggerType, alert.timeBlock)
    val description = AlertDescriptions.describe(language, alert.triggerType, alert.timeBlock, alert.deviationScore)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(accent, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                wellnessCopy.headerSubtitle,
                color = accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            RiskBadge(alert.riskLevel, copy)
        }
        Spacer(Modifier.height(10.dp))
        Text(description, color = SeniorColors.TextPrimary, fontSize = 14.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(10.dp))
        Text(formatTimestamp(alert.triggeredAt), color = SeniorColors.TextSecondary, fontSize = 12.sp)

        // Same "I'm fine now" action Home offers, always green regardless of the card's own
        // accent — it's the senior's own affirmative action, not a severity indicator.
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, SeniorColors.Green, RoundedCornerShape(12.dp))
                .clickable(onClick = onStandDown),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                wellnessCopy.standDownButton,
                color = SeniorColors.Green,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HistoryAlertCard(alert: Alert, language: String, copy: SeniorStrings.Copy) {
    val wellnessCopy = WellnessMessages.forAlert(language, "", alert.triggerType, alert.timeBlock)
    val description = AlertDescriptions.describe(language, alert.triggerType, alert.timeBlock, alert.deviationScore)
    val accent = riskColor(alert.riskLevel)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SeniorColors.FieldBackground)
            .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(accent, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                wellnessCopy.headerSubtitle,
                color = SeniorColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(formatTimestamp(alert.triggeredAt), color = SeniorColors.TextSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(description, color = SeniorColors.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RiskBadge(alert.riskLevel, copy)
            Spacer(Modifier.width(8.dp))
            Text(
                statusLabel(alert.status, copy),
                color = SeniorColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RiskBadge(riskLevel: String, copy: SeniorStrings.Copy) {
    val accent = riskColor(riskLevel)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(riskLabel(riskLevel, copy), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyNotice(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SeniorColors.GreenLightBg)
            .border(1.dp, SeniorColors.GreenBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = SeniorColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

private fun riskColor(riskLevel: String): Color = when (riskLevel) {
    "high" -> AlertRed
    "medium" -> AlertAmber
    else -> SeniorColors.Green
}

private fun riskLabel(riskLevel: String, copy: SeniorStrings.Copy): String = when (riskLevel) {
    "high" -> copy.riskHigh
    "medium" -> copy.riskMedium
    else -> copy.riskLow
}

private fun statusLabel(status: String, copy: SeniorStrings.Copy): String = when (status) {
    "pending" -> copy.alertStatusPending
    "acknowledged_family" -> copy.alertStatusFamilyNotified
    "escalated_barangay" -> copy.alertStatusBarangayNotified
    "resolved" -> copy.alertStatusResolved
    "self_cancelled" -> copy.alertStatusSelfCancelled
    "false_positive" -> copy.alertStatusFalsePositive
    else -> status
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
