package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The alert popup over the family Home tab
 * (designs/family_contact/notification/Alert Notification.png).
 *
 * The mock draws this as an operating-system notification sitting in the pull-down shade. There
 * is no push channel yet — FCM is CLAUDE.md §9 and build-order step 11, and none of it is built —
 * so this is the in-app stand-in: it can only appear while the family member already has the app
 * open on the Home tab. It is not the notification from the design and must not be described as
 * one; it exists so an open alert can't sit unnoticed one tab away.
 */
@Composable
fun FamilyAlertPopup(
    item: RecentAlert,
    onView: () -> Unit,
    onDismiss: () -> Unit
) {
    val isSos = item.alert.triggerType == "sos"
    // SOS and high risk break out of the blue palette for the same reason the Home risk tile
    // does — a family member shouldn't have to read the words to know this one is serious.
    val urgent = isSos || item.alert.riskLevel == "high"
    val accent = if (urgent) FamilyColors.AlertRed else FamilyColors.Blue
    val accentBg = if (urgent) FamilyColors.AlertRedBg else FamilyColors.BlueLightBg

    Dialog(
        onDismissRequest = onDismiss,
        // A stray tap on the scrim shouldn't clear an unacknowledged emergency; dismissing is a
        // deliberate act via "Not now" (or the back button, which stays available on purpose so
        // nobody can be trapped in the dialog).
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(accentBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isSos) Icons.Filled.Warning else Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        if (isSos) "SOS Alert" else "Alert Notification",
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "SEENior · ${relativeTimeAgo(item.alert.createdAt)}",
                        color = FamilyColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Text(
                item.seniorName,
                color = FamilyColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                popupMessage(item),
                color = FamilyColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Why it fired, in plain language — the same context the senior's own prompt is
            // required to carry (CLAUDE.md §7). Without it the popup would be the bare
            // "something happened" that the panel objected to on the senior side.
            //
            // Skipped for SOS: the reason there is "They pressed the SOS button", which the line
            // above already says. A box that only restates the sentence above it costs vertical
            // space on a 720px screen and reads as filler on the one alert that most needs to
            // look deliberate.
            if (!isSos) {
                Text(
                    alertReasonText(item.alert.triggerType),
                    color = accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .background(accentBg, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
                    .clickable(onClick = onView),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("View", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Not now", color = FamilyColors.TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

/** Mirrors the mock's "Lola Alfreda may need your attention.", with SOS called out plainly —
 *  a deliberate button press isn't a maybe. */
private fun popupMessage(item: RecentAlert): String {
    val firstName = item.seniorName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
        ?: item.seniorName
    return if (item.alert.triggerType == "sos") "$firstName pressed the SOS button."
    else "$firstName may need your attention."
}
