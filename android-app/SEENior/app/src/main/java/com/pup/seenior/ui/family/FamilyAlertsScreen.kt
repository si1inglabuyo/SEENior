package com.pup.seenior.ui.family

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.network.dto.AlertDto
import com.pup.seenior.network.dto.ContactDto
import com.pup.seenior.network.dto.SeniorDto

private val DISPATCH_REASONS = listOf(
    "No movement / unresponsive",
    "Fall suspected",
    "Medical emergency",
    "Other"
)

/**
 * Family Alerts tab (designs/family_contact/dashboard_notification). A small state machine
 * driven by FamilyAlertsViewModel.screen: All Clear <-> Active Alert -> Acknowledged -> (Call
 * Senior | Alert Location | Dispatch Barangay, each with their own back) -> Resolved -> All Clear.
 */
@Composable
fun FamilyAlertsScreen(
    viewModel: FamilyAlertsViewModel,
    contacts: List<ContactDto>,
    seniorsLoading: Boolean = false,
    seniorsLoadFailed: Boolean = false,
    onRetrySeniors: () -> Unit = {}
) {
    // Polls while this tab is resumed, rather than fetching once when the senior list changes —
    // that list rarely changes, so nothing re-triggered the fetch and the screen could keep
    // asserting "All clear" long after an alert had arrived.
    LifecycleResumeEffect(contacts) {
        viewModel.startPolling(contacts)
        onPauseOrDispose { viewModel.stopPolling() }
    }

    val fallbackSenior = contacts.firstOrNull()?.senior
    val senior = viewModel.activeSenior?.senior ?: fallbackSenior

    // Until the senior list has loaded we can't know whether an empty list means "none linked"
    // or "not fetched yet", so don't render any status claim.
    if (seniorsLoading) {
        AlertsLoadingContent()
        return
    }

    // If the senior list itself never loaded we know nothing about their status — showing
    // "All Clear" here would actively assert the senior is fine, which is the worst possible
    // thing for a safety app to get wrong.
    if (seniorsLoadFailed) {
        AlertsLoadFailedContent(
            message = "Could not reach the server. Check your internet connection.",
            onRetry = onRetrySeniors
        )
        return
    }

    when (viewModel.screen) {
        AlertScreen.LOADING -> AlertsLoadingContent()
        AlertScreen.LOAD_FAILED -> AlertsLoadFailedContent(
            message = viewModel.error ?: "Could not reach the server.",
            onRetry = { viewModel.retry(contacts) }
        )
        AlertScreen.ALL_CLEAR -> AllClearContent(senior)
        AlertScreen.DETAIL -> {
            val alert = viewModel.activeAlert
            if (alert == null || senior == null) {
                AllClearContent(senior)
            } else {
                AlertDetailContent(alert, senior, onAcknowledge = { viewModel.acknowledge() })
            }
        }
        AlertScreen.ACKNOWLEDGED -> {
            val alert = viewModel.activeAlert
            if (alert == null || senior == null) {
                AllClearContent(senior)
            } else {
                AcknowledgedContent(
                    alert = alert,
                    senior = senior,
                    onCallSenior = { viewModel.goTo(AlertScreen.CALL_SENIOR) },
                    onNavigate = { viewModel.goTo(AlertScreen.LOCATION) },
                    onDispatch = { viewModel.goTo(AlertScreen.DISPATCH) },
                    onMarkResolved = { viewModel.markResolved() }
                )
            }
        }
        AlertScreen.CALL_SENIOR -> senior?.let {
            CallSeniorContent(it, onBack = { viewModel.goTo(AlertScreen.ACKNOWLEDGED) })
        }
        AlertScreen.LOCATION -> {
            val alert = viewModel.activeAlert
            if (alert != null && senior != null) {
                AlertLocationContent(alert, senior, onBack = { viewModel.goTo(AlertScreen.ACKNOWLEDGED) })
            }
        }
        AlertScreen.DISPATCH -> senior?.let {
            DispatchBarangayContent(
                seniorName = it.firstName,
                onBack = { viewModel.goTo(AlertScreen.ACKNOWLEDGED) },
                onDispatch = { reason, notes -> viewModel.dispatchBarangay(reason, notes) }
            )
        }
        AlertScreen.RESOLVED -> {
            val summary = viewModel.resolvedSummary
            if (summary != null && senior != null) {
                ResolvedContent(senior, summary, onDone = { viewModel.backToAllClear() })
            } else {
                AllClearContent(senior)
            }
        }
    }
}

