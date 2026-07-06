package com.pup.seenior.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.ui.onboarding.components.RoleCard
import com.pup.seenior.ui.theme.SeniorColors

@Composable
fun RoleSelectionScreen(
    onSeniorSelected: () -> Unit,
    onFamilyMemberSelected: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color(0xFF6FA8DC))) {
                        append("Welcome ")
                    }
                    withStyle(style = androidx.compose.ui.text.SpanStyle(color = SeniorColors.Green)) {
                        append("To ")
                    }
                    withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color(0xFFE8A33D))) {
                        append("SEENior")
                    }
                },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose your role",
                color = SeniorColors.TextSecondary,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            RoleCard(
                borderColor = SeniorColors.GreenBorder,
                backgroundColor = SeniorColors.GreenLightBg,
                icon = { ShadowedIcon(Icons.Filled.Person) },
                title = "I am a Senior",
                bullets = listOf(
                    "Requires safety and wellness support.",
                    "Needs daily health monitoring.",
                    "Stays connected with family members.",
                    "Emergency alerts."
                ),
                onClick = onSeniorSelected
            )

            Column(modifier = Modifier.padding(top = 24.dp)) {
                RoleCard(
                    borderColor = SeniorColors.BlueBorder,
                    backgroundColor = SeniorColors.BlueLightBg,
                    icon = { ShadowedIcon(Icons.Filled.People) },
                    title = "Family Member",
                    bullets = listOf(
                        "Monitors senior status and activity.",
                        "Receives safety and emergency alerts.",
                        "Has alert-based location access.",
                        "Maintains connection and care remotely."
                    ),
                    onClick = onFamilyMemberSelected
                )
            }
        }
    }
}

@Composable
private fun ShadowedIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFBFBFBF),
            modifier = Modifier
                .size(44.dp)
                .offset(x = 3.dp, y = 3.dp)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF1A1A1A),
            modifier = Modifier.size(44.dp)
        )
    }
}
