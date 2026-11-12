package com.pup.seenior.ui.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.detection.FallContext
import com.pup.seenior.detection.FallDetector
import com.pup.seenior.ui.theme.SeniorColors
import kotlin.math.sin
import kotlin.math.sqrt

private val Navy = Color(0xFF283593)

/** Everything the walk test screen shows, so it can be drawn in a preview without sensors. */
data class WalkTestUi(
    val participant: String,
    val activityCode: String,
    val recording: Boolean,
    /** A recording is running but paused. */
    val recordingPaused: Boolean = false,
    /** Show acceleration with gravity (SEENior's) or without (linear acceleration). */
    val withGravity: Boolean = true,
    val elapsedSeconds: Int,
    val liveValue: Float?,
    val liveLinear: Float?,
    val hasLinearSensor: Boolean,
    val hasGyroscope: Boolean,
    /** Acceleration with gravity, m/s2. */
    val accelX: Series = Series.EMPTY,
    val accelY: Series = Series.EMPTY,
    val accelZ: Series = Series.EMPTY,
    /** Linear acceleration (without gravity), m/s2. */
    val linX: Series,
    val linY: Series,
    val linZ: Series,
    /** Absolute acceleration: the magnitude of the accelerometer reading (with gravity), m/s2. */
    val absolute: Series,
    /** Absolute linear acceleration: the magnitude without gravity, m/s2. */
    val linAbs: Series = Series.EMPTY,
    /** Angular velocity, rad/s. */
    val gyroX: Series,
    val gyroY: Series,
    val gyroZ: Series,
    /** SEENior's 2 s pre-fall value, m/s2. */
    val preFall: Series,
    /** Seconds from 0 s to the newest reading. */
    val latestSeconds: Float,
    val paused: Boolean,
    val message: String?,
    val savedFileName: String?,
    val savedCount: Int
)

/**
 * The walk test: the accelerometer (with or without g), absolute acceleration and the 2 s pre-fall
 * value as phyphox-style graphs (pause, then zoom and tap; one tap marks the same moment on every
 * graph), and labelled recordings saved as CSV for the walk-test spreadsheet.
 */
