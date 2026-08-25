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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.network.dto.ContactDto

/** Family "Contacts" tab: the seniors this account is monitoring (designs/family_contact/limit,
 *  designs/family_contact/unlink). Tapping a row opens its detail/unlink screen. */
@Composable
fun FamilyContactsScreen(viewModel: FamilySeniorsViewModel, onLinkSenior: () -> Unit) {
    var selected by remember { mutableStateOf<ContactDto?>(null) }
    val current = selected

    if (current != null) {
        ContactDetailScreen(
            contact = current,
            onBack = { selected = null },
            onUnlinked = { selected = null },
            viewModel = viewModel
        )
    } else {
        ContactsListScreen(viewModel = viewModel, onSelect = { selected = it })
    }
}

@Composable
private fun ContactsListScreen(
    viewModel: FamilySeniorsViewModel,
    onSelect: (ContactDto) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = viewModel.contacts.filter {
        query.isBlank() || "${it.senior.firstName} ${it.senior.lastName}".contains(query, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BlueHeader(Icons.Filled.Contacts, "Contacts")

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search seniors…") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FamilyColors.Blue,
                    unfocusedBorderColor = FamilyColors.FieldBorder
                )
            )
        }

        // Loading and failure are both checked before the empty case: either one showing
        // "No seniors linked yet." looked like the links had been removed.
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                LoadingCard("Loading your seniors…")
            }
        } else if (viewModel.loadFailed) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                CouldNotLoadCard(
                    title = "Could not load your seniors",
                    message = viewModel.error ?: "Could not reach the server.",
                    reassurance = "They are still linked to your account.",
                    onRetry = { viewModel.refresh() }
                )
            }
        } else if (viewModel.contacts.isEmpty() && !viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text("No seniors linked yet.", color = FamilyColors.TextSecondary, fontSize = 15.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
                items(filtered, key = { it.id }) { contact ->
                    ContactRow(contact, onClick = { onSelect(contact) })
                    Spacer(Modifier.height(12.dp))
                }
                if (!viewModel.canLinkMore) {
                    item { MonitoringLimitCard(modifier = Modifier.padding(bottom = 16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FamilyColors.BlueBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(FamilyColors.BlueLightBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(initials(contact.senior.firstName, contact.senior.lastName), color = FamilyColors.Blue, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text("${contact.senior.firstName} ${contact.senior.lastName}", color = FamilyColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "${contact.relationshipLabel?.replaceFirstChar { it.uppercase() } ?: "Family"} · ${formatPhone(contact.senior.mobileNumber)}",
                color = FamilyColors.TextSecondary,
                fontSize = 14.sp
            )
        }
        Icon(Icons.Filled.ChevronRight, null, tint = FamilyColors.TextSecondary)
    }
}

@Composable
private fun ContactDetailScreen(
    contact: ContactDto,
    viewModel: FamilySeniorsViewModel,
    onBack: () -> Unit,
    onUnlinked: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    val senior = contact.senior

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FamilyColors.HeaderBlue)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("${senior.firstName} ${senior.lastName}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // Scrolls, and the header above it does not. A senior's address is a joined
        // PSGC string and can run to several lines, which pushed the Unlink button off the
        // bottom of the display with no way to reach it. Putting the scroll on the outer
        // Column instead would have taken the back arrow with it.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(64.dp).border(2.dp, FamilyColors.Blue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials(senior.firstName, senior.lastName), color = FamilyColors.Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${senior.firstName} ${senior.lastName}",
                    color = FamilyColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    "${contact.relationshipLabel?.replaceFirstChar { it.uppercase() } ?: "Family"} · ${senior.age}",
                    color = FamilyColors.TextSecondary,
                    fontSize = 14.sp
                )
            }

            Text(
                "CONTACT INFORMATION",
                color = FamilyColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
            )

            InfoRow(Icons.Filled.Phone, "Phone", formatPhone(senior.mobileNumber))
            Spacer(Modifier.height(10.dp))
            InfoRow(Icons.Filled.Place, "Home address", senior.address)

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.dp, FamilyColors.ErrorRed, RoundedCornerShape(14.dp))
                    .clickable { showConfirm = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Delete, null, tint = FamilyColors.ErrorRed, modifier = Modifier.size(20.dp))
                Text("Unlink Senior", color = FamilyColors.ErrorRed, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }

            viewModel.error?.let {
                Text(it, color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }

            // Clears the bottom navigation bar, which would otherwise sit on top of the last
            // element once the content is scrolled all the way down.
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Unlink this senior?") },
            text = {
                Text("Are you sure you want to unlink ${senior.firstName} ${senior.lastName}? You will no longer receive alerts and updates from this senior.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.unlink(contact.id, onDone = onUnlinked)
                }) { Text("Unlink", color = FamilyColors.ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel", color = FamilyColors.TextSecondary) }
            }
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(FamilyColors.BlueLightBg, RoundedCornerShape(10.dp)),
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

private fun initials(first: String, last: String): String =
    "${first.firstOrNull()?.uppercase() ?: ""}${last.firstOrNull()?.uppercase() ?: ""}"

/** Display-only grouping of the normalized "09XXXXXXXXX" number, e.g. "0917 123 4567". */
private fun formatPhone(number: String): String {
    if (number.length != 11) return number
    return "${number.substring(0, 4)} ${number.substring(4, 7)} ${number.substring(7, 11)}"
}
