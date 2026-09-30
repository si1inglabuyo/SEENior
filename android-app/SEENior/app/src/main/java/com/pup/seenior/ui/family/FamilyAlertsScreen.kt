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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
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
import com.pup.seenior.location.Geohash
import com.pup.seenior.network.dto.AlertDto
import com.pup.seenior.network.dto.ContactDto
import com.pup.seenior.network.dto.SeniorDto

// Matches the mockup's fixed "10 mins/minutes" copy -- not read from any backend setting
// (unlike the family-tier SMS window, which does read FAMILY_RESPONSE_SECONDS; see sms.py).
private const val BARANGAY_WINDOW_MINUTES = 10

// Sent verbatim as the dispatch `reason` (POST /alerts/{id}/dispatch) -- kept in English
// regardless of the account's language; see Copy.dispatchReasonLabel's kdoc for why.
private val DISPATCH_REASON_CODES = listOf(
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

    val copy = LocalFamilyCopy.current

    // If the senior list itself never loaded we know nothing about their status — showing
    // "All Clear" here would actively assert the senior is fine, which is the worst possible
    // thing for a safety app to get wrong.
    if (seniorsLoadFailed) {
        AlertsLoadFailedContent(
            message = copy.couldNotReachInternet,
            onRetry = onRetrySeniors
        )
        return
    }

    when (viewModel.screen) {
        AlertScreen.LOADING -> AlertsLoadingContent()
        AlertScreen.LOAD_FAILED -> AlertsLoadFailedContent(
            message = copy.errorMessage(viewModel.error) ?: copy.couldNotReachServer,
            onRetry = { viewModel.retry(contacts) }
        )
        AlertScreen.ALL_CLEAR -> AllClearContent(senior, onViewHistory = { viewModel.goTo(AlertScreen.HISTORY) })
        AlertScreen.DETAIL -> {
            val alert = viewModel.activeAlert
            if (alert == null || senior == null) {
                AllClearContent(senior, onViewHistory = { viewModel.goTo(AlertScreen.HISTORY) })
            } else {
                AlertDetailContent(alert, senior, onAcknowledge = { viewModel.acknowledge() })
            }
        }
        AlertScreen.ACKNOWLEDGED -> {
            val alert = viewModel.activeAlert
            if (alert == null || senior == null) {
                AllClearContent(senior, onViewHistory = { viewModel.goTo(AlertScreen.HISTORY) })
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
                AllClearContent(senior, onViewHistory = { viewModel.goTo(AlertScreen.HISTORY) })
            }
        }
        AlertScreen.HISTORY -> AlertHistoryListContent(
            items = viewModel.history,
            onBack = { viewModel.goTo(AlertScreen.ALL_CLEAR) },
            onSelect = { viewModel.openHistoryDetail(it) }
        )
        AlertScreen.HISTORY_DETAIL -> {
            val item = viewModel.selectedHistoryItem
            if (item != null) {
                AlertHistoryDetailContent(
                    item = item,
                    summary = viewModel.summaryFor(item.alert),
                    onBack = { viewModel.goTo(AlertScreen.HISTORY) }
                )
            } else {
                // Defensive only -- openHistoryDetail always sets both together.
                AlertHistoryListContent(
                    items = viewModel.history,
                    onBack = { viewModel.goTo(AlertScreen.ALL_CLEAR) },
                    onSelect = { viewModel.openHistoryDetail(it) }
                )
            }
        }
    }
}

/** Shown while the status check is still in flight. The tab used to default straight to
 *  All Clear, so a cold server meant it asserted "your senior is safe" for ~40s before it had
 *  actually checked anything. */
@Composable
private fun AlertsLoadingContent() {
    val copy = LocalFamilyCopy.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BlueHeaderBar(copy.alertsHeader)
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            LoadingCard(copy.checkingStatus)
        }
    }
}

