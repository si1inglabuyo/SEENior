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
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

        viewModel.error?.let {
            Text(it, color = RemoveRed, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }

        when {
            viewModel.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SeniorColors.Green)
            }
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
                .clickable(onClick = onRemove),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Delete, null, tint = RemoveRed, modifier = Modifier.size(20.dp))
            Text("Remove Contact", color = RemoveRed, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
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
