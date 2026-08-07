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
import com.pup.seenior.ui.theme.SeniorColors

// TODO: replace both with the real support details before the demo — these are the
// placeholders straight out of designs/senior/profile.
private const val SUPPORT_PHONE = "+63 000 000 0000"
private const val SUPPORT_EMAIL = "seenior.support@example.com"

internal val ErrorRed = Color(0xFFCC3333)

// ---------------------------------------------------------------- About this app

@Composable
fun SeniorAboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    InfoScaffold(title = "About", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.logo_seenior),
                contentDescription = "SEENior",
                modifier = Modifier.size(96.dp)
            )
            Text("Version $version", color = SeniorColors.TextSecondary, fontSize = 15.sp)
        }

        InfoCard(heading = "Our mission") {
            InfoBody(
                "To provide a safe, accessible, and passive monitoring system that supports the " +
                    "well-being of seniors through intelligent, low-burden technology designed " +
                    "for early detection and timely assistance."
            )
        }
        InfoCard(heading = "Who we are") {
            InfoBody(
                "We are a small team of developers and healthcare-focused researchers who " +
                    "designed SEENior to address the growing need for proactive support for " +
                    "elderly individuals living alone or temporarily left alone at home in the " +
                    "Philippines."
            )
            Spacer(Modifier.height(12.dp))
            InfoBody(
                "We build mobile-based, low-burden systems that use passive monitoring and " +
                    "simple design to help detect unusual behavior and enable timely assistance " +
                    "when needed."
            )
        }
        InfoCard(heading = "Our values") {
            InfoBody("Privacy. Simplicity. Dignity. Reliability.")
            Spacer(Modifier.height(12.dp))
            InfoBody(
                "SEENior is built to respect user data, reduce complexity for seniors, and " +
                    "provide dependable support in times of need."
            )
        }
    }
}

// ---------------------------------------------------------------- How to use

private data class HowToStep(val title: String, val body: String)

private val howToSteps = listOf(
    HowToStep(
        "Set up your profile",
        "Enter your basic details and living arrangement during onboarding. This is done only " +
            "once, and your app will proceed immediately after setup."
    ),
    HowToStep(
        "Grant permissions and enable alerts (required)",
        "During onboarding, you must enable notifications and required permissions for SEENior " +
            "to function properly. The app cannot proceed without this setup."
    ),
    HowToStep(
        "Answer onboarding questions (14-day baseline period)",
        "After setup, you will answer a short set of onboarding questions. SEENior uses this " +
            "information to learn your normal behavior patterns over a 14-day monitoring " +
            "period, which serves as your personal baseline."
    ),
    HowToStep(
        "Invite a trusted contact",
        "Go to the Invite tab to generate a unique code. The code expires every 5 minutes for " +
            "security, and you can generate a new one once it does. Share this code to connect " +
            "with a trusted contact."
    ),
    HowToStep(
        "Manage your contacts",
        "In the Contacts tab, you can view connected contacts, check their status, and remove " +
            "them if needed."
    ),
    HowToStep(
        "Answer the wellness check when it appears",
        "If SEENior notices something unusual, it will ask \"Are you safe and well?\" and tell " +
            "you why it is asking. Tapping \"I'm safe\" stops the alert right there. If you do " +
            "not answer, your family contact is notified, and then your barangay responder."
    )
)