/** Shown while the status check is still in flight. The tab used to default straight to
 *  All Clear, so a cold server meant it asserted "your senior is safe" for ~40s before it had
 *  actually checked anything. */
@Composable
private fun AlertsLoadingContent() {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BlueHeaderBar("Alerts")
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            LoadingCard("Checking your senior's status…")
        }
    }
}

/** Explicit "we don't know" state. Distinct from All Clear on purpose: All Clear is a positive
 *  claim that the senior is fine, and we must never make that claim from a failed request. */
@Composable
private fun AlertsLoadFailedContent(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BlueHeaderBar("Alerts")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            CouldNotLoadCard(
                title = "Status unavailable",
                message = message,
                reassurance = "We could not check on your senior — this does not mean anything is wrong.",
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun AllClearContent(senior: SeniorDto?) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BlueHeaderBar("Alerts")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(72.dp)
                    .background(FamilyColors.SafeGreenBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Shield, null, tint = FamilyColors.SuccessGreen, modifier = Modifier.size(34.dp))
            }
            Text("All Clear", color = FamilyColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))

            if (senior == null) {
                Text(
                    "Link a senior to start receiving alerts.",
                    color = FamilyColors.TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                return@Column
            }

            Text(
                "${senior.firstName} is safe. Routine is normal.",
                color = FamilyColors.TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).background(FamilyColors.Blue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initials(senior), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text("${senior.firstName} ${senior.lastName}", color = FamilyColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${senior.age} · ${senior.gender.replaceFirstChar { it.uppercase() }} · ${senior.barangay}", color = FamilyColors.TextSecondary, fontSize = 13.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MiniStatTile("Low", "Risk Level", FamilyColors.SuccessGreen, Modifier.weight(1f))
                    MiniStatTile("—", "Battery", FamilyColors.TextSecondary, Modifier.weight(1f))
                }
            }

            SectionInfoBox(
                text = "SEENior is quietly tracking ${senior.firstName}'s routine fingerprint. No action needed.",
                bg = FamilyColors.SafeGreenBg,
                textColor = FamilyColors.SuccessGreen,
                modifier = Modifier.padding(top = 20.dp)
            )

            SectionLabel("LAST KNOWN LOCATION", Modifier.padding(top = 24.dp, bottom = 10.dp))
            MapPlaceholder()
        }
    }
}

@Composable
private fun AlertDetailContent(alert: AlertDto, senior: SeniorDto, onAcknowledge: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(FamilyColors.AlertRed).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.NotificationsNone, null, tint = Color.White)
            Text("Active Alert", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.padding(top = 8.dp).size(64.dp).background(FamilyColors.AlertRedBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Warning, null, tint = FamilyColors.AlertRed, modifier = Modifier.size(30.dp))
            }
            Text(
                "${alert.riskLevel.replaceFirstChar { it.uppercase() }} Risk Detected",
                color = FamilyColors.AlertRed,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "${senior.firstName} may need your attention.",
                color = FamilyColors.AlertRed,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "Detected ${formatClockTime(alert.createdAt)} · ${relativeTimeAgo(alert.createdAt)}",
                color = FamilyColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .border(1.dp, FamilyColors.AlertRed, RoundedCornerShape(16.dp))
                    .background(FamilyColors.AlertRedBg, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text("Alert reason", color = FamilyColors.AlertRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    alertReasonText(alert.triggerType),
                    color = FamilyColors.AlertRed,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
                ColorPillButton("Acknowledge Alert", color = FamilyColors.AlertRed, onClick = onAcknowledge)
            }

            SectionLabel("ESCALATION CHAIN", Modifier.padding(top = 24.dp, bottom = 10.dp))
            EscalationRow(Icons.Filled.CheckCircle, FamilyColors.Blue, "Senior prompted at ${formatClockTime(alert.createdAt)}")
            EscalationRow(Icons.Filled.HourglassEmpty, FamilyColors.Blue, "Your notified - pending acknowledgement")
            EscalationRow(Icons.Filled.AccountBalance, FamilyColors.Blue, "Barangay - 10 mins window")

            SectionLabel("LAST KNOWN LOCATION", Modifier.padding(top = 24.dp, bottom = 10.dp))
            MapPlaceholder()
        }
    }
}

