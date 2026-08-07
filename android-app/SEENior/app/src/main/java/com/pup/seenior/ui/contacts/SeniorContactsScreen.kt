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
import com.pup.seenior.ui.theme.SeniorColors

private val RemoveRed = Color(0xFFDA4A4A)
private val OnlineGreen = Color(0xFF57B84E)

@Composable
fun SeniorContactsScreen(
    viewModel: SeniorContactsViewModel = viewModel(),
    onGoToInvite: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        GreenHeader(icon = { Icon(Icons.Filled.Contacts, null, tint = Color.White) }, title = "Contacts")

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
                message = viewModel.error ?: "Could not load your contacts.",
                onRetry = { viewModel.refresh() }
            )
            viewModel.contacts.isEmpty() -> EmptyState(onGoToInvite)
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
                Text(contact.fullName ?: "Family member", color = SeniorColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(relationshipDisplay(contact.relationshipLabel), color = SeniorColors.Green, fontSize = 16.sp)
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
                Text("Phone", color = SeniorColors.TextSecondary, fontSize = 13.sp)
                Text(contact.phone ?: "—", color = SeniorColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Status", color = SeniorColors.TextSecondary, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(OnlineGreen, CircleShape))
                    Text("Online Now", color = OnlineGreen, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
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
            Text("Remove Contact", color = RemoveRed, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (showConfirm) {
        val name = contact.fullName ?: "this family member"
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Remove $name?") },
            text = {
                Text(
                    "They will stop receiving your alerts, and you will disappear from their " +
                        "app too. You can connect again later with a new invite code."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onRemove()
                }) { Text("Remove", color = RemoveRed) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", color = SeniorColors.TextSecondary)
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
                "Could not load your contacts",
                color = SeniorColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "$message Your family members are still connected.",
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
                Text("Try again", color = SeniorColors.Green, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyState(onGoToInvite: () -> Unit) {
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
            Text("No family connected yet", color = SeniorColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Share your code with a trusted family member so they can receive alerts and updates.",
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
                Text("Invite tab", color = SeniorColors.Green, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun initials(name: String?): String {
    if (name.isNullOrBlank()) return "?"
    return name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

private fun relationshipDisplay(label: String?): String =
    label?.replaceFirstChar { it.uppercase() } ?: "Family"
