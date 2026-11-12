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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlin.math.sqrt

internal val PassGreen = Color(0xFF2E7D32)
internal val FailRed = Color(0xFFC62828)
internal val Amber = Color(0xFFD9822B)
internal val AxisX = Color(0xFFD32F2F)
internal val AxisY = Color(0xFF388E3C)
internal val AxisZ = Color(0xFF1565C0)
private val Purple = Color(0xFF6A1B9A)
private val Navy = Color(0xFF283593)

/** Everything the fall detection screen shows, so it can be drawn in a preview without sensors. */
data class FallCheckUi(
    val snapshot: FallCheckMonitor.Snapshot,
    val accelX: Series,
    val accelY: Series,
    val accelZ: Series,
    /** Linear acceleration (without gravity), shown when [withGravity] is off. */
    val linX: Series = Series.EMPTY,
    val linY: Series = Series.EMPTY,
    val linZ: Series = Series.EMPTY,
    val withGravity: Boolean = true,
    val hasLinearSensor: Boolean = true,
    val magnitude: Series,
    val rotation: Series,
    /** Sensor time (ms) of the graphs' 0 s. */
    val originMs: Long,
    /** Seconds from 0 s to the newest reading. */
    val latestSeconds: Float,
    val paused: Boolean,
    val captured: Boolean,
    val hasGyroscope: Boolean
)

/**
 * Potential fall sensor readings: the accelerometer's three axes, the gyroscope and the
 * acceleration magnitude, each against the detector's thresholds, the four gates with what they
 * measured, the stillness timer, and the reason when it is not a fall.
 */
