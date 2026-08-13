package com.pup.seenior.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.pup.seenior.ui.wellness.WellnessPromptScreen
import com.pup.seenior.ui.wellness.WellnessPromptViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val EmergencyRed = Color(0xFFC62828)
private val EmergencyCardBg = Color(0xFFFDF7F5)
private val EmergencyBorder = Color(0xFFE9BDBD)
private val WarningAmber = Color(0xFFE99A20)
private val WarningAmberBg = Color(0xFFFDF3E7)

/**
 * The Home tab's own content. The wellness prompt is NOT rendered here — an open alert takes over
 * the entire screen including the bottom navigation, so that branch lives one level up in
 * [com.pup.seenior.ui.navigation.SeniorDashboard].
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
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
                    Text("Senior", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Hi, ${viewModel.fullName}",
                    color = SeniorColors.Green,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "You're all set today",
                    color = SeniorColors.TextSecondary,
                    fontSize = 15.sp
                )

                Spacer(Modifier.height(14.dp))
                StatusCard(atRisk = viewModel.isMonitoringAtRisk)

                Spacer(Modifier.height(16.dp))
                BatteryRow(percent = viewModel.batteryPercent, atRisk = viewModel.isMonitoringAtRisk)

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = SeniorColors.FieldBorder)
                Spacer(Modifier.height(16.dp))

                EmergencyCard(barangay = viewModel.barangay, onSosConfirmed = { viewModel.sendSos() })

                Spacer(Modifier.height(10.dp))
                SimulateAnomalyRow(viewModel)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(atRisk: Boolean) {
    val bg = if (atRisk) WarningAmberBg else SeniorColors.GreenLightBg
    val border = if (atRisk) WarningAmber else SeniorColors.GreenBorder
    val dot = if (atRisk) WarningAmber else SeniorColors.Green

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
                text = if (atRisk) "Monitoring At Risk" else "You're Safe",
                color = if (atRisk) WarningAmber else SeniorColors.Green,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (atRisk) "Charge your phone to continue\nemergency monitoring"
                else "Monitoring is active",
                color = SeniorColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun BatteryRow(percent: Int, atRisk: Boolean) {
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
            text = "Battery · ${if (atRisk) "Low" else "Good"}",
            color = tint,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 36.dp, top = 2.dp)
        )
    }
}

@Composable
private fun EmergencyCard(barangay: String, onSosConfirmed: () -> Unit) {
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
            text = "EMERGENCY ALERT",
            color = EmergencyRed,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(26.dp))
        SosSwipe(onConfirmed = onSosConfirmed)
        Spacer(Modifier.height(26.dp))
        Text(
            text = if (barangay.isBlank()) "Alerts family and your barangay if no response"
            else "Alerts family and Barangay $barangay\nif no response",
            color = EmergencyRed,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Swipe-to-send, not tap-to-send. A single mis-tap on the senior's main screen should not be able
 * to summon a barangay responder, and CLAUDE.md §7 describes SOS as one-swipe throughout.
 */
@Composable
private fun SosSwipe(onConfirmed: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val knobSizeDp = 72.dp
    val knobSizePx = with(density) { knobSizeDp.toPx() }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val offset = remember { Animatable(0f) }
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
            text = "Swipe to send alert",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(start = knobSizeDp),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(knobSizeDp)
                .padding(4.dp)
                .background(EmergencyRed, CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(0f, maxOffset))
                        }
                    },
                    onDragStopped = {
                        // Must reach most of the way across, so a short accidental drag releases
                        // harmlessly back to the start.
                        if (maxOffset > 0f && offset.value >= maxOffset * CONFIRM_FRACTION) {
                            offset.animateTo(maxOffset)
                            onConfirmed()
                        }
                        offset.animateTo(0f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("SOS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Not in the mockups, and deliberately styled as a plain text row rather than a real control:
 * this is a demo affordance, not a feature of the product. CLAUDE.md §10 endorses validating
 * detection by injecting known sensor values instead of waiting for a real emergency.
 */
@Composable
private fun SimulateAnomalyRow(viewModel: HomeViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = { viewModel.simulateAnomaly() }) {
            Text(
                text = "Simulate Anomaly (Demo)",
                color = SeniorColors.TextSecondary,
                fontSize = 14.sp
            )
        }
        viewModel.simulationMessage?.let { message ->
            Text(
                text = message,
                color = SeniorColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(4000)
                viewModel.clearSimulationMessage()
            }
        }
    }
}

private const val CONFIRM_FRACTION = 0.9f