@Composable
fun SeniorHowToUseScreen(onBack: () -> Unit) {
    InfoScaffold(title = "How to use", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            howToSteps.forEachIndexed { index, step ->
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
                            step.title,
                            color = SeniorColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        InfoBody(step.body, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- FAQs

private data class Faq(val question: String, val answer: String, val note: String? = null)

private val faqs = listOf(
    Faq(
        "Is this app free?",
        "Yes. SEENior is completely free to download and use."
    ),
    Faq(
        "Will my location always be shared?",
        "No. Your location is only captured at the moment an alert is triggered, never " +
            "continuously. Only that last known location is shared with your trusted contacts."
    ),
    Faq(
        "Can I use SEENior without internet?",
        "Yes. SEENior keeps watching over you and can still show your saved contacts with no " +
            "internet at all. Sending alerts and real-time updates to your family needs an " +
            "internet or network connection.",
        note = "If you have no signal at all, SEENior will try to send an SMS instead. Keep your " +
            "phone charged and with you so it can reach someone for you."
    ),
    Faq(
        "How do I remove a paired contact?",
        "Go to the Contacts tab, select the contact, scroll down, and tap Remove Contact. A " +
            "confirmation prompt will appear before removal."
    ),
    Faq(
        "Do I need to open the app every day?",
        "No. SEENior runs quietly in the background. You only need to answer if it asks you " +
            "\"Are you safe and well?\""
    )
)

@Composable
fun SeniorFaqsScreen(onBack: () -> Unit) {
    InfoScaffold(title = "FAQs", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            faqs.forEachIndexed { index, faq ->
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
                        "Important Note:",
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
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }

    InfoScaffold(title = "Contact support", onBack = onBack) {
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
                Text("Call us", color = SeniorColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(SUPPORT_PHONE, color = SeniorColors.TextSecondary, fontSize = 16.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("Send us a message", color = SeniorColors.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "MESSAGE",
                color = SeniorColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                placeholder = { Text("Suggest a new feature…", color = SeniorColors.TextHint) },
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
                text = "SEND",
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

private data class LegalSection(
    val heading: String,
    val body: String? = null,
    val bullets: List<String>? = null
)

private val termsSections = listOf(
    LegalSection(
        "1. Acceptance of Terms",
        "By creating an account and using SEENior, you agree to be bound by these Terms of Use. " +
            "If you do not agree to these terms, please do not use our services."
    ),
    LegalSection(
        "2. Description of Service",
        "SEENior is a passive behavioral monitoring application that uses your smartphone's " +
            "built-in sensors to establish a daily routine baseline and detect significant " +
            "deviations that may indicate an emergency."
    ),
    LegalSection(
        "3. User Eligibility",
        "The SEENior senior app is intended for use by senior citizens (60 years and older) " +
            "residing in the Philippines, consistent with RA 9994 (Expanded Senior Citizens Act " +
            "of 2010). Use by persons below this age threshold is permitted only for testing " +
            "purposes by the development team."
    ),
    LegalSection(
        "4. User Responsibilities",
        bullets = listOf(
            "Keep your phone charged and with you for the monitoring system to function reliably.",
            "Provide accurate personal information during account setup.",
            "Grant the required app permissions (motion, notifications, background activity) for the system to work.",
            "Add at least one emergency contact so alerts can be delivered if an anomaly is detected.",
            "Inform your emergency contacts that they will receive alerts from SEENior on your behalf."
        )
    ),
    LegalSection(
        "5. Limitation of Liability",
        "SEENior is provided \"as is\" and is a support tool only. It is not a substitute for " +
            "professional medical care, emergency services, or human supervision. The developers " +
            "are not liable for missed or delayed alerts caused by device, network, or " +
            "third-party service failures."
    )
)

private val privacySections = listOf(
    LegalSection(
        "1. Who collects your data",
        "SEENior is developed by Polytechnic University of the Philippines students. We act as " +
            "the personal information controller for data collected through this application, in " +
            "compliance with RA 10173 and NPC guidelines."
    ),
    LegalSection(
        "2. What data we collect",
        body = "We collect the following data from senior users:",
        bullets = listOf(
            "Personal profile — full name, age, gender, living arrangement, address, phone number, and relationship to emergency contacts.",
            "Behavioral sensor data — accelerometer readings, screen on/off timestamps, and battery/charging status. Used only to build your activity baseline.",
            "Location (GPS) — captured only when an alert is triggered. Not continuously tracked. Stops immediately when the alert is resolved."
        )
    ),
    LegalSection(
        "3. How data is stored",
        "Behavioral sensor data is stored locally on your device. It is not uploaded to any " +
            "external server during normal monitoring. Alert records are synchronized to a " +
            "secure cloud backend only when an alert is triggered, to enable notification " +
            "delivery to your emergency contacts."
    ),
    LegalSection(
        "4. Who can see your data",
        bullets = listOf(
            "Your emergency contacts — receive alert notifications and your location only during active alert events.",
            "Barangay responders — receive escalated alerts only when your contacts do not respond within the set timeframe.",
            "Development team — may access anonymized, aggregated data for academic research purposes only, with no individual identification."
        )
    )
)

@Composable
fun SeniorTermsScreen(onBack: () -> Unit) {
    InfoScaffold(title = "Terms & conditions", onBack = onBack) {
        LegalBody("Terms & Conditions", termsSections)
    }
}

@Composable
fun SeniorPrivacyScreen(onBack: () -> Unit) {
    InfoScaffold(title = "Privacy policy", onBack = onBack) {
        LegalBody("Privacy Policy", privacySections)
    }
}

@Composable
private fun LegalBody(title: String, sections: List<LegalSection>) {
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
