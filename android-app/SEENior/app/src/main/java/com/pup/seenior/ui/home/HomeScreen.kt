package com.pup.seenior.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.ui.theme.SeniorColors
import com.pup.seenior.ui.wellness.WellnessMessages
import com.pup.seenior.ui.wellness.WellnessPromptScreen
import com.pup.seenior.ui.wellness.WellnessPromptViewModel
import kotlin.math.roundToInt
import com.pup.seenior.ui.SeniorStrings

private val EmergencyRed = Color(0xFFC62828)
private val EmergencyCardBg = Color(0xFFFDF7F5)
private val EmergencyBorder = Color(0xFFE9BDBD)
private val WarningAmber = Color(0xFFE99A20)
private val WarningAmberBg = Color(0xFFFDF3E7)

/**
 * The Home tab's content. The wellness prompt isn't rendered here: an open alert takes over
 * the whole screen, so that branch is in [com.pup.seenior.ui.navigation.SeniorDashboard].
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val copy = SeniorStrings.forLanguage(viewModel.language)
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SeniorColors.Green)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SEENior", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(copy.roleLabel, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = copy.greeting(viewModel.fullName),
                    color = SeniorColors.Green,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = copy.allSetToday,
                    color = SeniorColors.TextSecondary,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(14.dp))
                // helpPending outranks battery, so an open HIGH alert never reads "You're Safe".
                StatusCard(
                    batteryAtRisk = viewModel.isMonitoringAtRisk,
                    helpPending = viewModel.helpDelivery != null,
                    copy = copy
                )

                // Directly under the status card because it contradicts it: that one is about
                // passive watching, this is about help the senior asked for.
                viewModel.helpDelivery?.let { delivery ->
                    Spacer(Modifier.height(12.dp))
                    HelpDeliveryCard(
                        delivery,
                        viewModel.language,
                        viewModel.firstName,
                        onStandDown = { viewModel.standDown(delivery.alert) }
                    )
                }

                Spacer(Modifier.height(16.dp))
                BatteryRow(percent = viewModel.batteryPercent, atRisk = viewModel.isMonitoringAtRisk, copy = copy)

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = SeniorColors.FieldBorder)
                Spacer(Modifier.height(16.dp))

                EmergencyCard(barangay = viewModel.barangay, onSosConfirmed = { viewModel.sendSos() }, copy = copy)

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Standing confirmation that the senior's request for help is being carried. The alert
 * screen closes after five seconds, and without this Home says "You're Safe" with no sign
 * the senior pressed SOS (or that, offline, the alert is still undelivered).
 */
