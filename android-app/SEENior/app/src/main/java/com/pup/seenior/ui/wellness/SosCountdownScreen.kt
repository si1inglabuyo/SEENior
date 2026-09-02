package com.pup.seenior.ui.wellness

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.database.entities.Contact
import com.pup.seenior.ui.theme.SeniorColors

private val SosRed = Color(0xFFC62828)
private val SosRingTrack = Color(0xFFF6C9C9)
private val SosCancelBg = Color(0xFFECECEC)
private val SosAvatarBorder = Color(0xFF7A8B3C)
private val SosBarangayAmber = Color(0xFFE08A1E)

/**
 * The SOS cancellation window (`designs/senior/sos/`).
 *
 * Kept separate from the wellness prompt rather than reusing it with a flag. The wellness prompt
 * is a green "are you alright?" question the senior can answer either way; this is a red
 * "help is on its way unless you stop it" statement with a single escape hatch. Rendering the
 * second in the first's calm green chrome would tell the senior the opposite of what is happening.
 */
@Composable
fun SosCountdownScreen(
    copy: WellnessMessages.Copy,
    secondsRemaining: Int,
    totalSeconds: Int,
    contacts: List<Contact>,
    contactsKnown: Boolean,
    barangay: String,
    onCancel: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SosRed)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = copy.sosHeaderTitle,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CountdownRing(secondsRemaining = secondsRemaining, totalSeconds = totalSeconds)

                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (secondsRemaining == 1) copy.sosSendingInOne
                    else copy.sosSendingIn.format(secondsRemaining),
                    color = SosRed,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = copy.sosAutoNote,
                    color = SeniorColors.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = SeniorColors.FieldBorder)
                Spacer(Modifier.height(16.dp))

                WillAlertCard(
                    copy = copy,
                    contacts = contacts,
                    contactsKnown = contactsKnown,
                    barangay = barangay
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SosCancelBg)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = SeniorColors.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = copy.sosCancel,
                        color = SeniorColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = copy.sosAutoFooter,
                    color = SeniorColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun CountdownRing(secondsRemaining: Int, totalSeconds: Int) {
    val fraction = if (totalSeconds <= 0) 0f else secondsRemaining.toFloat() / totalSeconds
    Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = SosRingTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke)
            )
            // Drains clockwise from the top as the window closes.
            drawArc(
                color = SosRed,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke)
            )
        }
        Text(
            text = secondsRemaining.toString(),
            color = SosRed,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WillAlertCard(
    copy: WellnessMessages.Copy,
    contacts: List<Contact>,
    contactsKnown: Boolean,
    barangay: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SosRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = copy.sosWillAlert,
            color = SosRed,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))

        contacts.forEach { contact ->
            WillAlertRow(
                name = contact.name.ifBlank { "Family contact" },
                role = contact.relationshipLabel.orEmpty().replaceFirstChar { it.uppercase() },
                accent = SosAvatarBorder,
                roleColor = SosRed
            )
        }

        // The barangay tier is listed whether or not any family is linked — it is the final
        // escalation step and is always available (CLAUDE.md §7).
        if (barangay.isNotBlank()) {
            WillAlertRow(
                name = "Brgy. $barangay",
                role = copy.sosBarangayRole,
                accent = SosBarangayAmber,
                roleColor = SosBarangayAmber
            )
        }

        // Only claimed when it is actually known to be true. An empty list on a phone that has
        // never managed to read its family list is an absence of information, not the absence of
        // a family, and saying "no family contacts linked yet" to a senior whose three children
        // are all about to be called would be a lie told at the worst possible moment.
        if (contacts.isEmpty() && contactsKnown) {
            Text(
                text = copy.sosNoContacts,
                color = SeniorColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun WillAlertRow(name: String, role: String, accent: Color, roleColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialsOf(name),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = name,
                color = if (roleColor == SosBarangayAmber) SosBarangayAmber else SeniorColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = role, color = roleColor, fontSize = 13.sp)
        }
    }
}

private fun initialsOf(name: String): String =
    name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