/** Explicit "we don't know" state. Distinct from All Clear on purpose: All Clear is a positive
 *  claim that the senior is fine, and we must never make that claim from a failed request. */
@Composable
private fun AlertsLoadFailedContent(message: String, onRetry: () -> Unit) {
    val copy = LocalFamilyCopy.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BlueHeaderBar(copy.alertsHeader)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            CouldNotLoadCard(
                title = copy.statusUnavailableTitle,
                message = message,
                reassurance = copy.statusUnavailableReassurance,
                onRetry = onRetry
            )
        }
    }
}

/**
 * The Alerts tab with nothing open.
 *
 * Deliberately almost empty. This used to render a senior profile card, a "Low" risk tile, a
 * "—" battery tile, a green reassurance box and a map placeholder — none of it derived from
 * anything the app knew at that moment. "Low" in particular was a hardcoded literal, not a
 * reading, and a safety app must not assert a risk level it has never measured. The senior's
 * details already live on the Contacts tab; when nothing is happening the honest answer is one
 * sentence.
 */
@Composable
private fun AllClearContent(senior: SeniorDto?, onViewHistory: () -> Unit) {
    val copy = LocalFamilyCopy.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BlueHeaderBar(copy.alertsHeader)
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Kept apart from the no-alerts line on purpose: "link a senior" is not a statement
            // about anybody's safety, it is the setup step, and a brand-new user landing on a
            // blank tab would otherwise have no idea why it is blank.
            Text(
                if (senior == null) copy.linkASeniorToStart
                else copy.noOngoingAlerts,
                color = FamilyColors.TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            if (senior != null) {
                Text(
                    copy.viewAlertHistory,
                    color = FamilyColors.Blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp).clickable(onClick = onViewHistory)
                )
            }
        }
    }
}

@Composable
private fun AlertDetailContent(alert: AlertDto, senior: SeniorDto, onAcknowledge: () -> Unit) {
    val copy = LocalFamilyCopy.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(FamilyColors.AlertRed).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.NotificationsNone, null, tint = Color.White)
            Text(copy.activeAlertHeader, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
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
                copy.riskDetected(alert.riskLevel.replaceFirstChar { it.uppercase() }),
                color = FamilyColors.AlertRed,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                copy.mayNeedAttention(senior.firstName),
                color = FamilyColors.AlertRed,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                copy.detectedAt(formatClockTime(alert.createdAt), copy.relativeTimeAgo(minutesAgo(alert.createdAt))),
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
                Text(copy.alertReasonLabel, color = FamilyColors.AlertRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    copy.alertReasonText(alert.triggerType),
                    color = FamilyColors.AlertRed,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
                ColorPillButton(copy.acknowledgeAlertButton, color = FamilyColors.AlertRed, onClick = onAcknowledge)
            }

            SectionLabel(copy.escalationChainLabel, Modifier.padding(top = 24.dp, bottom = 10.dp))
            EscalationRow(Icons.Filled.CheckCircle, FamilyColors.Blue, copy.seniorPromptedAt(formatClockTime(alert.createdAt)))
            EscalationRow(Icons.Filled.HourglassEmpty, FamilyColors.Blue, copy.escalationFamilyNotifiedPending)
            EscalationRow(Icons.Filled.AccountBalance, FamilyColors.Blue, copy.barangayWindow(BARANGAY_WINDOW_MINUTES))

            SectionLabel(copy.lastKnownLocationLabel, Modifier.padding(top = 24.dp, bottom = 10.dp))
            AlertLocationMap(
                clusterId = alert.locationClusterId,
                registeredAddress = senior.address
            )
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
    val copy = LocalFamilyCopy.current
    val alreadyDispatched = alert.status == "escalated"
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(FamilyColors.Orange).padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(copy.youAcknowledgedHeader, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
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
            Text(copy.alertAcknowledgedTitle, color = FamilyColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text(
                if (alreadyDispatched) copy.barangayDispatchedBody(senior.firstName)
                else copy.barangayWillNotifyBody,
                color = FamilyColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (!alreadyDispatched) {
                SectionInfoBox(
                    text = copy.barangayNotifyWindow(BARANGAY_WINDOW_MINUTES),
                    bg = FamilyColors.OrangeBg,
                    textColor = FamilyColors.WarningText,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }

            SectionLabel(copy.locationLabel, Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp))
            AlertLocationMap(
                clusterId = alert.locationClusterId,
                registeredAddress = senior.address
            )

            SectionLabel(copy.nextStepsLabel, Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp))

            ColorPillButton(copy.callName(senior.firstName), color = FamilyColors.SuccessGreen, icon = Icons.Filled.Phone, onClick = onCallSenior)
            Spacer(Modifier.height(12.dp))
            ColorPillButton(copy.navigateToSeniorLocation, color = FamilyColors.Blue, icon = Icons.Filled.Navigation, onClick = onNavigate)
            Spacer(Modifier.height(12.dp))
            if (!alreadyDispatched) {
                ColorPillButton(copy.dispatchBarangayResponders, color = FamilyColors.Orange, icon = Icons.Filled.AccountBalance, onClick = onDispatch)
                Spacer(Modifier.height(12.dp))
            }
            OutlinePillButton(copy.markResolvedSafe(genderPronoun(senior)), onClick = onMarkResolved)
        }
    }
}