@Composable
private fun AcknowledgedContent(
    alert: AlertDto,
    senior: SeniorDto,
    onCallSenior: () -> Unit,
    onNavigate: () -> Unit,
    onDispatch: () -> Unit,
    onMarkResolved: () -> Unit
) {
    val alreadyDispatched = alert.status == "escalated"
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(FamilyColors.Orange).padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("You Acknowledged", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.padding(top = 8.dp).size(72.dp).background(FamilyColors.SafeGreenBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = FamilyColors.SuccessGreen, modifier = Modifier.size(34.dp))
            }
            Text("Alert Acknowledged", color = FamilyColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text(
                if (alreadyDispatched) "Barangay responders have been dispatched to ${senior.firstName}'s location."
                else "Barangay responders will be notified if this isn't resolved soon.",
                color = FamilyColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (!alreadyDispatched) {
                SectionInfoBox(
                    text = "Barangay will be notified if unresolved in 10 minutes",
                    bg = FamilyColors.OrangeBg,
                    textColor = FamilyColors.WarningText,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }

            SectionLabel("NEXT STEPS", Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp))

            ColorPillButton("Call ${senior.firstName}", color = FamilyColors.SuccessGreen, icon = Icons.Filled.Phone, onClick = onCallSenior)
            Spacer(Modifier.height(12.dp))
            ColorPillButton("Navigate to her location", color = FamilyColors.Blue, icon = Icons.Filled.Navigation, onClick = onNavigate)
            Spacer(Modifier.height(12.dp))
            if (!alreadyDispatched) {
                ColorPillButton("Dispatch barangay responders", color = FamilyColors.Orange, icon = Icons.Filled.AccountBalance, onClick = onDispatch)
                Spacer(Modifier.height(12.dp))
            }
            OutlinePillButton("Mark resolved. ${genderPronoun(senior)}'s safe", onClick = onMarkResolved)
        }
    }
}

@Composable
private fun CallSeniorContent(senior: SeniorDto, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader("Call Senior", FamilyColors.HeaderBlue, onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(64.dp).border(2.dp, FamilyColors.Blue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials(senior), color = FamilyColors.Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text("${senior.firstName} ${senior.lastName}", color = FamilyColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("${senior.age}", color = FamilyColors.TextSecondary, fontSize = 14.sp)
            }

            SectionLabel("CONTACT INFORMATION", Modifier.padding(top = 24.dp, bottom = 10.dp))
            Column(modifier = Modifier.fillMaxWidth().border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(14.dp))) {
                ContactInfoRow(Icons.Filled.Phone, "Phone", senior.mobileNumber)
                androidx.compose.material3.HorizontalDivider(color = FamilyColors.FieldBorder)
                ContactInfoRow(Icons.Filled.Navigation, "Home address", senior.address)
            }

            Spacer(Modifier.height(24.dp))
            ColorPillButton(
                "Call now",
                color = FamilyColors.SuccessGreen,
                icon = Icons.Filled.Phone,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${senior.mobileNumber}")))
                }
            )
        }
    }
}

@Composable
private fun AlertLocationContent(alert: AlertDto, senior: SeniorDto, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader("Alert Location", FamilyColors.HeaderBlue, onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            SectionInfoBox(
                text = "Last known location. Captured at ${formatClockTime(alert.createdAt)}",
                bg = FamilyColors.AlertRedBg,
                textColor = FamilyColors.AlertRed
            )
            Spacer(Modifier.height(16.dp))
            MapPlaceholder(Modifier.height(220.dp))
            Spacer(Modifier.height(20.dp))
            // No live coordinates exist in this system (CLAUDE.md §11 — cluster IDs only,
            // never raw GPS), so this navigates to the senior's registered home address
            // as the closest real stand-in rather than a fabricated live pin.
            ColorPillButton(
                "Navigate here",
                color = FamilyColors.Blue,
                icon = Icons.Filled.Navigation,
                onClick = {
                    val uri = Uri.parse("geo:0,0?q=" + Uri.encode(senior.address))
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            )
        }
    }
}

