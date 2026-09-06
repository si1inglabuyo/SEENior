package com.pup.seenior.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.R
import com.pup.seenior.ui.onboarding.components.PrimaryPillButton
import com.pup.seenior.ui.LocalInfoCopy
import com.pup.seenior.ui.OnboardingStrings
import com.pup.seenior.ui.LocalProfileCopy
import com.pup.seenior.ui.theme.SeniorColors
import com.pup.seenior.ui.onboarding.OnboardingOptions
import androidx.compose.material3.RadioButton

// TODO: replace both with the real support details before the demo — these are the
// placeholders straight out of designs/senior/profile.
private const val SUPPORT_PHONE = "+63 000 000 0000"
private const val SUPPORT_EMAIL = "seenior.support@example.com"

internal val ErrorRed = Color(0xFFCC3333)

// ---------------------------------------------------------------- About this app

@Composable
fun SeniorAboutScreen(onBack: () -> Unit) {
    val copy = LocalInfoCopy.current
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    InfoScaffold(title = copy.aboutTitle, onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.logo_seenior),
                contentDescription = "SEENior",
                modifier = Modifier.size(96.dp)
            )
            Text(copy.version(version), color = SeniorColors.TextSecondary, fontSize = 15.sp)
        }

        InfoCard(heading = copy.missionHeading) {
            InfoBody(copy.missionBody)
        }
        InfoCard(heading = copy.whoWeAreHeading) {
            InfoBody(copy.whoWeAreBody1)
            Spacer(Modifier.height(12.dp))
            InfoBody(copy.whoWeAreBody2)
        }
        InfoCard(heading = copy.valuesHeading) {
            InfoBody(copy.valuesList)
            Spacer(Modifier.height(12.dp))
            InfoBody(copy.valuesBody)
        }
    }
}

// ---------------------------------------------------------------- How to use

@Composable
fun SeniorHowToUseScreen(onBack: () -> Unit) {
    val copy = LocalInfoCopy.current
    InfoScaffold(title = copy.howToTitle, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            copy.howToSteps.forEachIndexed { index, step ->
                if (index > 0) {
                    HorizontalDivider(
                        color = SeniorColors.FieldBorder,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.size(32.dp).background(SeniorColors.Green, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${index + 1}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            step.first,
                            color = SeniorColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        InfoBody(step.second, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- FAQs

@Composable
fun SeniorFaqsScreen(onBack: () -> Unit) {
    val copy = LocalInfoCopy.current
    InfoScaffold(title = copy.faqsTitle, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            copy.faqs.forEachIndexed { index, faq ->
                if (index > 0) Spacer(Modifier.height(20.dp))
                Text(
                    faq.question,
                    color = SeniorColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                InfoBody(faq.answer, modifier = Modifier.padding(top = 4.dp))
                faq.note?.let {
                    Text(
                        copy.importantNote,
                        color = ErrorRed,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    InfoBody(it)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Contact support

@Composable
fun SeniorContactSupportScreen(onBack: () -> Unit) {
    val copy = LocalInfoCopy.current
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }

    InfoScaffold(title = copy.supportTitle, onBack = onBack) {
        // ACTION_DIAL, not ACTION_CALL: no CALL_PHONE permission needed, and the senior
        // still confirms the call themselves.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
                .clickable {
                    val dialable = SUPPORT_PHONE.filter { it.isDigit() || it == '+' }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialable")))
                    }
                }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(SeniorColors.GreenLightBg, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Phone, null, tint = SeniorColors.Green, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(copy.callUs, color = SeniorColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(SUPPORT_PHONE, color = SeniorColors.TextSecondary, fontSize = 16.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text(copy.sendMessage, color = SeniorColors.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                copy.messageLabel,
                color = SeniorColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text(copy.messagePlaceholder, color = SeniorColors.TextHint) },
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(fontSize = 18.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = SeniorColors.FieldBackground,
                    focusedContainerColor = SeniorColors.FieldBackground,
                    unfocusedBorderColor = SeniorColors.FieldBorder,
                    focusedBorderColor = SeniorColors.Green,
                    unfocusedTextColor = SeniorColors.TextPrimary,
                    focusedTextColor = SeniorColors.TextPrimary
                )
            )
            Spacer(Modifier.height(16.dp))
            // There is no support-ticket endpoint on the backend, so SEND hands the text to the
            // phone's mail app rather than pretending to submit it somewhere.
            PrimaryPillButton(
                text = copy.send,
                enabled = message.isNotBlank(),
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$SUPPORT_EMAIL")
                        putExtra(Intent.EXTRA_SUBJECT, "SEENior support request")
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    runCatching { context.startActivity(intent) }.onSuccess { message = "" }
                }
            )
        }
    }
}

// ---------------------------------------------------------------- Terms & Privacy

@Composable
fun SeniorTermsScreen(onBack: () -> Unit) {
    val copy = LocalInfoCopy.current
    InfoScaffold(title = copy.termsScaffoldTitle, onBack = onBack) {
        LegalBody(copy.termsBodyTitle, copy.termsSections)
    }
}

@Composable
fun SeniorPrivacyScreen(onBack: () -> Unit) {
    val copy = LocalInfoCopy.current
    InfoScaffold(title = copy.privacyScaffoldTitle, onBack = onBack) {
        LegalBody(copy.privacyBodyTitle, copy.privacySections)
    }
}

@Composable
private fun LegalBody(title: String, sections: List<OnboardingStrings.Section>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = SeniorColors.Green,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        sections.forEach { section ->
            Text(
                section.heading,
                color = SeniorColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
            )
            section.body?.let { InfoBody(it) }
            section.bullets?.forEach { bullet ->
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Text("•  ", color = SeniorColors.TextSecondary, fontSize = 15.sp)
                    InfoBody(bullet)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared bits

/** Green back-header + scrollable white body, shared by every Profile sub-screen. */
/**
 * Lets the senior change their language after onboarding.
 *
 * This exists as well as the onboarding question, not instead of it. The answer is stored per
 * senior in `Senior_Onboarding.language_preference` and never taken from the device locale, so a
 * handset a relative set up in English would otherwise decide, permanently, what an emergency
 * prompt says to someone who reads only Filipino. Correcting that must not require a reinstall.
 *
 * Each option is written in its own language for the same reason it is in onboarding: a senior
 * looking for Filipino has to be able to recognise the word without reading English first.
 */
@Composable
fun SeniorLanguageScreen(viewModel: SeniorProfileViewModel, onBack: () -> Unit) {
    InfoScaffold(title = "Language / Wika", onBack = onBack) {
        InfoCard(heading = "Choose your language") {
            InfoBody("Your wellness check and alerts will use this language.")
            Spacer(modifier = Modifier.height(6.dp))
            InfoBody("Ito ang wikang gagamitin sa pagsusuri ng inyong kalagayan at sa mga alerto.")
            Spacer(modifier = Modifier.height(8.dp))
            OnboardingOptions.languages.forEach { (label, code) ->
                LanguageOption(
                    label = label,
                    selected = viewModel.language == code,
                    onClick = { viewModel.chooseLanguage(code) }
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = SeniorColors.TextPrimary
        )
    }
}

@Composable
private fun InfoScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        GreenBackHeader(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoCard(heading: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SeniorColors.FieldBackground, RoundedCornerShape(20.dp))
            .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(heading, color = SeniorColors.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun InfoBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = SeniorColors.TextSecondary,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        modifier = modifier
    )
}