@Composable
private fun CallSeniorContent(senior: SeniorDto, onBack: () -> Unit) {
    val copy = LocalFamilyCopy.current
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader(copy.callSeniorHeader, FamilyColors.HeaderBlue, onBack)
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

            SectionLabel(copy.contactInformationLabel, Modifier.padding(top = 24.dp, bottom = 10.dp))
            Column(modifier = Modifier.fillMaxWidth().border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(14.dp))) {
                ContactInfoRow(Icons.Filled.Phone, copy.phoneLabel, senior.mobileNumber)
                androidx.compose.material3.HorizontalDivider(color = FamilyColors.FieldBorder)
                ContactInfoRow(Icons.Filled.Navigation, copy.homeAddressLabel, senior.address)
            }

            Spacer(Modifier.height(24.dp))
            ColorPillButton(
                copy.callNowButton,
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
    val copy = LocalFamilyCopy.current
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader(copy.alertLocationHeader, FamilyColors.HeaderBlue, onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            SectionInfoBox(
                text = copy.lastKnownLocationCaptured(formatClockTime(alert.createdAt)),
                bg = FamilyColors.AlertRedBg,
                textColor = FamilyColors.AlertRed
            )
            Spacer(Modifier.height(16.dp))
            AlertLocationMap(
                clusterId = alert.locationClusterId,
                registeredAddress = senior.address,
                height = 220.dp,
                interactive = true
            )
            Spacer(Modifier.height(20.dp))
            // Routes to the location captured for THIS alert when there is one (CLAUDE.md §11 —
            // one fix per alert, decoded here from its geohash cell), so help goes where the
            // senior actually was. Falls back to the registered home address only when no fix
            // was captured. The map above already draws the same cell.
            val alertCell = alert.locationClusterId?.let(Geohash::decode)
            ColorPillButton(
                if (alertCell != null) copy.navigateHereButton else copy.navigateToHomeAddressButton,
                color = FamilyColors.Blue,
                icon = Icons.Filled.Navigation,
                onClick = { openMaps(context, alertCell, senior.address) }
            )
        }
    }
}

/**
 * Opens a maps app on the alert's captured location (a decoded geohash [cell]) if there is one,
 * otherwise on the senior's registered [address].
 *
 * Tries a `geo:` intent first — it lets the OS offer a chooser when several maps apps are
 * installed — then falls back to a Google Maps web URL, which a browser can always handle. Both
 * are wrapped: a device with no maps app AND no browser must not crash the app from a tap on a
 * button during an emergency (a bare emulator is exactly that device).
 */