@Composable
fun WalkTestPanel(
    ui: WalkTestUi,
    onParticipant: (String) -> Unit,
    onActivity: (String) -> Unit,
    /** Start a recording, or resume a paused one (phyphox's play). */
    onStart: () -> Unit,
    /** Save the recording as CSV. */
    onStop: () -> Unit,
    /** Reset: throw the recording away. */
    onDiscard: () -> Unit,
    onShareLast: () -> Unit,
    onShareAll: () -> Unit,
    /** phyphox's pause. */
    onPause: () -> Unit,
    onGravity: (Boolean) -> Unit = {},
    /** Freeze the graphs to read them (recording, if any, carries on). */
    onPauseGraphs: () -> Unit = {},
    onResumeGraphs: () -> Unit = {}
) {
    val limits = if (ui.absolute.isEmpty) GraphView(0f, 10f)
    else GraphView(ui.absolute.t.first(), ui.absolute.t.last().coerceAtLeast(ui.absolute.t.first() + 0.1f))
    val live = liveView(ui.latestSeconds)
    var view by remember(ui.paused) {
        mutableStateOf(GraphView((limits.end - 10f).coerceAtLeast(limits.start), limits.end.coerceAtLeast(limits.start + 0.1f)))
    }
    var pickedTime by remember(ui.paused) { mutableStateOf<Float?>(null) }
    val shown = if (ui.paused) view else live

    @Composable
    fun Graph(title: String, lines: List<GraphSeries>, yMin: Float, yMax: Float, unit: String,
              thresholds: List<GraphLine>, label: (Int) -> String, height: Int = 120) {
        ZoomGraph(
            title = title, lines = lines, yMin = yMin, yMax = yMax, unit = unit, thresholds = thresholds,
            markers = emptyList(), view = shown, onViewChange = { view = it }, limits = limits,
            interactive = ui.paused, pickLabel = label, height = height,
            pickedTime = pickedTime, onPick = { pickedTime = it },
            onTogglePause = if (ui.paused) onResumeGraphs else onPauseGraphs
        )
    }

    fun xyz(t: Series, x: Series, y: Series, z: Series, unit: String): (Int) -> String = { i ->
        val vx = x.v.getOrNull(i)
        val vy = y.v.getOrNull(i)
        val vz = z.v.getOrNull(i)
        if (vx == null || vy == null || vz == null) "" else
            "t = %.2f s · x %.2f · y %.2f · z %.2f · absolute %.2f %s"
                .format(t.t[i], vx, vy, vz, sqrt(vx * vx + vy * vy + vz * vz), unit)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Pre-fall Event", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = if (ui.paused) onResumeGraphs else onPauseGraphs, modifier = Modifier.weight(1f)) {
                Text(if (ui.paused) "Back to live" else "Pause", color = SeniorColors.TextPrimary)
            }
            GravityToggle(ui.withGravity, ui.hasLinearSensor, onGravity, Modifier.weight(1.4f))
        }

        val ax = if (ui.withGravity) ui.accelX else ui.linX
        val ay = if (ui.withGravity) ui.accelY else ui.linY
        val az = if (ui.withGravity) ui.accelZ else ui.linZ
        val abs = if (ui.withGravity) ui.absolute else ui.linAbs
        Graph(
            if (ui.withGravity) "Accelerometer (Acceleration with g)" else "Accelerometer (Linear Acceleration)",
            listOf(GraphSeries(ax, AxisX, "x"), GraphSeries(ay, AxisY, "y"), GraphSeries(az, AxisZ, "z")),
            -20f, 20f, "m/s²", emptyList(), xyz(ax, ax, ay, az, "m/s²")
        )
        Graph(
            if (ui.withGravity) "Accelerometer (Absolute Acceleration)" else "Accelerometer (Absolute Linear Acceleration)",
            listOf(GraphSeries(abs, Navy, "absolute")),
            0f, 30f, "m/s²",
            if (ui.withGravity) listOf(GraphLine(FallDetector.GRAVITY, "still 9.8", SeniorColors.TextSecondary)) else emptyList(),
            { i -> "t = %.2f s · absolute %.2f m/s²".format(abs.t[i], abs.v[i]) }
        )
        Graph(
            "Pre-fall Event (2-second window)",
            listOf(GraphSeries(ui.preFall, PassGreen, "2 s value")),
            0f, 4f, "m/s²",
            listOf(
                GraphLine(FallContext.STILL_MAX, "still below %.1f".format(FallContext.STILL_MAX), Color(0xFF6B8E9B)),
                GraphLine(FallContext.ACTIVE_MIN, "moving from %.1f".format(FallContext.ACTIVE_MIN), Amber)
            ),
            { i ->
                "t = %.2f s · 2 s value %.2f m/s² · %s".format(
                    ui.preFall.t[i], ui.preFall.v[i], movementName(FallContext.levelOf(ui.preFall.v[i]))
                )
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("2 s value", ui.liveValue?.let { "%.2f".format(it) } ?: "-")
            Stat("Movement", ui.liveValue?.let { movementName(FallContext.levelOf(it)) } ?: "-")
            Stat("Without g", if (ui.hasLinearSensor) ui.liveLinear?.let { "%.2f".format(it) } ?: "-" else "none")
            Stat("Time", "%d:%02d".format(ui.elapsedSeconds / 60, ui.elapsedSeconds % 60))
        }

        OutlinedTextField(
            value = ui.participant,
            onValueChange = onParticipant,
            label = { Text("Participant") },
            singleLine = true,
            enabled = !ui.recording,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Activity", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
        RecorderCsv.ACTIVITIES.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { activity ->
                    val selected = ui.activityCode == activity.code
                    OutlinedButton(
                        onClick = { onActivity(activity.code) },
                        enabled = !ui.recording,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(if (selected) 2.dp else 1.dp,
                            if (selected) SeniorColors.GreenDark else SeniorColors.GreenBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) SeniorColors.GreenLightBg else Color.Transparent
                        )
                    ) {
                        Text(activity.label, fontSize = 13.sp, color = SeniorColors.TextPrimary)
                    }
                }
                if (pair.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }

        // phyphox's controls: play / pause, then save or reset.
        val running = ui.recording && !ui.recordingPaused
        Button(
            onClick = if (running) onPause else onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (running) Amber else SeniorColors.GreenDark)
        ) {
            Icon(
                if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    running -> "Pause recording"
                    ui.recording -> "Resume recording"
                    else -> "Start recording"
                },
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
        if (ui.recording) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onStop, enabled = ui.recordingPaused, modifier = Modifier.weight(1f)) {
                    Text("Save", color = SeniorColors.TextPrimary)
                }
                OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                    Text("Reset", color = SeniorColors.TextPrimary)
                }
            }
        }

        ui.message?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SeniorColors.GreenDark) }
        if (ui.savedFileName != null && !ui.recording) {
            OutlinedButton(onClick = onShareLast, modifier = Modifier.fillMaxWidth()) {
                Text("Share ${ui.savedFileName}", color = SeniorColors.TextPrimary)
            }
        }
        if (ui.savedCount > 0 && !ui.recording) {
            OutlinedButton(onClick = onShareAll, modifier = Modifier.fillMaxWidth()) {
                Text("Share all ${ui.savedCount} recordings", color = SeniorColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = SeniorColors.TextSecondary)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
    }
}

