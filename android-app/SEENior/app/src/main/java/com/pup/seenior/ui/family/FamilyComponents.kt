package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BlueHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FamilyColors.HeaderBlue)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White)
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
    }
}

/** A colored header with a back arrow, used by every Alerts sub-screen (each design uses a
 *  different accent color: red for the active alert, orange once acknowledged, blue elsewhere). */
@Composable
fun BackHeader(title: String, background: Color, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Stand-in for a live map tile (designs show Google Maps). No Maps SDK/API key is wired up
 *  in this project yet, so this renders a neutral placeholder instead of a fake interactive
 *  map — same "cosmetic, not a dead-end pretending to be real" judgment call as elsewhere. */
@Composable
fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(FamilyColors.FieldBackground, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = FamilyColors.TextSecondary, modifier = Modifier.size(24.dp))
        Text(
            "Map preview unavailable",
            color = FamilyColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 36.dp)
        )
    }
}

@Composable
fun BluePillButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(if (enabled) FamilyColors.Blue else FamilyColors.FieldBackground, RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Color.White else FamilyColors.TextHint,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** A solid-color pill button (Alerts tab uses several distinct accent colors — red to
 *  acknowledge, green to call, orange to dispatch — where BluePillButton's single fixed
 *  color doesn't fit). */
@Composable
fun ColorPillButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(color, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** An outline-only pill button, e.g. "Mark resolved" (the last, least-urgent Next Step). */
@Composable
fun OutlinePillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = FamilyColors.TextPrimary, modifier = Modifier.size(20.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text, color = FamilyColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Shown while a fetch is still in flight and there is nothing to display yet. Necessary because
 * a cold Render instance can take ~40s to wake: without this the screen sat on its "empty" state
 * for the whole wait, which is indistinguishable from the data having been deleted.
 */
@Composable
fun LoadingCard(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(18.dp))
            .padding(vertical = 32.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = FamilyColors.Blue, strokeWidth = 3.dp)
        Text(
            message,
            color = FamilyColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "This can take a moment if the server is waking up.",
            color = FamilyColors.TextHint,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Shown wherever a fetch failed and there is nothing to display. Never reuse an "empty" state
 * for this: an unreachable server rendering as "no seniors linked" / "no alerts" reads as data
 * loss, and on the Alerts tab it would falsely imply the senior is fine.
 */
@Composable
fun CouldNotLoadCard(
    title: String,
    message: String,
    reassurance: String? = null,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(18.dp))
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(60.dp).background(FamilyColors.FieldBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CloudOff, null, tint = FamilyColors.TextSecondary, modifier = Modifier.size(28.dp))
        }
        Text(title, color = FamilyColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(
            if (reassurance != null) "$message $reassurance" else message,
            color = FamilyColors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(50.dp)
                .border(1.dp, FamilyColors.BlueBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onRetry),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Refresh, null, tint = FamilyColors.Blue, modifier = Modifier.size(20.dp))
            Text("Try again", color = FamilyColors.Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Shown on the Link tab (instead of the code form) and the Contacts tab (below the list)
 *  once a family account has reached MAX_LINKED_SENIORS. */
@Composable
fun MonitoringLimitCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(FamilyColors.WarningBg, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.Info, null, tint = FamilyColors.WarningText, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                "Monitoring limit reached ($MAX_LINKED_SENIORS/$MAX_LINKED_SENIORS)",
                color = FamilyColors.WarningText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Remove a linked senior to monitor a new one.",
                color = FamilyColors.WarningText,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun FamilyTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        isError = isError,
        supportingText = if (isError && errorText != null) {
            { Text(errorText, color = FamilyColors.ErrorRed, fontSize = 13.sp) }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FamilyColors.Blue,
            unfocusedBorderColor = FamilyColors.FieldBorder,
            focusedLabelColor = FamilyColors.Blue,
            errorBorderColor = FamilyColors.ErrorRed
        )
    )
}
