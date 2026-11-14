package com.pup.seenior.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The research team, shown under Contact support in both the senior and the family app. One list
 * for both apps so the two screens cannot drift apart.
 *
 * These are people's own numbers and addresses, so keep this to what each person agreed to show.
 * Names are deliberately not here: only the contact details were supplied.
 */
object ResearcherContacts {

    data class Researcher(val email: String, val phone: String?)

    val ALL: List<Researcher> = listOf(
        Researcher("seth.oseas.evardo@gmail.com", "0907 592 5255"),
        Researcher("altheagatch@gmail.com", "0991 812 8756"),
        Researcher("andre.dulfo4617@gmail.com", "0947 549 5242"),
        Researcher("liezeljoantamparong@gmail.com", "0975 559 4939"),
        Researcher("ocasionreviman@gmail.com", "0994 219 0424"),
    )

    /** Digits and a leading plus only, which is what the dialler needs. */
    fun dialable(phone: String): String = phone.filter { it.isDigit() || it == '+' }
}

/**
 * The research team as shown under Contact support in both apps: a label, then the number with a
 * phone icon and the email with an envelope icon. Tapping a number opens the dialler pre-filled
 * (no call permission), tapping an email opens the mail app. Colours are passed in because the
 * senior app is green and the family app is blue.
 */
@Composable
fun ResearchersCard(
    heading: String,
    label: String,
    accent: Color,
    border: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(heading, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        ResearcherContacts.ALL.forEachIndexed { index, researcher ->
            if (index > 0) HorizontalDivider(color = border, modifier = Modifier.padding(vertical = 12.dp))
            else Spacer(Modifier.height(12.dp))
            Text(label, color = textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            researcher.phone?.let { phone ->
                ContactLine(Icons.Filled.Phone, phone, textPrimary, accent) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ResearcherContacts.dialable(phone)}"))
                        )
                    }
                }
            }
            ContactLine(Icons.Filled.Email, researcher.email, accent, accent) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${researcher.email}")))
                }
            }
        }
    }
}

/** One tappable line: an icon, then the text on a single line that shrinks rather than breaking mid-word. */
@Composable
private fun ContactLine(icon: ImageVector, text: String, textColor: Color, iconColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        FitText(text, color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}
