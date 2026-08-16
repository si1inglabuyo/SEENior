package com.pup.seenior.ui.wellness

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.alerts.AlertEscalator
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.network.dto.FamilyContactDto
import com.pup.seenior.ui.theme.SeniorColors

private val AlertRed = Color(0xFFC62828)
private val AlertRedSoftBg = Color(0xFFFBEAEA)
private val AlertRedText = Color(0xFFB3261E)

/**
 * The senior-facing wellness check (`designs/senior/prompt_screen/`).
 *
 * Full-screen and not dismissible by back press: an unanswered alert is the one thing in this app
 * that must not be swiped away by accident. The only exits are answering it, or the response
 * window running out — both of which resolve the alert one way or another.
 */
@Composable
fun WellnessPromptScreen(
    alert: Alert,
    seniorFirstName: String,
    language: String,
    willAlertContacts: List<FamilyContactDto>,
    barangay: String,
    viewModel: WellnessPromptViewModel,
    onFinished: () -> Unit
) {
    val copy = WellnessMessages.forAlert(language, seniorFirstName, alert.triggerType, alert.timeBlock)
    val isSos = alert.triggerType == "sos"

    LaunchedEffect(alert.alertId) { viewModel.begin(alert, onFinished) }

    BackHandler(enabled = true) { /* Deliberately swallowed — see the KDoc above. */ }

    when (viewModel.stage) {
        PromptStage.PROMPT ->
            if (isSos) {
                SosCountdownScreen(
                    copy = copy,
                    secondsRemaining = viewModel.secondsRemaining,
                    totalSeconds = AlertEscalator.windowSecondsFor(alert.triggerType),
                    contacts = willAlertContacts,
                    barangay = barangay,
                    onCancel = { viewModel.markSafe(onFinished) }
                )
            } else {
                PromptBody(copy, viewModel, onFinished)
            }
        PromptStage.ACKNOWLEDGED -> AcknowledgedBody(copy)
        PromptStage.SENT -> AlertSentBody(copy, viewModel)
    }
}

@Composable
private fun PromptBody(
    copy: WellnessMessages.Copy,
    viewModel: WellnessPromptViewModel,
    onFinished: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SeniorColors.Green)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.headerTitle,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = copy.headerSubtitle,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            // Scrollable because the prompt must never hide its own buttons: on a short screen
            // the question and reason text alone can fill the viewport, and an unreachable
            // "I'M SAFE" button would force the senior into an escalation they did not want.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // The mockup shows a decorative avatar above the question. It is dropped here:
                // on a 720x1280 device it consumed exactly the space the second line of the
                // greeting needed, clipping the senior's own name mid-word. Nothing in the
                // prompt is worth less than the name of the person being asked.
                Text(
                    text = copy.question,
                    color = SeniorColors.TextPrimary,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Everything below is pinned outside the scrolling area. The reason line is here and
            // not above because CLAUDE.md §7 requires the prompt to say WHY it is asking — a
            // senior who has to scroll to find that is being shown a bare "are you okay", which
            // is exactly what the panel rejected. The countdown and buttons are pinned for the
            // more obvious reason that an unreachable "I'M SAFE" forces an unwanted escalation.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = copy.reason,
                    color = SeniorColors.TextPrimary,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatCountdown(viewModel.secondsRemaining),
                    color = SeniorColors.Green,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.markSafe(onFinished) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SeniorColors.Green)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = copy.safeButton,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { viewModel.escalate(onFinished) },
                    enabled = !viewModel.isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlertRedSoftBg,
                        disabledContainerColor = AlertRedSoftBg
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = AlertRedText,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = copy.helpButton,
                        color = AlertRedText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = copy.footer,
                color = SeniorColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 20.dp)
            )
        }
    }
}

@Composable
private fun AcknowledgedBody(copy: WellnessMessages.Copy) {
    Surface(modifier = Modifier.fillMaxSize(), color = SeniorColors.Green) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = SeniorColors.Green,
                    modifier = Modifier.size(72.dp)
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = copy.acknowledged,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AlertSentBody(copy: WellnessMessages.Copy, viewModel: WellnessPromptViewModel) {
    Surface(modifier = Modifier.fillMaxSize(), color = AlertRed) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PriorityHigh,
                    contentDescription = null,
                    tint = AlertRed,
                    modifier = Modifier.size(66.dp)
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = copy.alertSentTitle,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                // The delivery warning replaces the reassuring copy rather than sitting beside
                // it: telling the senior their contacts were notified when the push failed
                // would be a lie at the worst possible moment.
                text = when {
                    viewModel.isDelivering -> copy.delivering
                    else -> viewModel.deliveryWarning ?: copy.alertSentBody
                },
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            if (!viewModel.isDelivering) {
                Text(
                    text = if (viewModel.secondsRemaining == 1) copy.returningHomeOne
                    else copy.returningHome.format(viewModel.secondsRemaining),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 36.dp)
                )
            }
        }
    }
}

private fun formatCountdown(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%02d : %02d".format(safe / 60, safe % 60)
}
