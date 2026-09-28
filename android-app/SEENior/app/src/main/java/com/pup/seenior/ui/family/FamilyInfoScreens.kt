package com.pup.seenior.ui.family

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Family Profile's HELP & INFORMATION sub-screens. Mirrors `ui/profile/SeniorInfoScreens.kt`'s
 * structure (own InfoScaffold/InfoCard/InfoBody, reusing [BackHeader] from
 * `FamilyComponents.kt` for the header rather than duplicating it).
 *
 * See [FamilyInfoStrings]'s kdoc for the two content corrections made against the
 * `designs/family_contact/infos` mockups (How to use, Terms & Privacy).
 */

/*
 * Same reasoning as SeniorInfoScreens.kt's SUPPORT_PHONE/SUPPORT_EMAIL: both were left null
 * until real ones existed, because a placeholder number is worse than no button at all --
 * tapping "Call us" and reaching a dead line spends the exact moment someone decided to ask
 * for help. Both are now real and monitored.
 */
private val FAMILY_SUPPORT_PHONE: String? = "0910 358 4546"
private val FAMILY_SUPPORT_EMAIL: String? = "SEENiorApp@gmail.com"

/** Whether there is anything behind the Profile menu's "Contact support" row. */
internal val FAMILY_SUPPORT_CONTACT_CONFIGURED: Boolean
    get() = FAMILY_SUPPORT_PHONE != null || FAMILY_SUPPORT_EMAIL != null

// ---------------------------------------------------------------- About this app

