package com.pup.seenior.ui.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import com.pup.seenior.ui.profile.GreenBackHeader
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.pup.seenior.detection.ToleranceExplanation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.ui.theme.SeniorColors

/**
 * Set to false before a build goes onto a real senior's phone: the buttons raise real alerts.
 */
const val SHOW_DEMO_TOOLS = true

/**
 * Demo-only page opened from the Demo card under Account in the Profile tab, not a feature of the product (CLAUDE.md §10
 * validates detection by injecting known values rather than waiting for a real emergency).
 * English only on purpose: it is for the presenter, never the senior.
 */
@Composable
fun DemoScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        GreenBackHeader(title = "Demo", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            DemoSection()
        }
    }
}

@Composable
private fun DemoSection(viewModel: DemoViewModel = viewModel()) {
    if (!SHOW_DEMO_TOOLS) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Each button injects known sensor readings into the real detector. The alert is real: " +
                "it runs the full escalation chain and notifies the paired family contact and barangay.",
            fontSize = 14.sp,
            color = SeniorColors.TextSecondary
        )

        val idle = !viewModel.running

        Section("Layer 0")
        DemoButton("Simulate fall", "Free fall, impact, then stillness", idle) { viewModel.simulateFall() }

        Section("Layer 1: Median-MAD (z = 4.0)")
        DemoButton("Simulate prolonged inactivity", "inactivity_duration far above baseline", idle) {
            viewModel.simulateInactivity()
        }
        DemoButton("Simulate screen idle", "screen_idle_duration far above baseline", idle) {
            viewModel.simulateScreenIdle()
        }
        DemoButton("Simulate low movement", "movement_score far below baseline", idle) {
            viewModel.simulateLowMovement()
        }

        Section("False-alarm tolerance (z = 3.0)")
        DemoButton(
            "1. Simulate inactivity at z = 3.0",
            "Moderate reading, no history: the senior is asked. Answer \"Ligtas po ako\" before step 3, or the alert escalates for real",
            idle
        ) { viewModel.simulateModerateInactivity() }
        DemoButton(
            "2. Plant 2 false alarms",
            "Two earlier \"I'm fine now\" alerts in this time block; raises its gate to 3.25",
            idle
        ) { viewModel.plantFalseAlarmHistory() }
        DemoButton(
            "3. Repeat step 1",
            "Same reading, now under the gate: logged, nobody asked. Then press the z = 4.0 inactivity button: it still alerts",
            idle
        ) { viewModel.simulateModerateInactivity() }
        DemoButton(
            "4. Plant 1 real alert",
            "An alert in this block that family acknowledged. It takes back part of the minutes the false alarms added",
            idle
        ) { viewModel.plantRealAlert() }
        DemoButton("Show the working", "Minutes added for this block and how the gate was computed", idle) {
            viewModel.showTolerance()
        }
        DemoButton("Remove planted alerts", "Gate back to 2.5", idle) { viewModel.removeFalseAlarmHistory() }

        viewModel.toleranceSteps?.let { ToleranceCard(it) }

        Section("Layer 2")
        DemoButton(
            "Plant an unusual day",
            "Writes one block-day where every signal is mildly off together; run Isolation Forest next",
            idle
        ) { viewModel.plantUnusualDay() }
        DemoButton(
            "Run Isolation Forest",
            "Real run over stored daily aggregates; only raises if a day is unusual",
            idle
        ) { viewModel.runIsolationForest() }
        DemoButton("Remove planted day", "Clears the planted row once the demo is done", idle) {
            viewModel.removePlantedDay()
        }

        Section("Senior-initiated")
        DemoButton("Simulate SOS", "Raises an SOS alert, same as the Home swipe", idle) { viewModel.simulateSos() }

        viewModel.message?.let {
            Text(it, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SeniorColors.GreenDark)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 10.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = SeniorColors.TextSecondary
    )
}

@Composable
private fun DemoButton(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SeniorColors.GreenBorder)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = SeniorColors.TextSecondary)
        }
    }
}

/** The false-alarm working, one block per step. Each number carries a tag saying where it came from. */
@Composable
private fun ToleranceCard(steps: List<ToleranceExplanation.Step>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF2F7F3), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        steps.forEach { step ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(step.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeniorColors.GreenDark)
                Text(step.formula, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SeniorColors.TextPrimary)
                Text(step.why, fontSize = 12.sp, color = SeniorColors.TextSecondary)
                step.facts.forEach { fact ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(fact.value) }
                            append("  ${fact.meaning}  ")
                            withStyle(SpanStyle(color = originColor(fact.origin), fontWeight = FontWeight.Medium)) {
                                append("[${fact.origin.label}]")
                            }
                        },
                        fontSize = 12.sp,
                        color = SeniorColors.TextPrimary
                    )
                }
            }
        }
    }
}

private fun originColor(origin: ToleranceExplanation.Origin): Color = when (origin) {
    ToleranceExplanation.Origin.YOUR_DATA -> Color(0xFF2E7D32)
    ToleranceExplanation.Origin.DESIGN_RULE -> Color(0xFF1565C0)
    ToleranceExplanation.Origin.CHOSEN -> Color(0xFFC77700)
    ToleranceExplanation.Origin.ARITHMETIC -> Color(0xFF757575)
}
