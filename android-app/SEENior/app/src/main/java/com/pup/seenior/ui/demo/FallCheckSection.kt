package com.pup.seenior.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.detection.FallCheckMonitor
import com.pup.seenior.detection.FallCheckMonitor.Gate
import com.pup.seenior.detection.FallCheckMonitor.Outcome
import com.pup.seenior.detection.FallCheckMonitor.Status
import com.pup.seenior.detection.FallDetector
import com.pup.seenior.ui.theme.SeniorColors

private val PassGreen = Color(0xFF2E7D32)
private val FailRed = Color(0xFFC62828)
private val Amber = Color(0xFFD9822B)
private val Blue = Color(0xFF1565C0)
private val Purple = Color(0xFF6A1B9A)

/** Everything the fall check shows, so it can be drawn from a preview without sensors. */
data class FallCheckUi(
    val snapshot: FallCheckMonitor.Snapshot,
    val accel: Series,
    val rotation: Series,
    /** Sensor time (ms) of the graphs' zero. */
    val originMs: Long,
    val paused: Boolean,
    val captured: Boolean,
    val hasGyroscope: Boolean
)

/**
 * The live fall check: two phyphox-style graphs (acceleration and rotation) with the detector's
 * thresholds as dashed lines and the attempt's events as markers, the four gates with what each
 * measured, and a timer for the settle period and the eight seconds of stillness.
 */
@Composable
fun FallCheckPanel(
    ui: FallCheckUi,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit
) {
    val snap = ui.snapshot
    val c = snap.config
    val limits = remember(ui.accel) {
        if (ui.accel.isEmpty) GraphView(-10f, 0f) else GraphView(ui.accel.t.first(), ui.accel.t.last().coerceAtLeast(ui.accel.t.first() + 0.1f))
    }
    val defaultView = when {
        !ui.paused -> GraphView(-10f, 0f)
        ui.captured -> GraphView(-2f, (endSeconds(snap, ui.originMs) ?: 10f) + 1f).clampTo(limits)
        else -> GraphView(limits.end - 10f, limits.end).clampTo(limits)
    }
    var view by remember(ui.paused, ui.captured, ui.originMs) { mutableStateOf(defaultView) }
    val shownView = if (ui.paused) view else GraphView(-10f, 0f)
    val markers = markers(snap, ui.originMs)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            when {
                ui.captured -> "Captured fall attempt. Time 0 = start of free fall. Zoom in and tap points to read them."
                ui.paused -> "Paused. Zoom in and tap points to read them."
                else -> "Live: last 10 seconds. Drop or fall with the phone and watch each gate."
            },
            fontSize = 12.sp,
            color = SeniorColors.TextSecondary
        )

        ZoomGraph(
            series = ui.accel, color = Blue, yMax = 40f, unit = "m/s²",
            thresholds = listOf(
                GraphLine(c.impactMin, "impact %.0f".format(c.impactMin), FailRed),
                GraphLine(FallDetector.GRAVITY, "still 9.8", SeniorColors.TextSecondary),
                GraphLine(c.freeFallMax, "free fall %.1f".format(c.freeFallMax), Amber)
            ),
            markers = markers, view = shownView, onViewChange = { view = it }, limits = limits,
            interactive = ui.paused, height = 160
        )
        if (ui.hasGyroscope) {
            ZoomGraph(
                series = ui.rotation, color = Purple, yMax = 8f, unit = "rad/s",
                thresholds = listOf(GraphLine(c.rotationMin, "rotation %.1f".format(c.rotationMin), Purple)),
                markers = markers, view = shownView, onViewChange = { view = it }, limits = limits,
                interactive = ui.paused, height = 110
            )
        } else {
            Text("No gyroscope on this phone: the rotation gate is off, as in the real detector.",
                fontSize = 12.sp, color = SeniorColors.TextSecondary)
        }

        GateRow("1. Free fall", "below %.1f m/s² for %.1f–%.0f s".format(c.freeFallMax, c.freeFallMinMs / 1000f, c.freeFallMaxMs / 1000f),
            snap.statuses[Gate.FREE_FALL], snap.freeFallMs?.let { "%.2f s".format(it / 1000f) })
        GateRow("2. Impact", "≥ %.0f m/s² within %.1f s".format(c.impactMin, c.impactWindowMs / 1000f),
            snap.statuses[Gate.IMPACT], snap.impactPeak?.let { "%.1f m/s²".format(it) })
        GateRow("3. Rotation", "≥ %.1f rad/s within %.0f s of impact".format(c.rotationMin, c.rotationMemoryMs / 1000f),
            snap.statuses[Gate.ROTATION], snap.rotationPeak?.let { "%.1f rad/s".format(it) })
        GateRow("4. Stillness", "%.0f s still after a %.1f s settle".format(c.stillnessMs / 1000f, c.settleGraceMs / 1000f),
            snap.statuses[Gate.STILLNESS], snap.stillMs?.let { "%.1f s".format(it / 1000f) })

        StillnessTimer(snap)

        snap.message?.let {
            Text("Detector: $it", fontSize = 11.sp, color = SeniorColors.TextSecondary)
        }
        Outcome(snap)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = if (ui.paused) onResume else onPause, modifier = Modifier.weight(1f)) {
                Text(if (ui.paused) "Back to live" else "Pause graphs", color = SeniorColors.TextPrimary)
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text("Reset fall check", color = SeniorColors.TextPrimary)
            }
        }
        Text(
            "The real fall detector also runs on this phone. A full simulated fall can raise a real alert: " +
                "use a test account and tell whoever watches the dashboard.",
            fontSize = 11.sp,
            color = FailRed
        )
    }
}

