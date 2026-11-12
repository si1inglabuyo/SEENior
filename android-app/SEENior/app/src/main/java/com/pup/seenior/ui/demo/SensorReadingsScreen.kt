package com.pup.seenior.ui.demo

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.detection.FallCheckMonitor.Outcome
import com.pup.seenior.detection.FallCheckMonitor.Status
import com.pup.seenior.ui.theme.SeniorColors
import java.io.File

/** The two screens inside Sensor readings. */
enum class SensorTab { FALL_DETECTION, WALK_TEST }

/**
 * A small line-graph button for the bottom right of the Home tab, just above the tab bar. It
 * opens Sensor readings. Absent when [SHOW_DEMO_TOOLS] is off, so a senior's build never shows it.
 */
@Composable
fun SensorReadingsButton(modifier: Modifier = Modifier) {
    if (!SHOW_DEMO_TOOLS) return
    var open by rememberSaveable { mutableStateOf(false) }
    SmallFloatingActionButton(
        onClick = { open = true },
        modifier = modifier,
        containerColor = Color.White,
        contentColor = SeniorColors.GreenDark
    ) {
        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Sensor readings")
    }
    if (open) SensorReadingsDialog(onClose = { open = false })
}

/** Full-screen Sensor readings, wired to the live sensors. */
@Composable
fun SensorReadingsDialog(onClose: () -> Unit, viewModel: RecorderViewModel = viewModel()) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(SensorTab.FALL_DETECTION) }

    Dialog(
        onDismissRequest = { if (!viewModel.recording) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        val view = LocalView.current
        // The phone sits in a pocket while recording; keep the screen awake so readings keep coming.
        DisposableEffect(viewModel.recording) {
            view.keepScreenOn = viewModel.recording
            onDispose { view.keepScreenOn = false }
        }
        // Sensors run only while this screen is open.
        DisposableEffect(Unit) {
            viewModel.listen(true)
            onDispose { viewModel.listen(false) }
        }

        SensorReadingsContent(
            tab = tab,
            onTab = { tab = it },
            onClose = onClose,
            canClose = !viewModel.recording,
            fallDetection = {
                FallDetectionPanel(
                    ui = FallCheckUi(
                        snapshot = viewModel.fallSnapshot,
                        accelX = viewModel.accelX, accelY = viewModel.accelY, accelZ = viewModel.accelZ,
                        linX = viewModel.linX, linY = viewModel.linY, linZ = viewModel.linZ,
                        withGravity = viewModel.withGravity, hasLinearSensor = viewModel.hasLinearSensor,
                        magnitude = viewModel.accelSeries, rotation = viewModel.rotationSeries,
                        originMs = viewModel.graphOriginMs, latestSeconds = viewModel.latestSeconds,
                        paused = viewModel.graphPaused,
                        captured = viewModel.graphCaptured, hasGyroscope = viewModel.hasGyroscope
                    ),
                    onPause = { viewModel.pauseGraphs() },
                    onResume = { viewModel.resumeGraphs() },
                    onReset = { viewModel.resetFallCheck() },
                    onGravity = { viewModel.withGravity = it }
                )
            },
            walkTest = {
                WalkTestPanel(
                    ui = WalkTestUi(
                        participant = viewModel.participant, activityCode = viewModel.activityCode,
                        recording = viewModel.recording, recordingPaused = viewModel.recordingPaused,
                        withGravity = viewModel.withGravity, elapsedSeconds = viewModel.elapsedSeconds,
                        liveValue = viewModel.liveValue, liveLinear = viewModel.liveLinear,
                        hasLinearSensor = viewModel.hasLinearSensor, hasGyroscope = viewModel.hasGyroscope,
                        accelX = viewModel.accelX, accelY = viewModel.accelY, accelZ = viewModel.accelZ,
                        linX = viewModel.linX, linY = viewModel.linY, linZ = viewModel.linZ,
                        absolute = viewModel.accelSeries, linAbs = viewModel.linAbs,
                        gyroX = viewModel.gyroX, gyroY = viewModel.gyroY, gyroZ = viewModel.gyroZ,
                        preFall = viewModel.preFallSeries,
                        latestSeconds = viewModel.latestSeconds, paused = viewModel.graphPaused,
                        message = viewModel.message,
                        savedFileName = viewModel.savedFile?.name, savedCount = viewModel.savedRecordings.size
                    ),
                    onParticipant = { viewModel.participant = it },
                    onActivity = { viewModel.activityCode = it },
                    onStart = { viewModel.start() },
                    onStop = { viewModel.stop() },
                    onDiscard = { viewModel.discard() },
                    onShareLast = { viewModel.savedFile?.let { share(context, listOf(it)) } },
                    onShareAll = { share(context, viewModel.savedRecordings) },
                    onPause = { viewModel.pauseRecording() },
                    onGravity = { viewModel.withGravity = it },
                    onPauseGraphs = { viewModel.pauseGraphs() },
                    onResumeGraphs = { viewModel.resumeGraphs() }
                )
            }
        )
    }
}