@Composable
fun FallDetectionPanel(
    ui: FallCheckUi,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onGravity: (Boolean) -> Unit = {}
) {
    val snap = ui.snapshot
    val c = snap.config
    val limits = remember(ui.magnitude) {
        if (ui.magnitude.isEmpty) GraphView(0f, 10f)
        else GraphView(ui.magnitude.t.first(), ui.magnitude.t.last().coerceAtLeast(ui.magnitude.t.first() + 0.1f))
    }
    val live = liveView(ui.latestSeconds)
    val fallStart = sec(snap.freeFallStartAtMs, ui.originMs)
    val defaultView = when {
        !ui.paused -> live
        ui.captured && fallStart != null ->
            GraphView(fallStart - 2f, (sec(snap.endedAtMs, ui.originMs) ?: (fallStart + 10f)) + 1f).clampTo(limits)
        else -> GraphView(limits.end - 10f, limits.end).clampTo(limits)
    }
    var view by remember(ui.paused, ui.captured, ui.originMs) { mutableStateOf(defaultView) }
    // One picked time shared by all three graphs: tap one, all show that moment.
    var pickedTime by remember(ui.paused, ui.captured, ui.originMs) { mutableStateOf<Float?>(null) }
    val shown = if (ui.paused) view else live
    val markers = markers(snap, ui.originMs)

    val ax = if (ui.withGravity) ui.accelX else ui.linX
    val ay = if (ui.withGravity) ui.accelY else ui.linY
    val az = if (ui.withGravity) ui.accelZ else ui.linZ

    fun accelAt(i: Int): String {
        val x = ax.v.getOrNull(i) ?: return ""
        val y = ay.v.getOrNull(i) ?: return ""
        val z = az.v.getOrNull(i) ?: return ""
        val t = ax.t[i]
        return "t = %.2f s · x %.2f · y %.2f · z %.2f · absolute %.2f m/s²".format(t, x, y, z, sqrt(x * x + y * y + z * z))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Potential fall sensor readings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = if (ui.paused) onResume else onPause, modifier = Modifier.weight(1f)) {
                Text(if (ui.paused) "Back to live" else "Pause", color = SeniorColors.TextPrimary)
            }
            GravityToggle(ui.withGravity, ui.hasLinearSensor, onGravity, Modifier.weight(1.4f))
        }
        Text(
            when {
                ui.captured -> "Captured attempt"
                ui.paused -> "Paused"
                else -> "Live"
            },
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (ui.paused) Navy else PassGreen
        )

        ZoomGraph(
            title = if (ui.withGravity) "Accelerometer (free fall)" else "Accelerometer (free fall, without g)",
            lines = listOf(
                GraphSeries(ax, AxisX, "x"),
                GraphSeries(ay, AxisY, "y"),
                GraphSeries(az, AxisZ, "z")
            ),
            yMin = -20f, yMax = 20f, unit = "m/s²",
            thresholds = emptyList(), markers = markers,
            view = shown, onViewChange = { view = it }, limits = limits, interactive = ui.paused,
            pickLabel = ::accelAt, height = 150,
            pickedTime = pickedTime, onPick = { pickedTime = it }
        )
        if (ui.hasGyroscope) {
            ZoomGraph(
                title = "Gyroscope (rotation)",
                lines = listOf(GraphSeries(ui.rotation, Purple, "rotation")),
                yMin = 0f, yMax = 8f, unit = "rad/s",
                thresholds = listOf(GraphLine(c.rotationMin, "rotation %.1f".format(c.rotationMin), Purple)),
                markers = markers, view = shown, onViewChange = { view = it }, limits = limits, interactive = ui.paused,
                pickLabel = { i -> "t = %.2f s · rotation %.2f rad/s".format(ui.rotation.t[i], ui.rotation.v[i]) },
                height = 110, pickedTime = pickedTime, onPick = { pickedTime = it }
            )
        } else {
            Text("Gyroscope (rotation): not on this phone, so the rotation gate is off.", fontSize = 12.sp,
                color = SeniorColors.TextSecondary)
        }
        ZoomGraph(
            title = "Magnitude (impact)",
            lines = listOf(GraphSeries(ui.magnitude, Navy, "magnitude")),
            yMin = 0f, yMax = 40f, unit = "m/s²",
            thresholds = listOf(
                GraphLine(c.impactMin, "impact %.0f".format(c.impactMin), FailRed),
                GraphLine(FallDetector.GRAVITY, "still 9.8", SeniorColors.TextSecondary),
                GraphLine(c.freeFallMax, "free fall %.1f".format(c.freeFallMax), Amber)
            ),
            markers = markers, view = shown, onViewChange = { view = it }, limits = limits, interactive = ui.paused,
            pickLabel = ::accelAt, height = 150,
            pickedTime = pickedTime, onPick = { pickedTime = it }
        )

        Text("Readings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
        GateRow("1. Free fall", "magnitude below %.1f m/s² for %.1f–%.0f s".format(c.freeFallMax, c.freeFallMinMs / 1000f, c.freeFallMaxMs / 1000f),
            snap.statuses[Gate.FREE_FALL], snap.freeFallMs?.let { "%.2f s".format(it / 1000f) })
        GateRow("2. Impact", "magnitude ≥ %.0f m/s² within %.1f s".format(c.impactMin, c.impactWindowMs / 1000f),
            snap.statuses[Gate.IMPACT], snap.impactPeak?.let { "%.1f m/s²".format(it) })
        GateRow("3. Rotation", "≥ %.1f rad/s within %.0f s of impact".format(c.rotationMin, c.rotationMemoryMs / 1000f),
            snap.statuses[Gate.ROTATION], snap.rotationPeak?.let { "%.1f rad/s".format(it) })
        GateRow("4. Stillness", "%.0f s still after a %.1f s settle".format(c.stillnessMs / 1000f, c.settleGraceMs / 1000f),
            snap.statuses[Gate.STILLNESS], snap.stillMs?.let { "%.1f s".format(it / 1000f) })

        StillnessTimer(snap)
        Result(snap)
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Reset", color = SeniorColors.TextPrimary)
        }
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
            modifier = Modifier.width(40.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .padding(vertical = 4.dp)
        ) { Text(mark, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color) }
    }
}