@Composable
private fun DispatchBarangayContent(
    seniorName: String,
    onBack: () -> Unit,
    onDispatch: (reason: String, notes: String?) -> Unit
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader("Dispatch Barangay", FamilyColors.HeaderBlue, onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            SectionInfoBox(
                text = "This requests an official welfare check from the Barangay. A responder will be dispatched to $seniorName's location.",
                bg = FamilyColors.OrangeBg,
                textColor = FamilyColors.WarningText
            )

            SectionLabel("REASON FOR DISPATCH", Modifier.padding(top = 24.dp, bottom = 10.dp))
            DISPATCH_REASONS.forEach { reason ->
                val selected = selectedReason == reason
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .border(1.dp, if (selected) FamilyColors.Orange else FamilyColors.FieldBorder, RoundedCornerShape(14.dp))
                        .clickable { selectedReason = reason }
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                        null,
                        tint = if (selected) FamilyColors.Orange else FamilyColors.TextSecondary
                    )
                    Text(reason, color = FamilyColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Additional notes for responder…") },
                modifier = Modifier.fillMaxWidth().height(110.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FamilyColors.Orange,
                    unfocusedBorderColor = FamilyColors.FieldBorder
                )
            )

            Spacer(Modifier.height(20.dp))
            ColorPillButton(
                "Dispatch now",
                color = FamilyColors.Orange,
                icon = Icons.Filled.AccountBalance,
                onClick = { selectedReason?.let { onDispatch(it, notes.ifBlank { null }) } }
            )
        }
    }
}

@Composable
private fun ResolvedContent(senior: SeniorDto, summary: ResolvedSummary, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader("Alert Resolved", FamilyColors.HeaderBlue, onDone)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.padding(top = 8.dp).size(72.dp).background(FamilyColors.SafeGreenBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Favorite, null, tint = FamilyColors.SuccessGreen, modifier = Modifier.size(32.dp))
            }
            Text("${senior.firstName} is Safe", color = FamilyColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text("Alert closed by you · ${summary.resolvedAt}", color = FamilyColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

            SectionInfoBox(
                text = "All family members and Barangay responders have been notified that the situation is resolved.",
                bg = FamilyColors.SafeGreenBg,
                textColor = FamilyColors.SuccessGreen,
                modifier = Modifier.padding(top = 18.dp)
            )

            SectionLabel("INCIDENT SUMMARY", Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp))
            Column(modifier = Modifier.fillMaxWidth().background(FamilyColors.FieldBackground, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp)) {
                SummaryRow("Alert ID", summary.alertShortId)
                SummaryRow("Triggered", summary.triggeredAt)
                SummaryRow("Resolved", summary.resolvedAt)
                SummaryRow("Duration", "${summary.durationMinutes} minutes")
                SummaryRow("Resolved by", "You", isLast = true)
            }
        }
    }
}

// ---- Small shared pieces ----

@Composable
private fun BlueHeaderBar(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(FamilyColors.HeaderBlue).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.NotificationsNone, null, tint = Color.White)
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = FamilyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
private fun SectionInfoBox(text: String, bg: Color, textColor: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().background(bg, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text(text, color = textColor, fontSize = 14.sp)
    }
}

@Composable
private fun MiniStatTile(value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(FamilyColors.FieldBackground, RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = FamilyColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun EscalationRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, color = FamilyColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun ContactInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).background(FamilyColors.BlueLightBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = FamilyColors.Blue, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(label, color = FamilyColors.TextSecondary, fontSize = 13.sp)
            Text(value, color = FamilyColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, isLast: Boolean = false) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(label, color = FamilyColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(value, color = FamilyColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (!isLast) androidx.compose.material3.HorizontalDivider(color = FamilyColors.FieldBorder)
    }
}

private fun initials(senior: SeniorDto): String =
    "${senior.firstName.firstOrNull()?.uppercase() ?: ""}${senior.lastName.firstOrNull()?.uppercase() ?: ""}"

private fun genderPronoun(senior: SeniorDto): String =
    if (senior.gender.equals("male", ignoreCase = true)) "He" else "She"
