package com.pup.seenior.ui.demo

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.detection.FallContext
import com.pup.seenior.ui.theme.SeniorColors
import java.io.File

/**
 * The recorder as a closed card at the bottom of the senior's Home tab. Closed by default, and
 * hidden entirely when [SHOW_DEMO_TOOLS] is off, so a senior's build never shows it. It cannot be
 * closed while recording.
 */
@Composable
fun WalkTestRecorderCard(viewModel: RecorderViewModel = viewModel()) {
    if (!SHOW_DEMO_TOOLS) return
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SeniorColors.GreenBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !viewModel.recording) { open = !open }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Walk-test recorder", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
                Text("For the research team", fontSize = 12.sp, color = SeniorColors.TextSecondary)
            }
            Text(if (open || viewModel.recording) "Close" else "Open", fontSize = 14.sp, color = SeniorColors.GreenDark)
        }
        if (open || viewModel.recording) {
            Spacer(Modifier.height(10.dp))
            RecorderSection(viewModel)
        }
    }
}

/** Top of the live graph, in m/s2. Readings above it are drawn at the top edge. */
private const val GRAPH_MAX = 4f

/**
 * Walk-test recorder: labelled accelerometer recordings, exported in phyphox's CSV format plus
 * SEENior's own 2 s value and step count. For the research team, never for the senior.
 */
@Composable
fun RecorderSection(viewModel: RecorderViewModel = viewModel()) {
    val context = LocalContext.current
    val view = LocalView.current

    // The phone sits in a pocket while recording; keep the screen awake so readings keep coming.
    DisposableEffect(viewModel.recording) {
        view.keepScreenOn = viewModel.recording
        onDispose { view.keepScreenOn = false }
    }
    // Sensors only while this section is on screen.
    DisposableEffect(Unit) {
        viewModel.listen(true)
        onDispose { viewModel.listen(false) }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "For the research team. Records the accelerometer at 50 Hz, the rate the fall detector uses, " +
                "and saves a CSV in phyphox's format with SEENior's 2 s value, steps and acceleration " +
                "without gravity. Files stay on this phone.",
            fontSize = 13.sp,
            color = SeniorColors.TextSecondary
        )

        LiveGraph(viewModel.graph)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("2 s value", viewModel.liveValue?.let { "%.2f m/s²".format(it) } ?: "-")
            Stat("Without g", if (viewModel.hasLinearSensor) viewModel.liveLinear?.let { "%.2f".format(it) } ?: "-" else "no sensor")
            Stat("Steps", if (viewModel.hasStepDetector) viewModel.steps.toString() else "no sensor")
            Stat("Time", "%d:%02d".format(viewModel.elapsedSeconds / 60, viewModel.elapsedSeconds % 60))
        }

        OutlinedTextField(
            value = viewModel.participant,
            onValueChange = { viewModel.participant = it },
            label = { Text("Participant code") },
            singleLine = true,
            enabled = !viewModel.recording,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Activity", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextSecondary)
        RecorderCsv.ACTIVITIES.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { activity ->
                    val selected = viewModel.activityCode == activity.code
                    OutlinedButton(
                        onClick = { viewModel.activityCode = activity.code },
                        enabled = !viewModel.recording,
                        modifier = Modifier.weight(1f),
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

        if (!viewModel.recording) {
            Button(
                onClick = { viewModel.start() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SeniorColors.GreenDark)
            ) { Text("Start recording", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        } else {
            HoldToStop(onStop = { viewModel.stop() })
            OutlinedButton(onClick = { viewModel.discard() }, modifier = Modifier.fillMaxWidth()) {
                Text("Discard this recording", color = SeniorColors.TextSecondary)
            }
        }

        viewModel.message?.let {
            Text(it, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = SeniorColors.GreenDark)
        }

        viewModel.savedFile?.let { file ->
            OutlinedButton(onClick = { share(context, listOf(file)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Share ${file.name}", color = SeniorColors.TextPrimary)
            }
        }

        val all = viewModel.savedRecordings
        if (all.isNotEmpty() && !viewModel.recording) {
            OutlinedButton(onClick = { share(context, all) }, modifier = Modifier.fillMaxWidth()) {
                Text("Share all ${all.size} recordings", color = SeniorColors.TextPrimary)
            }
            Text(
                "Also on the phone at ${viewModel.recordingsDir().absolutePath}",
                fontSize = 11.sp,
                color = SeniorColors.TextSecondary
            )
        }
    }
}

/** The 2 s value over the last 10 s, with the current activity threshold as a dashed line. */
@Composable
private fun LiveGraph(points: FloatArray) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(SeniorColors.GreenLightBg, RoundedCornerShape(10.dp))
            .border(1.dp, SeniorColors.GreenBorder, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(124.dp)) {
            val h = size.height
            val w = size.width
            fun yOf(v: Float) = h - (v.coerceIn(0f, GRAPH_MAX) / GRAPH_MAX) * h

            val threshold = yOf(FallContext.ACTIVE_MIN)
            drawLine(
                color = Color(0xFFD9822B),
                start = Offset(0f, threshold),
                end = Offset(w, threshold),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
            if (points.size > 1) {
                val step = w / (RecorderViewModel.GRAPH_POINTS - 1)
                val offset = (RecorderViewModel.GRAPH_POINTS - points.size) * step
                val path = Path()
                points.forEachIndexed { i, v ->
                    val x = offset + i * step
                    if (i == 0) path.moveTo(x, yOf(v)) else path.lineTo(x, yOf(v))
                }
                drawPath(path, color = SeniorColors.GreenDark, style = Stroke(width = 3f))
            }
        }
        Text(
            "Dashed line: current threshold %.1f m/s². Top: %.0f m/s².".format(FallContext.ACTIVE_MIN, GRAPH_MAX),
            fontSize = 10.sp,
            color = SeniorColors.TextSecondary,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = SeniorColors.TextSecondary)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
    }
}

/** A long press stops the recording, so a touch in the pocket can't end it by accident. */
@Composable
private fun HoldToStop(onStop: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color(0xFFC0392B), RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onStop() }) }
    ) {
        Text("Recording. Hold to stop and save", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

private fun share(context: android.content.Context, files: List<File>) {
    val authority = "${context.packageName}.recorder"
    val uris = ArrayList(files.map { FileProvider.getUriForFile(context, authority, it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.setType("text/csv").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share recordings"))
}