/** Settle period, then the eight seconds of stillness, counted from the impact. */
@Composable
private fun StillnessTimer(snap: FallCheckMonitor.Snapshot) {
    val since = snap.sinceImpactMs ?: return
    val c = snap.config
    val settle = since.coerceAtMost(c.settleGraceMs) / 1000f
    val still = (since - c.settleGraceMs).coerceIn(0, c.stillnessMs) / 1000f
    val failed = snap.statuses[Gate.STILLNESS] == Status.FAILED
    Column(
        modifier = Modifier.fillMaxWidth().background(SeniorColors.GreenLightBg, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Since impact: %.1f s".format(since / 1000f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = SeniorColors.TextPrimary)
        Text("Settling: %.1f / %.1f s".format(settle, c.settleGraceMs / 1000f), fontSize = 12.sp,
            color = SeniorColors.TextSecondary)
        LinearProgressIndicator(progress = { settle / (c.settleGraceMs / 1000f) }, modifier = Modifier.fillMaxWidth(),
            color = Amber)
        Text("Stillness: %.1f / %.0f s".format(still, c.stillnessMs / 1000f), fontSize = 22.sp,
            fontWeight = FontWeight.Bold, color = if (failed) FailRed else SeniorColors.TextPrimary)
        LinearProgressIndicator(progress = { still / (c.stillnessMs / 1000f) },
            modifier = Modifier.fillMaxWidth().height(8.dp), color = if (failed) FailRed else PassGreen)
    }
}

@Composable
private fun Result(snap: FallCheckMonitor.Snapshot) {
    when (snap.outcome) {
        Outcome.CONFIRMED -> Text("✅ A fall would be detected", fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = PassGreen)
        Outcome.NOT_CONFIRMED -> Text("Not a fall: ${notAFallReason(snap)}", fontSize = 15.sp, color = FailRed)
        Outcome.NONE -> Unit
    }
}

/** Why the detector stopped, in plain words, with the measured value against the rule. */
internal fun notAFallReason(snap: FallCheckMonitor.Snapshot): String {
    val c = snap.config
    val s = snap.statuses
    return when {
        s[Gate.FREE_FALL] == Status.FAILED ->
            if ((snap.message ?: "").contains("weightless", ignoreCase = true))
                "Free fall lasted over %.0f s. That is a stuck sensor, not a person falling.".format(c.freeFallMaxMs / 1000f)
            else
                "Free fall lasted only %.2f s. It must last at least %.1f s.".format((snap.freeFallMs ?: 0) / 1000f, c.freeFallMinMs / 1000f)
        s[Gate.IMPACT] == Status.FAILED ->
            "Impact not within or above the rule: no reading of %.0f m/s² or more within %.1f s after the free fall. The hardest was %.1f m/s²."
                .format(c.impactMin, c.impactWindowMs / 1000f, snap.impactPeak ?: 0f)
        s[Gate.STILLNESS] == Status.FAILED ->
            "Moved after %.1f s of stillness. It must stay still for %.0f s after a %.1f s settle."
                .format((snap.stillMs ?: 0) / 1000f, c.stillnessMs / 1000f, c.settleGraceMs / 1000f)
        s[Gate.ROTATION] == Status.FAILED ->
            "Rotation below the rule: %.1f rad/s, needs at least %.1f rad/s within %.0f s of the impact."
                .format(snap.rotationPeak ?: 0f, c.rotationMin, c.rotationMemoryMs / 1000f)
        else -> snap.message ?: "The detector stopped before all four gates passed."
    }
}

/**
 * phyphox's two accelerometer modes: with gravity (what SEENior's detection uses) and without
 * (Android's linear acceleration sensor).
 */
@Composable
internal fun GravityToggle(withGravity: Boolean, hasLinear: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(true to "With g", false to "Without g").forEach { (value, label) ->
            val selected = withGravity == value
            OutlinedButton(
                onClick = { onChange(value) },
                enabled = value || hasLinear,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected) SeniorColors.GreenDark else Color.White,
                    contentColor = if (selected) Color.White else SeniorColors.TextPrimary
                )
            ) { Text(label, fontSize = 13.sp) }
        }
    }
}

/** Live graphs, like phyphox: the newest 10 seconds, starting from 0 s. */
internal fun liveView(latest: Float): GraphView = GraphView((latest - 10f).coerceAtLeast(0f), latest.coerceAtLeast(10f))

private fun GraphView.clampTo(limits: GraphView): GraphView {
    val s = start.coerceIn(limits.start, limits.end)
    val e = end.coerceIn(s + 0.1f, maxOf(limits.end, s + 0.1f))
    return GraphView(s, e)
}

private fun sec(atMs: Long?, originMs: Long): Float? = atMs?.let { (it - originMs) / 1000f }

