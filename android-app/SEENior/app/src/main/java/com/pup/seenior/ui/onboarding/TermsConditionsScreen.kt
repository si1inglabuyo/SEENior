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
import com.pup.seenior.ui.LocalOnboardingCopy
import com.pup.seenior.ui.onboarding.components.OnboardingHeading
import com.pup.seenior.ui.onboarding.components.OnboardingTopBar
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton
import com.pup.seenior.ui.theme.SeniorColors

@Composable
fun TermsConditionsScreen(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val copy = LocalOnboardingCopy.current
    var acceptedTerms by remember { mutableStateOf(false) }
    var acceptedPrivacy by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            OnboardingTopBar(currentStep = 2, onBack = onBack)
            OnboardingHeading(
                title = copy.termsTitle,
                subtitle = copy.termsSubtitle
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp)
            ) {
                copy.termsSections.forEach { section ->
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
                prefix = copy.termsConsentPrefix,
                linkText = copy.termsConsentLink,
                suffix = copy.termsConsentSuffix
            )
            ConsentRow(
                checked = acceptedPrivacy,
                onCheckedChange = { acceptedPrivacy = it },
                prefix = copy.privacyConsentPrefix,
                linkText = copy.privacyConsentLink,
                suffix = copy.privacyConsentSuffix
            )

            PrimaryPillButton(
                text = copy.next,
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