private fun openMaps(context: android.content.Context, cell: Geohash.Cell?, address: String) {
    val geo: Uri
    val web: Uri
    if (cell != null) {
        val q = "${cell.centerLatitude},${cell.centerLongitude}"
        geo = Uri.parse("geo:$q?q=$q")
        web = Uri.parse("https://www.google.com/maps/search/?api=1&query=$q")
    } else {
        geo = Uri.parse("geo:0,0?q=" + Uri.encode(address))
        web = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(address))
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
        .recoverCatching { context.startActivity(Intent(Intent.ACTION_VIEW, web)) }
}

@Composable
private fun DispatchBarangayContent(
    seniorName: String,
    onBack: () -> Unit,
    onDispatch: (reason: String, notes: String?) -> Unit
) {
    val copy = LocalFamilyCopy.current
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader(copy.dispatchBarangayHeader, FamilyColors.HeaderBlue, onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            SectionInfoBox(
                text = copy.dispatchExplainer(seniorName),
                bg = FamilyColors.OrangeBg,
                textColor = FamilyColors.WarningText
            )

            SectionLabel(copy.reasonForDispatchLabel, Modifier.padding(top = 24.dp, bottom = 10.dp))
            // DISPATCH_REASON_CODES are the English values sent to the server; only the
            // displayed label (copy.dispatchReasonLabel) changes with the account's language.
            DISPATCH_REASON_CODES.forEach { reasonCode ->
                val selected = selectedReason == reasonCode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .border(1.dp, if (selected) FamilyColors.Orange else FamilyColors.FieldBorder, RoundedCornerShape(14.dp))
                        .clickable { selectedReason = reasonCode }
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                        null,
                        tint = if (selected) FamilyColors.Orange else FamilyColors.TextSecondary
                    )
                    Text(copy.dispatchReasonLabel(reasonCode), color = FamilyColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text(copy.additionalNotesPlaceholder) },
                modifier = Modifier.fillMaxWidth().height(110.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FamilyColors.Orange,
                    unfocusedBorderColor = FamilyColors.FieldBorder
                )
            )

            Spacer(Modifier.height(20.dp))
            ColorPillButton(
                copy.dispatchNowButton,
                color = FamilyColors.Orange,
                icon = Icons.Filled.AccountBalance,
                onClick = { selectedReason?.let { onDispatch(it, notes.ifBlank { null }) } }
            )
        }
    }
}

@Composable
private fun ResolvedContent(senior: SeniorDto, summary: ResolvedSummary, onDone: () -> Unit) {
    val copy = LocalFamilyCopy.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader(copy.alertResolvedHeader, FamilyColors.HeaderBlue, onDone)
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
            Text(copy.isSafe(senior.firstName), color = FamilyColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text(copy.closedByYou(summary.resolvedAt), color = FamilyColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

            SectionInfoBox(
                text = copy.allNotifiedResolved,
                bg = FamilyColors.SafeGreenBg,
                textColor = FamilyColors.SuccessGreen,
                modifier = Modifier.padding(top = 18.dp)
            )

            SectionLabel(copy.incidentSummaryLabel, Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp))
            Column(modifier = Modifier.fillMaxWidth().background(FamilyColors.FieldBackground, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp)) {
                SummaryRow(copy.summaryAlertId, summary.alertShortId)
                SummaryRow(copy.summaryTriggered, summary.triggeredAt)
                SummaryRow(copy.summaryResolved, summary.resolvedAt)
                SummaryRow(copy.summaryDuration, copy.durationMinutes(summary.durationMinutes))
                SummaryRow(copy.summaryResolvedBy, summary.resolvedBy, isLast = true)
            }
        }
    }
}

/** Every alert across every linked senior — reached from All Clear's "View alert history"
 *  link. Each tile is tappable, opening [AlertHistoryDetailContent] for that one alert. */
