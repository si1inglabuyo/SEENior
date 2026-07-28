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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.session.FamilySession

/**
 * Family landing after pairing. Shows the linked senior (or the "no one linked yet" empty
 * state). The rich dashboard with battery/risk tiles is a separate pipeline — this covers
 * the contacts-pipeline slice: confirming the pairing landed.
 */
@Composable
fun FamilyHomeScreen(onLinkSenior: () -> Unit) {
    val context = LocalContext.current
    val seniorName = FamilySession.getSeniorName(context)
    val relationship = FamilySession.getRelationship(context)

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
            Text("Family", color = Color.White, fontSize = 16.sp)
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Hi there,", color = FamilyColors.Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
            Text("You're all set today", color = FamilyColors.TextSecondary, fontSize = 15.sp)

            Text("YOUR SENIOR", color = FamilyColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))

            if (seniorName == null) {
                EmptyLinkCard(onLinkSenior)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, FamilyColors.BlueBorder, RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).background(FamilyColors.Blue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initials(seniorName), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(seniorName, color = FamilyColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(relationship?.replaceFirstChar { it.uppercase() } ?: "Family", color = FamilyColors.TextSecondary, fontSize = 14.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.background(Color(0xFFE7F5E6), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).background(FamilyColors.SuccessGreen, CircleShape))
                        Text("Online", color = FamilyColors.SuccessGreen, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLinkCard(onLinkSenior: () -> Unit) {
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
        Text("No one linked yet", color = FamilyColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Link your senior family member so you can keep an eye on them and receive alerts.",
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
            Text("Link a senior now", color = FamilyColors.Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
