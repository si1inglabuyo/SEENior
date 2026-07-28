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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
fun ConnectedScreen(viewModel: FamilyPairingViewModel, onGoHome: () -> Unit) {
    val senior: SeniorDto = viewModel.verifiedSenior ?: return

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

        Text("Connected", color = FamilyColors.Blue, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(
            "You are now monitoring ${senior.firstName}. You'll receive alerts if anything unusual is detected.",
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

        Text(
            "What is your relationship to the senior?",
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
                        .clickable { viewModel.selectedRelationship = rel }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(rel.replaceFirstChar { it.uppercase() }, color = if (selected) FamilyColors.Blue else FamilyColors.TextPrimary, fontSize = 15.sp)
                }
            }
        }

        viewModel.error?.let {
            Text(it, color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(Modifier.height(24.dp))
        BluePillButton(
            text = if (viewModel.isPairing) "CONNECTING…" else "Go to Home",
            enabled = viewModel.selectedRelationship != null && !viewModel.isPairing,
            onClick = { viewModel.pair(onGoHome) }
        )
    }
}

private fun initials(first: String, last: String): String =
    "${first.firstOrNull()?.uppercase() ?: ""}${last.firstOrNull()?.uppercase() ?: ""}"