@Composable
private fun AlertHistoryListContent(
    items: List<AlertHistoryItem>,
    onBack: () -> Unit,
    onSelect: (AlertHistoryItem) -> Unit
) {
    val copy = LocalFamilyCopy.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader(copy.alertHistoryTitle, FamilyColors.HeaderBlue, onBack)
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(copy.alertHistoryEmpty, color = FamilyColors.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
            ) {
                items(items, key = { it.alert.syncId }) { entry ->
                    AlertHistoryTile(entry, onClick = { onSelect(entry) })
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AlertHistoryTile(entry: AlertHistoryItem, onClick: () -> Unit) {
    val copy = LocalFamilyCopy.current
    val accent = riskColor(entry.alert.riskLevel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FamilyColors.FieldBackground)
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).background(accent, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                copy.triggerShortLabel(entry.alert.triggerType),
                color = FamilyColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(entry.senior.firstName, color = FamilyColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            Text(
                formatClockTime(entry.alert.createdAt) + " · " + copy.recentAlertChipLabel(entry.alert.status),
                color = FamilyColors.TextSecondary,
                fontSize = 12.sp
            )
        }
        RiskDot(entry.alert.riskLevel, copy)
    }
}

@Composable
private fun RiskDot(riskLevel: String, copy: FamilyStrings.Copy) {
    val accent = riskColor(riskLevel)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            riskLevel.replaceFirstChar { it.uppercase() },
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun riskColor(riskLevel: String): Color = when (riskLevel) {
    "high" -> FamilyColors.AlertRed
    "medium" -> FamilyColors.Orange
    else -> FamilyColors.SuccessGreen
}

/**
 * Read-only detail for one past alert, tapped from [AlertHistoryListContent]. No action buttons
 * -- unlike [AlertDetailContent]/[AcknowledgedContent] this is history, not a live incident, so
 * Acknowledge/Dispatch/Call/Resolve would all be acting on something already over. Reuses the
 * same incident-summary shape [ResolvedContent] shows for a just-closed alert (built for any
 * alert via [FamilyAlertsViewModel.summaryFor]), plus the reason text and location map.
 */
@Composable
private fun AlertHistoryDetailContent(item: AlertHistoryItem, summary: ResolvedSummary, onBack: () -> Unit) {
    val copy = LocalFamilyCopy.current
    val alert = item.alert
    val senior = item.senior
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader(copy.alertHistoryDetailTitle, FamilyColors.HeaderBlue, onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    copy.triggerShortLabel(alert.triggerType),
                    color = FamilyColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                RiskDot(alert.riskLevel, copy)
            }
            Text(senior.firstName, color = FamilyColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(16.dp))
                    .background(FamilyColors.FieldBackground, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text(copy.alertReasonLabel, color = FamilyColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    copy.alertReasonText(alert.triggerType),
                    color = FamilyColors.TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            SectionLabel(copy.incidentSummaryLabel, Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp))
            Column(modifier = Modifier.fillMaxWidth().background(FamilyColors.FieldBackground, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp)) {
                SummaryRow(copy.summaryTriggered, summary.triggeredAt)
                SummaryRow(copy.summaryResolved, summary.resolvedAt)
                SummaryRow(copy.summaryDuration, copy.durationMinutes(summary.durationMinutes))
                SummaryRow(copy.summaryResolvedBy, summary.resolvedBy, isLast = true)
            }
            // The location shows only while the alert is still open -- the family may still need
            // to reach the senior. Once it is resolved or marked a false positive there is
            // nothing left to act on, so the stored position is not shown again.
            if (alert.status != "resolved" && alert.status != "false_positive") {
                SectionLabel(copy.lastKnownLocationLabel, Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp))
                AlertLocationMap(
                    clusterId = alert.locationClusterId,
                    registeredAddress = senior.address
                )
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
