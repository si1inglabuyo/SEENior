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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.network.dto.SeniorDto

private val RELATIONSHIPS = listOf("daughter", "son", "grandchild", "caregiver", "husband", "wife")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ConnectedScreen(
    viewModel: FamilyPairingViewModel,
    onGoHome: () -> Unit,
    onAddAnother: (() -> Unit)? = null
) {
    val senior: SeniorDto = viewModel.verifiedSenior ?: return
    val copy = LocalFamilyCopy.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .size(72.dp)
                .border(3.dp, FamilyColors.SuccessGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, null, tint = FamilyColors.SuccessGreen, modifier = Modifier.size(36.dp))
        }

        Text(copy.connectedTitle, color = FamilyColors.Blue, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(
            copy.nowMonitoring(senior.firstName),
            color = FamilyColors.TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Senior card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .background(FamilyColors.BlueLightBg, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(FamilyColors.Blue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(senior.firstName, senior.lastName), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text("${senior.firstName} ${senior.lastName}", color = FamilyColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${senior.age} · ${senior.gender.replaceFirstChar { it.uppercase() }} · ${senior.barangay}", color = FamilyColors.TextSecondary, fontSize = 14.sp)
            }
        }

        // Declarative fill-in-the-blank on purpose, not an open question. "What is your
        // relationship to the senior?" reads two opposite ways: the chips below answer it as
        // "I am the senior's ___" (their frame), but someone typing into the blank field
        // underneath just as naturally answers "he/she is my ___" instead -- literally what
        // happened on 2026-09-06, where a contact typed "Father" meaning the senior is his
        // father, and the senior-facing contact list then displayed it as if the contact were
        // the senior's father. "You are the senior's:" fixes the direction for both paths at
        // once, since only one answer to the fill-in-the-blank is grammatical.
        Text(
            copy.youAreSeniorsLabel,
            color = FamilyColors.Blue,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 8.dp)
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RELATIONSHIPS.forEach { rel ->
                val selected = viewModel.selectedRelationship == rel
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .background(if (selected) FamilyColors.BlueLightBg else Color.White, RoundedCornerShape(20.dp))
                        .border(1.dp, if (selected) FamilyColors.Blue else FamilyColors.FieldBorder, RoundedCornerShape(20.dp))
                        .clickable { viewModel.chooseRelationship(rel) }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    // rel (the value sent to the server as relationship_label) stays English --
                    // only the displayed chip label varies with this account's language. See
                    // FamilyStrings.Copy.relationshipLabel's kdoc for why.
                    Text(copy.relationshipLabel(rel), color = if (selected) FamilyColors.Blue else FamilyColors.TextPrimary, fontSize = 15.sp)
                }
            }
        }

        // "Other", per the design: a free-text field under the chips rather than a seventh chip.
        // Six labels cannot cover how Filipino families actually describe themselves — niece,
        // neighbour, kapitbahay — and a pairing that cannot be completed because none of the
        // words fit is a worse outcome than an unusual label in the database.
        OutlinedTextField(
            value = viewModel.otherRelationship,
            onValueChange = { viewModel.typeOtherRelationship(it) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            // Examples in the same direction as the chips above (niece, neighbor -- not "aunt",
            // "landlord"), so a free-typed answer keeps the fill-in-the-blank grammatical instead
            // of silently reversing it.
            placeholder = { Text(copy.otherRelationshipPlaceholder) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FamilyColors.Blue,
                unfocusedBorderColor = FamilyColors.FieldBorder
            )
        )

        viewModel.error?.let {
            Text(copy.errorMessage(it) ?: "", color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(Modifier.height(24.dp))
        BluePillButton(
            text = if (viewModel.isPairing) copy.connectingEllipsis else copy.goToHomeButton,
            enabled = viewModel.relationshipLabel != null && !viewModel.isPairing,
            onClick = { viewModel.pair(onGoHome) }
        )

        if (onAddAnother != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(50.dp)
                    .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(14.dp))
                    .clickable(enabled = !viewModel.isPairing) {
                        viewModel.pair(onAddAnother)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, null, tint = FamilyColors.Blue, modifier = Modifier.size(18.dp))
                Text(
                    copy.addAnotherSenior,
                    color = FamilyColors.Blue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    // Shown once the pairing has actually gone through on the server — the flow otherwise
    // navigated straight on, so nothing confirmed the link had been made.
    viewModel.pairedSeniorName?.let { name ->
        val displayName = name.ifBlank { copy.theSeniorFallback }
        AlertDialog(
            onDismissRequest = viewModel::confirmPairSuccess,
            confirmButton = {
                TextButton(onClick = viewModel::confirmPairSuccess) {
                    Text(copy.doneButton, color = FamilyColors.Blue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = FamilyColors.SuccessGreen) },
            title = { Text(copy.linkedSuccessTitle, fontSize = 19.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(copy.linkedSuccessBody(displayName), fontSize = 15.sp)
            }
        )
    }
}

private fun initials(first: String, last: String): String =
    "${first.firstOrNull()?.uppercase() ?: ""}${last.firstOrNull()?.uppercase() ?: ""}"