@Composable
private fun HelpDeliveryCard(
    delivery: HelpDelivery,
    language: String,
    firstName: String,
    onStandDown: () -> Unit
) {
    val waiting = delivery is HelpDelivery.Waiting
    val copy = WellnessMessages.forAlert(
        language, firstName, delivery.alert.triggerType, delivery.alert.timeBlock
    )
    // Delivered means family were notified of a still-open alert, which is urgent, so it reads
    // red like other emergency UI. The stand-down button stays green: it's the senior's
    // "I'm fine now", not a severity indicator.
    val accent = if (waiting) WarningAmber else EmergencyRed
    val bg = if (waiting) WarningAmberBg else EmergencyCardBg

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, accent, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Icon(
            imageVector = if (waiting) Icons.Filled.CloudOff else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(26.dp)
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                if (waiting) copy.homePendingTitle else copy.homeDeliveredTitle,
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (waiting) copy.homePendingBody else copy.homeDeliveredBody,
                color = SeniorColors.TextPrimary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            // The way back out: a senior who got up unhurt can say so. Always green.
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, SeniorColors.Green, RoundedCornerShape(12.dp))
                    .clickable(onClick = onStandDown)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    copy.standDownButton,
                    color = SeniorColors.Green,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatusCard(batteryAtRisk: Boolean, helpPending: Boolean, copy: SeniorStrings.Copy) {
    val atRisk = batteryAtRisk || helpPending
    val bg = if (atRisk) WarningAmberBg else SeniorColors.GreenLightBg
    val border = if (atRisk) WarningAmber else SeniorColors.GreenBorder
    val dot = if (atRisk) WarningAmber else SeniorColors.Green

    // helpPending takes priority: never claim "You're Safe" while an alert is outstanding.
    val title = when {
        helpPending -> copy.helpPendingTitle
        batteryAtRisk -> copy.monitoringAtRisk
        else -> copy.youAreSafe
    }
    val body = when {
        helpPending -> copy.helpPendingBody
        batteryAtRisk -> copy.chargeToContinue
        else -> copy.monitoringActive
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(12.dp).background(dot, CircleShape))
        Spacer(Modifier.size(14.dp))
        Column {
            Text(
                text = title,
                color = if (atRisk) WarningAmber else SeniorColors.Green,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                color = SeniorColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun BatteryRow(percent: Int, atRisk: Boolean, copy: SeniorStrings.Copy) {
    val tint = if (atRisk) WarningAmber else SeniorColors.Green
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.BatteryFull,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.size(10.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                color = tint,
                trackColor = SeniorColors.DisabledButtonBg
            )
            Spacer(Modifier.size(10.dp))
            Text("$percent%", color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = if (atRisk) copy.batteryLow else copy.batteryGood,
            color = tint,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 36.dp, top = 2.dp)
        )
    }
}

@Composable
private fun EmergencyCard(barangay: String, onSosConfirmed: () -> Unit, copy: SeniorStrings.Copy) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EmergencyCardBg)
            .border(1.dp, EmergencyBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 22.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = copy.emergencyAlert,
            color = EmergencyRed,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(26.dp))
        SosSwipe(onConfirmed = onSosConfirmed, copy = copy)
        Spacer(Modifier.height(26.dp))
        Text(
            text = copy.alertsFamilyAndBarangay(barangay),
            color = EmergencyRed,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Swipe-to-send, not tap, so a mis-tap can't summon a responder. SOS is a one-swipe action
 * (spec section 7).
 */
@Composable
private fun SosSwipe(onConfirmed: () -> Unit, copy: SeniorStrings.Copy) {
    val density = LocalDensity.current
    val knobSizeDp = 72.dp
    val knobSizePx = with(density) { knobSizeDp.toPx() }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    /*
     * Plain state, not an Animatable. Animatable's MutatorMutex cancels each snapTo, and since
     * snapTo suspends, drag deltas launched into separate coroutines were dropped (a finger
     * emits 60-120 a second). The knob crawled and often never reached the threshold on a
     * real phone, though a synthetic adb swipe worked. A straggling snapTo could also cancel
     * the spring-back and strand the knob. rememberDraggableState's lambda isn't suspend, so
     * assigning here is synchronous and no delta is lost.
     */
    var knobX by remember { mutableFloatStateOf(0f) }
    val maxOffset = (trackWidthPx - knobSizePx).coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(knobSizeDp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .clip(RoundedCornerShape(percent = 50))
            .background(
                Brush.horizontalGradient(listOf(Color(0xFFD32F2F), Color(0xFFF3A3A3)))
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = copy.swipeToSend,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(start = knobSizeDp),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(knobX.roundToInt(), 0) }
                .size(knobSizeDp)
                .padding(4.dp)
                .background(EmergencyRed, CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        knobX = (knobX + delta).coerceIn(0f, maxOffset)
                    },
                    onDragStopped = {
                        // Must reach most of the way across, so a short accidental drag springs back.
                        if (maxOffset > 0f && knobX >= maxOffset * CONFIRM_FRACTION) onConfirmed()
                        // Always springs back, and now nothing can cancel it.
                        animate(knobX, 0f) { value, _ -> knobX = value }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("SOS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * How far across the track the knob must travel to count as a swipe. Kept near the full
 * width: lowering it to 0.7 didn't help (a 75% swipe still didn't fire), and "drag it all
 * the way" is easier for a senior to learn than "most of the way".
 */
private const val CONFIRM_FRACTION = 0.9f
