package com.pup.seenior.ui.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.ui.theme.SeniorColors

private val WarningBg = Color(0xFFFBEEDC)
private val WarningText = Color(0xFF9A6B1E)

@Composable
fun InviteScreen(viewModel: InviteViewModel = viewModel()) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        GreenHeader(icon = { Icon(Icons.Filled.PersonAddAlt1, null, tint = Color.White) }, title = "Invite")

        // Scrolls; the header does not. The senior's half of the pairing flow, and it grew
        // the same way LinkScreen did: code card, countdown, instructions and a warning banner
        // together outrun a short display, and the regenerate control was the part that fell
        // off the bottom.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "Share With Family",
                color = SeniorColors.Green,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                "Give this code to family members so they can enter it in their app and monitor you.",
                color = SeniorColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            // ---- Code card ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .background(SeniorColors.GreenLightBg, RoundedCornerShape(20.dp))
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Your invite code:", color = SeniorColors.TextPrimary, fontSize = 17.sp)

                if (viewModel.hasActiveCode) {
                    Text(
                        viewModel.inviteCode ?: "",
                        color = SeniorColors.Green,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "Expires in ${viewModel.formattedRemaining()}",
                        color = SeniorColors.TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        "— — — — — —",
                        color = SeniorColors.TextHint,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                CardButton(
                    icon = { Icon(Icons.Filled.ContentCopy, null, tint = SeniorColors.TextPrimary, modifier = Modifier.size(20.dp)) },
                    label = "Copy code",
                    enabled = viewModel.hasActiveCode,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    viewModel.inviteCode?.let { copyToClipboard(context, it) }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- Generate button ----
            CardButton(
                icon = { Icon(Icons.Filled.Refresh, null, tint = if (viewModel.hasActiveCode) SeniorColors.TextHint else SeniorColors.TextPrimary, modifier = Modifier.size(24.dp)) },
                label = if (viewModel.isLoading) "Generating..."
                    else if (viewModel.hasActiveCode) "Generate new"
                    else "Generate code",
                enabled = !viewModel.hasActiveCode && !viewModel.isLoading,
                big = true
            ) {
                viewModel.generateInvite()
            }

            viewModel.error?.let {
                Text(it, color = Color(0xFFCC3333), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }

            // ---- Warning box ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .background(WarningBg, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Info, null, tint = WarningText, modifier = Modifier.size(22.dp))
                Text(
                    "Codes expire after 5 minutes. A new code can only be generated once the current code expires.",
                    color = WarningText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
fun GreenHeader(icon: @Composable () -> Unit, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SeniorColors.Green)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun CardButton(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (big) 72.dp else 56.dp)
            .background(if (big) SeniorColors.FieldBackground else Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            label,
            color = if (enabled) SeniorColors.TextPrimary else SeniorColors.TextHint,
            fontSize = if (big) 24.sp else 17.sp,
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("SEENior invite code", text))
}