@Composable
private fun GateRow(name: String, rule: String, status: Status?, value: String?) {
    val (mark, color) = when (status) {
        Status.PASSED -> "✓" to PassGreen
        Status.FAILED -> "✗" to FailRed
        Status.IN_PROGRESS -> "…" to Amber
        Status.NOT_USED -> "off" to SeniorColors.TextSecondary
        else -> "–" to SeniorColors.TextSecondary
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
            Text(rule, fontSize = 11.sp, color = SeniorColors.TextSecondary)
        }
        Text(value ?: "", fontSize = 14.sp, color = SeniorColors.TextPrimary, modifier = Modifier.padding(end = 10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.width(40.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(vertical = 4.dp)
        ) { Text(mark, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color) }
    }
}

/** Settle period, then the eight seconds of stillness, counted from the impact. */
@Composable
private fun StillnessTimer(snap: FallCheckMonitor.Snapshot) {
    val since = snap.sinceImpactMs ?: return
    val c = snap.config
    val settle = (since.coerceAtMost(c.settleGraceMs)) / 1000f
    val still = ((since - c.settleGraceMs).coerceIn(0, c.stillnessMs)) / 1000f
    val failed = snap.statuses[Gate.STILLNESS] == Status.FAILED
    Column(
        modifier = Modifier.fillMaxWidth().background(SeniorColors.GreenLightBg, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Timer since impact: %.1f s".format(since / 1000f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = SeniorColors.TextPrimary)
        Text("Settling (movement ignored): %.1f / %.1f s".format(settle, c.settleGraceMs / 1000f), fontSize = 12.sp,
            color = SeniorColors.TextSecondary)
        LinearProgressIndicator(progress = { settle / (c.settleGraceMs / 1000f) }, modifier = Modifier.fillMaxWidth(),
            color = Amber)
        Text(
            if (failed) "Moved at %.1f s of stillness: not a fall".format(still)
            else "Stillness: %.1f / %.0f s".format(still, c.stillnessMs / 1000f),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (failed) FailRed else SeniorColors.TextPrimary
        )
        LinearProgressIndicator(progress = { still / (c.stillnessMs / 1000f) }, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (failed) FailRed else PassGreen)
    }
}

@Composable
private fun Outcome(snap: FallCheckMonitor.Snapshot) {
    val (text, color) = when (snap.outcome) {
        Outcome.CONFIRMED -> "✅ A fall would be detected" to PassGreen
        Outcome.NOT_CONFIRMED -> "❌ Not a fall: stopped at the gate marked ✗" to FailRed
        Outcome.NONE -> return
    }
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
}

private fun GraphView.clampTo(limits: GraphView): GraphView {
    val s = start.coerceIn(limits.start, limits.end)
    val e = end.coerceIn(s + 0.1f, maxOf(limits.end, s + 0.1f))
    return GraphView(s, e)
}

private fun sec(atMs: Long?, originMs: Long): Float? = atMs?.let { (it - originMs) / 1000f }

private fun endSeconds(snap: FallCheckMonitor.Snapshot, originMs: Long): Float? = sec(snap.endedAtMs, originMs)

/** Vertical lines for this attempt's events, in the graphs' time. */
private fun markers(snap: FallCheckMonitor.Snapshot, originMs: Long): List<GraphLine> {
    val out = mutableListOf<GraphLine>()
    sec(snap.freeFallStartAtMs, originMs)?.let { out += GraphLine(it, "fall %+.2f".format(it), Amber) }
    sec(snap.impactAtMs, originMs)?.let { impact ->
        out += GraphLine(impact, "impact %+.2f".format(impact), FailRed)
        val settleEnd = impact + snap.config.settleGraceMs / 1000f
        out += GraphLine(settleEnd, "settled", SeniorColors.TextSecondary)
        val stillEnd = settleEnd + snap.config.stillnessMs / 1000f
        if (snap.outcome == Outcome.CONFIRMED) out += GraphLine(stillEnd, "8 s still", PassGreen)
    }
    if (snap.outcome == Outcome.NOT_CONFIRMED) {
        sec(snap.endedAtMs, originMs)?.let { out += GraphLine(it, "stopped %+.2f".format(it), FailRed) }
    }
    return out
}

// ----------------------------------------------------------------------------- previews

private fun previewFall(): Pair<Series, Series> {
    val t = ArrayList<Float>()
    val a = ArrayList<Float>()
    val r = ArrayList<Float>()
    var time = -2f
    while (time <= 12f) {
        t += time
        a += when {
            time < 0f -> 9.8f + 0.6f * kotlin.math.sin(time * 11f)
            time < 0.36f -> 1.5f
            time < 0.40f -> 31.2f
            time < 0.8f -> 14f
            else -> 9.81f
        }
        r += when {
            time < 0f -> 0.2f
            time < 0.8f -> 4.0f * (1f - kotlin.math.abs(time - 0.38f))
            else -> 0.03f
        }
        time += 0.02f
    }
    return Series(t.toFloatArray(), a.toFloatArray()) to Series(t.toFloatArray(), r.toFloatArray())
}

private fun previewSnapshot(outcome: Outcome, stillness: Status, sinceImpact: Long): FallCheckMonitor.Snapshot {
    val config = FallDetector.Config()
    return FallCheckMonitor.Snapshot(
        statuses = mapOf(
            Gate.FREE_FALL to Status.PASSED, Gate.IMPACT to Status.PASSED,
            Gate.ROTATION to Status.PASSED, Gate.STILLNESS to stillness
        ),
        freeFallMs = 360, impactPeak = 31.2f, rotationPeak = 3.8f,
        stillMs = (sinceImpact - config.settleGraceMs).coerceAtLeast(0),
        outcome = outcome,
        message = if (outcome == Outcome.CONFIRMED) "CONFIRMED: all four gates passed (rotation 3.8 rad/s)"
        else "2/4 PASSED: impact at +0 ms — now hold still until +9500 ms (rotation so far 3.8 rad/s)",
        config = config,
        freeFallStartAtMs = 0, freeFallEndAtMs = 360, impactAtMs = 380,
        endedAtMs = if (outcome == Outcome.NONE) null else 380 + sinceImpact,
        sinceImpactMs = sinceImpact
    )
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun FallCheckConfirmedPreview() {
    val (a, r) = previewFall()
    FallCheckPanel(
        FallCheckUi(previewSnapshot(Outcome.CONFIRMED, Status.PASSED, 9_500), a, r, 0L, paused = true,
            captured = true, hasGyroscope = true),
        {}, {}, {}
    )
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun FallCheckCountingPreview() {
    val (a, r) = previewFall()
    FallCheckPanel(
        FallCheckUi(previewSnapshot(Outcome.NONE, Status.IN_PROGRESS, 6_700), a, r, 0L, paused = false,
            captured = false, hasGyroscope = true),
        {}, {}, {}
    )
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun FallCheckIdlePreview() {
    FallCheckPanel(
        FallCheckUi(FallCheckMonitor.Snapshot.idle(FallDetector.Config()), Series.EMPTY, Series.EMPTY, 0L,
            paused = false, captured = false, hasGyroscope = true),
        {}, {}, {}
    )
}
