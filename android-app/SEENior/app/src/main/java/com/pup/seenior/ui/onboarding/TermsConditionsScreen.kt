package com.pup.seenior.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.ui.onboarding.components.OnboardingHeading
import com.pup.seenior.ui.onboarding.components.OnboardingTopBar
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton
import com.pup.seenior.ui.theme.SeniorColors

private data class TermsSection(val heading: String, val body: String? = null, val bullets: List<String>? = null)

private val sections = listOf(
    TermsSection(
        "1. Acceptance of Terms",
        body = "By creating an account and using SEENior, you agree to be bound by these Terms of Use. If you do not agree to these terms, please do not use our services."
    ),
    TermsSection(
        "2. Description of Service",
        body = "SEENior is a passive behavioral monitoring application that uses your smartphone's built-in sensors to establish a daily routine baseline and detect significant deviations that may indicate an emergency."
    ),
    TermsSection(
        "3. User Eligibility",
        body = "The SEENior senior app is intended for use by senior citizens (60 years and older) residing in the Philippines, consistent with RA 9994 (Expanded Senior Citizens Act of 2010). Use by persons below this age threshold is permitted only for testing purposes by the development team."
    ),
    TermsSection(
        "4. User Responsibilities",
        bullets = listOf(
            "Keep your phone charged and with you for the monitoring system to function reliably.",
            "Provide accurate personal information during account setup."
        )
    )
)

@Composable
fun TermsConditionsScreen(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    var acceptedTerms by remember { mutableStateOf(false) }
    var acceptedPrivacy by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            OnboardingTopBar(currentStep = 2, onBack = onBack)
            OnboardingHeading(
                title = "Terms & Conditions",
                subtitle = "Review our Terms and Conditions to continue using the app."
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp)
            ) {
                sections.forEach { section ->
                    Text(
                        text = section.heading,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = SeniorColors.TextPrimary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    section.body?.let {
                        Text(it, fontSize = 15.sp, color = SeniorColors.TextSecondary, lineHeight = 22.sp)
                    }
                    section.bullets?.forEach { bullet ->
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            Text("•  ", color = SeniorColors.TextSecondary, fontSize = 15.sp)
                            Text(bullet, color = SeniorColors.TextSecondary, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = SeniorColors.FieldBorder)

            ConsentRow(
                checked = acceptedTerms,
                onCheckedChange = { acceptedTerms = it },
                prefix = "I acknowledge that I have carefully read and accepted the ",
                linkText = "Terms and Conditions",
                suffix = " of SEENior."
            )
            ConsentRow(
                checked = acceptedPrivacy,
                onCheckedChange = { acceptedPrivacy = it },
                prefix = "I have read and agree to the ",
                linkText = "Privacy Policy",
                suffix = " and consent to the collection of my data as described, under RA 10173."
            )

            PrimaryPillButton(
                text = "NEXT",
                onClick = onNext,
                enabled = acceptedTerms && acceptedPrivacy,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    prefix: String,
    linkText: String,
    suffix: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = SeniorColors.Green)
        )
        Text(
            text = buildAnnotatedString {
                append(prefix)
                withStyle(SpanStyle(color = SeniorColors.Green, textDecoration = TextDecoration.Underline)) {
                    append(linkText)
                }
                append(suffix)
            },
            fontSize = 14.sp,
            color = SeniorColors.TextPrimary,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 12.dp, end = 4.dp)
        )
    }
}