@Composable
fun FamilyAboutScreen(onBack: () -> Unit) {
    val copy = LocalFamilyInfoCopy.current
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
            Text("SEENior", color = FamilyColors.Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(copy.version(version), color = FamilyColors.TextSecondary, fontSize = 15.sp)
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
fun FamilyHowToUseScreen(onBack: () -> Unit) {
    val copy = LocalFamilyInfoCopy.current
    InfoScaffold(title = copy.howToTitle, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            copy.howToSteps.forEachIndexed { index, step ->
                if (index > 0) {
                    HorizontalDivider(
                        color = FamilyColors.FieldBorder,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.size(32.dp).background(FamilyColors.Blue, CircleShape),
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
                            color = FamilyColors.TextPrimary,
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
fun FamilyFaqsScreen(onBack: () -> Unit) {
    val copy = LocalFamilyInfoCopy.current
    InfoScaffold(title = copy.faqsTitle, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            copy.faqs.forEachIndexed { index, faq ->
                if (index > 0) Spacer(Modifier.height(20.dp))
                Text(
                    faq.question,
                    color = FamilyColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                InfoBody(faq.answer, modifier = Modifier.padding(top = 4.dp))
                faq.note?.let {
                    Text(
                        copy.importantNote,
                        color = FamilyColors.ErrorRed,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    InfoBody(it)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Feedback & requests

/**
 * Star rating + free-text feedback (designs/family_contact/infos). The rating is local UI
 * state only — there is no feedback endpoint on the backend (same gap SeniorInfoScreens.kt's
 * Contact support flags), so Send routes through the same mailto pattern as
 * [FamilyContactSupportScreen] and stays disabled until [FAMILY_SUPPORT_EMAIL] is configured,
 * rather than pretending a tap submits anything.
 */
@Composable
fun FamilyFeedbackScreen(onBack: () -> Unit) {
    val copy = LocalFamilyInfoCopy.current
    val context = LocalContext.current
    var rating by remember { mutableStateOf(0) }
    var improve by remember { mutableStateOf("") }
    var featureRequest by remember { mutableStateOf("") }

    InfoScaffold(title = copy.feedbackTitle, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text(copy.howAreWeDoing, color = FamilyColors.Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(copy.tapAStarToRate, color = FamilyColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            Row {
                (1..5).forEach { star ->
                    IconButton(onClick = { rating = star }) {
                        Icon(
                            if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = if (star <= rating) Color(0xFFF2B84B) else FamilyColors.TextHint,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Text(
                copy.whatCanWeImprove,
                color = FamilyColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = improve,
                onValueChange = { improve = it },
                placeholder = { Text(copy.improvePlaceholder, color = FamilyColors.TextHint) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(12.dp),
                colors = feedbackFieldColors()
            )

            Text(
                copy.featureRequestLabel,
                color = FamilyColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = featureRequest,
                onValueChange = { featureRequest = it },
                placeholder = { Text(copy.featureRequestPlaceholder, color = FamilyColors.TextHint) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(12.dp),
                colors = feedbackFieldColors()
            )

            Spacer(Modifier.height(20.dp))
            val supportEmail = FAMILY_SUPPORT_EMAIL
            val hasContent = rating > 0 || improve.isNotBlank() || featureRequest.isNotBlank()
            BluePillButton(
                text = copy.sendFeedback,
                enabled = supportEmail != null && hasContent,
                onClick = {
                    if (supportEmail == null) return@BluePillButton
                    val body = buildString {
                        append("Rating: ").append(rating).append("/5\n\n")
                        append(copy.whatCanWeImprove).append("\n").append(improve).append("\n\n")
                        append(copy.featureRequestLabel).append("\n").append(featureRequest)
                    }
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$supportEmail")
                        putExtra(Intent.EXTRA_SUBJECT, "SEENior family app feedback")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    runCatching { context.startActivity(intent) }
                        .onSuccess { rating = 0; improve = ""; featureRequest = "" }
                }
            )
            if (supportEmail == null) {
                Text(
                    copy.feedbackNotConnected,
                    color = FamilyColors.TextHint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun feedbackFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = FamilyColors.FieldBackground,
    focusedContainerColor = FamilyColors.FieldBackground,
    unfocusedBorderColor = FamilyColors.FieldBorder,
    focusedBorderColor = FamilyColors.Blue,
    unfocusedTextColor = FamilyColors.TextPrimary,
    focusedTextColor = FamilyColors.TextPrimary
)

// ---------------------------------------------------------------- Contact support

@Composable
fun FamilyContactSupportScreen(onBack: () -> Unit) {
    val copy = LocalFamilyInfoCopy.current
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }

    InfoScaffold(title = copy.supportTitle, onBack = onBack) {
        // Both halves are conditional so this screen degrades to whatever is actually
        // configured, rather than offering a route that goes nowhere. With neither set the
        // Profile row is hidden (FAMILY_SUPPORT_CONTACT_CONFIGURED) and this is unreachable;
        // still written to survive being reached, so a future edit to the menu can't crash it.
        val supportPhone = FAMILY_SUPPORT_PHONE
        val supportEmail = FAMILY_SUPPORT_EMAIL

        if (supportPhone != null) Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(20.dp))
                .clickable {
                    val dialable = supportPhone.filter { it.isDigit() || it == '+' }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialable")))
                    }
                }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(FamilyColors.BlueLightBg, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Phone, null, tint = FamilyColors.Blue, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(copy.callUs, color = FamilyColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(supportPhone, color = FamilyColors.TextSecondary, fontSize = 16.sp)
            }
        }

        if (supportEmail != null) Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text(copy.sendMessage, color = FamilyColors.Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                copy.messageLabel,
                color = FamilyColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text(copy.messagePlaceholder, color = FamilyColors.TextHint) },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors = feedbackFieldColors()
            )
            Spacer(Modifier.height(16.dp))
            // No support-ticket endpoint on the backend, same as the senior side -- hands the
            // text to the phone's mail app rather than pretending to submit it somewhere.
            BluePillButton(
                text = copy.send,
                enabled = message.isNotBlank(),
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$supportEmail")
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
fun FamilyTermsScreen(onBack: () -> Unit) {
    val copy = LocalFamilyInfoCopy.current
    InfoScaffold(title = copy.termsScaffoldTitle, onBack = onBack) {
        LegalBody(copy.termsBodyTitle, copy.termsSections)
    }
}

@Composable
fun FamilyPrivacyScreen(onBack: () -> Unit) {
    val copy = LocalFamilyInfoCopy.current
    InfoScaffold(title = copy.privacyScaffoldTitle, onBack = onBack) {
        LegalBody(copy.privacyBodyTitle, copy.privacySections)
    }
}

@Composable
private fun LegalBody(title: String, sections: List<com.pup.seenior.ui.OnboardingStrings.Section>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = FamilyColors.Blue,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        sections.forEach { section ->
            Text(
                section.heading,
                color = FamilyColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
            )
            section.body?.let { InfoBody(it) }
            section.bullets?.forEach { bullet ->
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Text("•  ", color = FamilyColors.TextSecondary, fontSize = 15.sp)
                    InfoBody(bullet)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared bits

/** Blue back-header + scrollable white body, shared by every Profile info sub-screen. Reuses
 *  [BackHeader] from FamilyComponents.kt rather than duplicating it. */
@Composable
private fun InfoScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        BackHeader(title, FamilyColors.HeaderBlue, onBack)
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
            .background(FamilyColors.FieldBackground, RoundedCornerShape(20.dp))
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(heading, color = FamilyColors.Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun InfoBody(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = FamilyColors.TextSecondary,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        modifier = modifier
    )
}