/** Vertical lines for this attempt's events, in the graphs' time. */
private fun markers(snap: FallCheckMonitor.Snapshot, originMs: Long): List<GraphLine> {
    val out = mutableListOf<GraphLine>()
    sec(snap.freeFallStartAtMs, originMs)?.let { out += GraphLine(it, "fall %.2f s".format(it), Amber) }
    sec(snap.impactAtMs, originMs)?.let { impact ->
        out += GraphLine(impact, "impact %.2f s".format(impact), FailRed)
        val settleEnd = impact + snap.config.settleGraceMs / 1000f
        out += GraphLine(settleEnd, "settled", SeniorColors.TextSecondary)
        if (snap.outcome == Outcome.CONFIRMED) {
            out += GraphLine(settleEnd + snap.config.stillnessMs / 1000f, "8 s still", PassGreen)
        }
    }
    if (snap.outcome == Outcome.NOT_CONFIRMED) {
        sec(snap.endedAtMs, originMs)?.let { out += GraphLine(it, "stopped %.2f s".format(it), FailRed) }
    }
    return out
}

// ----------------------------------------------------------------------------- preview data

/** A made-up fall at 50 Hz: standing, 0.36 s of free fall, a 31.2 m/s2 impact, then lying still. */
internal fun previewFallUi(outcome: Outcome, stillness: Status, sinceImpact: Long, paused: Boolean): FallCheckUi {
    val t = ArrayList<Float>()
    val x = ArrayList<Float>()
    val y = ArrayList<Float>()
    val z = ArrayList<Float>()
    val r = ArrayList<Float>()
    var time = 0f
    while (time <= 14f) {
        t += time
        val f = time - 2f            // the free fall starts at 2 s
        when {
            f < 0f -> { x += 0.4f * kotlin.math.sin(time * 9f); y += 9.6f + 0.5f * kotlin.math.sin(time * 11f); z += 1.2f }
            f < 0.36f -> { x += 0.3f; y += 1.2f; z += 0.5f }
            f < 0.40f -> { x += 6.0f; y += -29.5f; z += 7.0f }
            f < 0.8f -> { x += 4f; y += 9f; z += 9f }
            else -> { x += 0.2f; y += 0.6f; z += 9.79f }
        }
        r += when {
            f < 0f -> 0.2f
            f < 0.8f -> 4.0f * (1f - kotlin.math.abs(f - 0.38f))
            else -> 0.03f
        }
        time += 0.02f
    }
    val tt = t.toFloatArray()
    val mag = FloatArray(tt.size) { i -> sqrt(x[i] * x[i] + y[i] * y[i] + z[i] * z[i]) }
    val config = FallDetector.Config()
    val snap = FallCheckMonitor.Snapshot(
        statuses = mapOf(
            Gate.FREE_FALL to Status.PASSED, Gate.IMPACT to Status.PASSED,
            Gate.ROTATION to Status.PASSED, Gate.STILLNESS to stillness
        ),
        freeFallMs = 360, impactPeak = 31.2f, rotationPeak = 3.8f,
        stillMs = (sinceImpact - config.settleGraceMs).coerceAtLeast(0),
        outcome = outcome,
        message = null,
        config = config,
        freeFallStartAtMs = 2_000, freeFallEndAtMs = 2_360, impactAtMs = 2_380,
        endedAtMs = if (outcome == Outcome.NONE) null else 2_380 + sinceImpact,
        sinceImpactMs = sinceImpact
    )
    return FallCheckUi(
        snapshot = snap,
        accelX = Series(tt, x.toFloatArray()), accelY = Series(tt, y.toFloatArray()), accelZ = Series(tt, z.toFloatArray()),
        magnitude = Series(tt, mag), rotation = Series(tt, r.toFloatArray()),
        originMs = 0L, latestSeconds = 14f, paused = paused, captured = paused, hasGyroscope = true
    )
}

@Preview(showBackground = true, heightDp = 1500, name = "Fall detection: a fall")
@Composable
private fun FallDetectedPreview() {
    Column(Modifier.background(Color.White).padding(16.dp).verticalScroll(rememberScrollState())) {
        FallDetectionPanel(previewFallUi(Outcome.CONFIRMED, Status.PASSED, 9_500, paused = true), {}, {}, {})
    }
}

@Preview(showBackground = true, heightDp = 1500, name = "Fall detection: moved, not a fall")
@Composable
private fun NotAFallPreview() {
    Column(Modifier.background(Color.White).padding(16.dp).verticalScroll(rememberScrollState())) {
        FallDetectionPanel(previewFallUi(Outcome.NOT_CONFIRMED, Status.FAILED, 4_900, paused = true), {}, {}, {})
    }
}