// ----------------------------------------------------------------------------- preview data

/** Eighteen seconds of made-up walking, phone in pocket, on the phyphox clock (0 s at the start). */
internal fun previewWalkUi(recording: Boolean, paused: Boolean = false): WalkTestUi {
    val n = 900
    val t = FloatArray(n) { i -> i * 0.02f }
    fun s(f: (Float) -> Float) = Series(t, FloatArray(n) { i -> f(t[i]) })
    val lx = s { 1.5f * sin(it * 11f) }
    val ly = s { 2.2f * sin(it * 11.3f) }
    val lz = s { 0.8f * sin(it * 5.6f) }
    return WalkTestUi(
        participant = "P03", activityCode = "WALKPOCKET", recording = recording, elapsedSeconds = if (recording) 18 else 0,
        liveValue = 1.12f, liveLinear = 2.05f, hasLinearSensor = true, hasGyroscope = true,
        linX = lx, linY = ly, linZ = lz,
        absolute = s { 9.8f + 2.2f * sin(it * 11.3f) },
        gyroX = s { 0.9f * sin(it * 5.6f) }, gyroY = s { 0.5f * sin(it * 3.1f) }, gyroZ = s { 1.2f * sin(it * 5.6f + 1f) },
        preFall = s { 1.1f + 0.15f * sin(it * 0.9f) },
        latestSeconds = 17.98f, paused = paused, recordingPaused = recording && paused,
        accelX = s { 1.5f * sin(it * 11f) }, accelY = s { 9.8f + 2.2f * sin(it * 11.3f) }, accelZ = s { 0.8f * sin(it * 5.6f) },
        linAbs = s { 2.4f + 0.6f * sin(it * 11.3f) },
        message = if (recording) null else "Saved P03_WALKPOCKET_R2.csv: 1503 readings, 30.1 s.",
        savedFileName = if (recording) null else "P03_WALKPOCKET_R2.csv", savedCount = if (recording) 0 else 7
    )
}

@Preview(showBackground = true, heightDp = 1400, name = "Walk test: recording")
@Composable
private fun WalkTestRecordingPreview() {
    Column(Modifier.background(Color.White).padding(16.dp).verticalScroll(rememberScrollState())) {
        WalkTestPanel(previewWalkUi(recording = true), {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, heightDp = 1400, name = "Walk test: recording paused")
@Composable
private fun WalkTestPausedPreview() {
    Column(Modifier.background(Color.White).padding(16.dp).verticalScroll(rememberScrollState())) {
        WalkTestPanel(previewWalkUi(recording = true, paused = true), {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** The band the 2 s value falls in, for the walk test (display only; nothing in the detector reads it). */
internal fun movementName(level: FallContext.Level) = when (level) {
    FallContext.Level.STILL -> "Still"
    FallContext.Level.GENTLE -> "Gentle"
    FallContext.Level.MOVING -> "Moving"
}