/** The Sensor readings layout: title, the two screen buttons, and the chosen screen. */
@Composable
fun SensorReadingsContent(
    tab: SensorTab,
    onTab: (SensorTab) -> Unit,
    onClose: () -> Unit,
    canClose: Boolean,
    fallDetection: @Composable () -> Unit,
    walkTest: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(SeniorColors.Green).padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = Color.White,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp))
            Text("Sensor readings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, enabled = canClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close",
                    tint = if (canClose) Color.White else Color.White.copy(alpha = 0.4f))
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            TabButton("Fall detection", tab == SensorTab.FALL_DETECTION, Modifier.weight(1f)) { onTab(SensorTab.FALL_DETECTION) }
            TabButton("Walk test", tab == SensorTab.WALK_TEST, Modifier.weight(1f)) { onTab(SensorTab.WALK_TEST) }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            when (tab) {
                SensorTab.FALL_DETECTION -> fallDetection()
                SensorTab.WALK_TEST -> walkTest()
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SeniorColors.GreenDark else SeniorColors.GreenBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) SeniorColors.GreenDark else Color.White,
            contentColor = if (selected) Color.White else SeniorColors.TextPrimary
        )
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

private fun share(context: Context, files: List<File>) {
    if (files.isEmpty()) return
    val authority = "${context.packageName}.recorder"
    val uris = ArrayList(files.map { FileProvider.getUriForFile(context, authority, it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.setType("text/csv").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share recordings"))
}

// ----------------------------------------------------------------------------- previews

@Preview(showBackground = true, widthDp = 400, heightDp = 1700, name = "Sensor readings: fall detection")
@Composable
private fun SensorReadingsFallPreview() {
    SensorReadingsContent(
        tab = SensorTab.FALL_DETECTION, onTab = {}, onClose = {}, canClose = true,
        fallDetection = { FallDetectionPanel(previewFallUi(Outcome.CONFIRMED, Status.PASSED, 9_500, paused = true), {}, {}, {}) },
        walkTest = {}
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 1200, name = "Sensor readings: walk test")
@Composable
private fun SensorReadingsWalkPreview() {
    SensorReadingsContent(
        tab = SensorTab.WALK_TEST, onTab = {}, onClose = {}, canClose = true,
        fallDetection = {},
        walkTest = { WalkTestPanel(previewWalkUi(recording = false), {}, {}, {}, {}, {}, {}, {}, {}) }
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 300, name = "Home: Sensor readings button")
@Composable
private fun SensorReadingsButtonPreview() {
    Box(Modifier.fillMaxSize().background(Color(0xFFF7F7F2))) {
        SmallFloatingActionButton(
            onClick = {}, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Color.White, contentColor = SeniorColors.GreenDark
        ) { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Sensor readings") }
    }
}
