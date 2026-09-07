package com.pup.seenior.ui.contacts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.network.dto.FamilyContactDto
import com.pup.seenior.ui.LocalProfileCopy
import com.pup.seenior.ui.theme.SeniorColors

private val RemoveRed = Color(0xFFDA4A4A)
private val OnlineGreen = Color(0xFF57B84E)
private val ActiveAmber = Color(0xFFE0952A)
private val InactiveGrey = Color(0xFF8A8F98)

/** Minutes since [iso] (a naive-UTC server timestamp), or null when it is null/unparseable. */
private fun minutesSince(iso: String?): Long? {
    iso ?: return null
    val instant = runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching {
            java.time.LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC)
        }.getOrNull()
        ?: return null
    return java.time.Duration.between(instant, java.time.Instant.now()).toMinutes().coerceAtLeast(0)
}

/** Green when the family opened their app in the last 15 min, amber within a day, grey beyond. */
private fun presenceColor(minutesAgo: Long?): Color = when {
    minutesAgo == null -> InactiveGrey
    minutesAgo < 15L -> OnlineGreen
    minutesAgo < 60L * 24 -> ActiveAmber
    else -> InactiveGrey
}

@Composable
/**
 * @param inviteActionLabel what the empty state's button is called. It says "Invite tab"
 *   when this screen is a tab, because that is literally where it sends you — but a senior
 *   living alone reaches this from Profile and has no Invite tab, and a button naming a
 *   tab that is not on their screen is worse than no button.
 * @param onBack non-null only when hosted under Profile; see [GreenHeader].
 */
fun SeniorContactsScreen(
    viewModel: SeniorContactsViewModel = viewModel(),
    onGoToInvite: () -> Unit,
    inviteActionLabel: String? = null,
    onBack: (() -> Unit)? = null
) {
    val copy = LocalProfileCopy.current
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        GreenHeader(
            icon = { Icon(Icons.Filled.Contacts, null, tint = Color.White) },
            title = copy.contactsHeader,
            onBack = onBack
        )

        // Only surface the error inline when there's still a list under it; a failed load with
        // nothing to show gets the full CouldNotLoadState below instead.
        viewModel.error?.takeIf { viewModel.contacts.isNotEmpty() }?.let {
            Text(it, color = RemoveRed, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }

        when {
            viewModel.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SeniorColors.Green)
            }
            // Order matters: a failed load must never fall through to EmptyState, or a
            // temporary network problem looks like the contacts were deleted.
            viewModel.loadFailed -> CouldNotLoadState(
                message = viewModel.error ?: copy.contactsLoadFailedInline,
                onRetry = { viewModel.refresh() }
            )
            viewModel.contacts.isEmpty() -> EmptyState(inviteActionLabel ?: copy.inviteTabLabel, onGoToInvite)
            else -> LazyColumn(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
            ) {
                items(viewModel.contacts, key = { it.id }) { contact ->
                    ContactCard(contact) { viewModel.removeContact(contact.id) }
                }
            }
        }
    }
}

@Composable
private fun ContactCard(contact: FamilyContactDto, onRemove: () -> Unit) {
    val copy = LocalProfileCopy.current
    // The family side has always confirmed before unlinking; this side removed on the
    // raw tap, so one stray touch dropped a family contact with no warning and no undo.
    var showConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .border(2.dp, SeniorColors.GreenBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(contact.fullName), color = SeniorColors.Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(contact.fullName ?: copy.familyMemberFallback, color = SeniorColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(relationshipDisplay(contact.relationshipLabel, copy.familyFallbackLabel), color = SeniorColors.Green, fontSize = 16.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .background(SeniorColors.FieldBackground, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(copy.phone, color = SeniorColors.TextSecondary, fontSize = 13.sp)
                Text(contact.phone ?: "—", color = SeniorColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(copy.status, color = SeniorColors.TextSecondary, fontSize = 13.sp)
                val minutesAgo = remember(contact.lastActiveAt) { minutesSince(contact.lastActiveAt) }
                val dot = presenceColor(minutesAgo)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(dot, CircleShape))
                    Text(
                        copy.contactActive(minutesAgo),
                        color = dot,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .height(52.dp)
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(14.dp))
                .clickable { showConfirm = true },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Delete, null, tint = RemoveRed, modifier = Modifier.size(20.dp))
            Text(copy.removeContact, color = RemoveRed, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (showConfirm) {
        val name = contact.fullName ?: copy.thisFamilyMember
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(copy.removeTitle(name)) },
            text = {
                Text(copy.removeBody)
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onRemove()
                }) { Text(copy.remove, color = RemoveRed) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(copy.cancel, color = SeniorColors.TextSecondary)
                }
            }
        )
    }
}

/** Shown when the contact list could not be fetched. Deliberately worded so the senior knows
 *  their family is still linked and only the *loading* failed — the previous behaviour reused
 *  the "no family connected yet" empty state here, which looked like the pairing was lost. */
@Composable
private fun CouldNotLoadState(message: String, onRetry: () -> Unit) {
    val copy = LocalProfileCopy.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(64.dp).background(SeniorColors.FieldBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CloudOff, null, tint = SeniorColors.TextSecondary, modifier = Modifier.size(30.dp))
            }
            Text(
                copy.contactsLoadFailedTitle,
                color = SeniorColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                message + " " + copy.contactsStillConnected,
                color = SeniorColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .height(52.dp)
                    .border(1.dp, SeniorColors.GreenBorder, RoundedCornerShape(14.dp))
                    .clickable(onClick = onRetry),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(copy.tryAgain, color = SeniorColors.Green, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyState(inviteActionLabel: String, onGoToInvite: () -> Unit) {
    val copy = LocalProfileCopy.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(64.dp).background(SeniorColors.FieldBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Favorite, null, tint = SeniorColors.TextSecondary, modifier = Modifier.size(30.dp))
            }
            Text(copy.noFamilyTitle, color = SeniorColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text(
                copy.noFamilyBody,
                color = SeniorColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            // Said plainly rather than left to be inferred. With nobody on the family tier
            // the escalation chain really does run senior -> barangay, and a senior who is
            // deciding whether to bother adding a contact deserves to know that is what
            // happens today — not to be nudged with an implied warning that they are
            // unprotected. They are not; the barangay tier is always there.
            Text(
                copy.noFamilyBarangayNote,
                color = SeniorColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .height(52.dp)
                    .border(1.dp, SeniorColors.GreenBorder, RoundedCornerShape(14.dp))
                    .clickable(onClick = onGoToInvite),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(inviteActionLabel, color = SeniorColors.Green, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun initials(name: String?): String {
    if (name.isNullOrBlank()) return "?"
    return name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

private fun relationshipDisplay(label: String?, fallback: String): String =
    label?.replaceFirstChar { it.uppercase() } ?: fallback
