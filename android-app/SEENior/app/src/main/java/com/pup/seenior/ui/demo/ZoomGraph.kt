package com.pup.seenior.ui.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pup.seenior.ui.theme.SeniorColors
import kotlin.math.abs

/** One sensor series: times in seconds (relative to the graph's zero) and values, oldest first. */
class Series(val t: FloatArray, val v: FloatArray) {
    val isEmpty: Boolean get() = t.isEmpty()

    companion object {
        val EMPTY = Series(FloatArray(0), FloatArray(0))
    }
}

/** A line drawn on a graph, with its legend name. All lines on one graph share the same times. */
class GraphSeries(val series: Series, val color: Color, val name: String)

/** A horizontal threshold line or a vertical event marker. */
data class GraphLine(val at: Float, val label: String, val color: Color)

/** The visible time range, in seconds. Shared by graphs that should zoom together. */
data class GraphView(val start: Float, val end: Float) {
    val span: Float get() = end - start
}

/**
 * A phyphox-style line graph. While [interactive]: pinch to zoom the time axis, drag to pan, tap
 * to read the nearest reading ([pickLabel] says what to show for it). Each dot is one real sensor
 * reading; the line between dots is only drawn so the shape can be read.
 */
@Composable
fun ZoomGraph(
    title: String,
    lines: List<GraphSeries>,
    yMin: Float,
    yMax: Float,
    unit: String,
    thresholds: List<GraphLine>,
    markers: List<GraphLine>,
    view: GraphView,
    onViewChange: (GraphView) -> Unit,
    limits: GraphView,
    interactive: Boolean,
    pickLabel: (Int) -> String,
    modifier: Modifier = Modifier,
    height: Int = 150,
    /** The time (s) picked on any linked graph; every linked graph marks its own reading nearest to it. */
    pickedTime: Float? = null,
    onPick: (Float) -> Unit = {},
    /** Shows the full-screen button; off for the graph drawn inside the full-screen view. */
    allowFullscreen: Boolean = true,
    /**
     * Pauses or resumes the live graphs from inside the full-screen view (phyphox's play/pause
     * icon). The graph is paused while [interactive], so the icon shows play then, pause when live.
     */
    onTogglePause: (() -> Unit)? = null
) {
    var fullscreen by remember { mutableStateOf(false) }
    if (fullscreen) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary,
                        modifier = Modifier.weight(1f))
                    if (onTogglePause != null) {
                        IconButton(onClick = onTogglePause) {
                            Icon(
                                if (interactive) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (interactive) "Play" else "Pause",
                                tint = SeniorColors.TextPrimary
                            )
                        }
                    }
                    IconButton(onClick = { fullscreen = false }) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ZoomGraph(
                        title = "", lines = lines, yMin = yMin, yMax = yMax, unit = unit, thresholds = thresholds,
                        markers = markers, view = view, onViewChange = onViewChange, limits = limits,
                        interactive = interactive, pickLabel = pickLabel,
                        height = (maxHeight.value - 70f).toInt().coerceAtLeast(150),
                        pickedTime = pickedTime, onPick = onPick, allowFullscreen = false
                    )
                }
            }
        }
    }

    val measurer = rememberTextMeasurer()
    val base = lines.firstOrNull()?.series ?: Series.EMPTY
    val picked = pickedTime?.let { nearestIndex(base, it) }
    // The gesture handlers outlive recompositions, so they read the latest view through these.
    val currentView by rememberUpdatedState(view)
    val currentOnViewChange by rememberUpdatedState(onViewChange)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary,
                modifier = Modifier.weight(1f))
            if (lines.size > 1) {
                lines.forEach { line ->
                    Box(Modifier.padding(start = 8.dp, end = 3.dp).size(8.dp).background(line.color, CircleShape))
                    Text(line.name, fontSize = 11.sp, color = SeniorColors.TextSecondary)
                }
            }
            if (allowFullscreen) {
                IconButton(onClick = { fullscreen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = "Full screen", tint = SeniorColors.TextSecondary)
                }
            }
        }
        val pickedIndex = picked?.takeIf { it < base.t.size }
        if (pickedIndex != null) {
            Text(pickLabel(pickedIndex), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SeniorColors.TextPrimary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, SeniorColors.FieldBorder, RoundedCornerShape(8.dp))
                .padding(4.dp)
                .then(
                    if (!interactive) Modifier else Modifier
                        .pointerInput(limits) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val v = currentView
                                val minSpan = 0.1f
                                val maxSpan = (limits.end - limits.start).coerceAtLeast(minSpan)
                                val newSpan = (v.span / zoom).coerceIn(minSpan, maxSpan)
                                val anchor = v.start + v.span * (centroid.x / w)
                                var start = anchor - newSpan * (centroid.x / w) - pan.x / w * newSpan
                                start = start.coerceIn(limits.start, (limits.end - newSpan).coerceAtLeast(limits.start))
                                currentOnViewChange(GraphView(start, start + newSpan))
                            }
                        }
                        .pointerInput(base, view) {
                            detectTapGestures { offset ->
                                val tap = view.start + view.span * (offset.x / size.width)
                                nearestIndex(base, tap)?.let { onPick(base.t[it]) }
                            }
                        }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height((height - 8).dp)) {
                val w = size.width
                val h = size.height
                val range = (yMax - yMin).coerceAtLeast(0.001f)
                fun x(t: Float) = (t - view.start) / view.span * w
                fun y(v: Float) = h - ((v.coerceIn(yMin, yMax) - yMin) / range) * h
                val small = TextStyle(fontSize = 9.sp)

                if (yMin < 0f) drawLine(SeniorColors.FieldBorder, Offset(0f, y(0f)), Offset(w, y(0f)), 1f)
                for (line in thresholds) {
                    val yy = y(line.at)
                    drawLine(line.color, Offset(0f, yy), Offset(w, yy), 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
                    drawText(measurer, line.label, Offset(4f, (yy - 14f).coerceAtLeast(0f)), small.copy(color = line.color))
                }
                for (m in markers) {
                    if (m.at < view.start || m.at > view.end) continue
                    val xx = x(m.at)
                    drawLine(m.color, Offset(xx, 0f), Offset(xx, h), 2f)
                    drawText(measurer, m.label, Offset(xx + 3f, 2f), small.copy(color = m.color))
                }

                for (line in lines) {
                    val s = line.series
                    val path = Path()
                    var started = false
                    var shown = 0
                    for (i in s.t.indices) {
                        val t = s.t[i]
                        if (t < view.start - 0.05f || t > view.end + 0.05f) continue
                        if (!started) {
                            path.moveTo(x(t), y(s.v[i]))
                            started = true
                        } else {
                            path.lineTo(x(t), y(s.v[i]))
                        }
                        shown++
                    }
                    drawPath(path, line.color, style = Stroke(width = 2.5f))
                    // Dots once zoomed in far enough to see the readings apart.
                    if (shown in 1..120) {
                        for (i in s.t.indices) {
                            if (s.t[i] < view.start || s.t[i] > view.end) continue
                            drawCircle(line.color, 3.5f, Offset(x(s.t[i]), y(s.v[i])))
                        }
                    }
                }
                pickedIndex?.let { i ->
                    val px = x(base.t[i])
                    drawLine(Color.Black, Offset(px, 0f), Offset(px, h), 1f)
                    for (line in lines) {
                        if (i < line.series.v.size) {
                            drawCircle(Color.Black, 6f, Offset(px, y(line.series.v[i])), style = Stroke(2f))
                        }
                    }
                }
                drawText(measurer, "%.0f %s".format(yMax, unit), Offset(w - 80f, 2f),
                    small.copy(color = SeniorColors.TextSecondary))
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("%.1f s".format(view.start), fontSize = 10.sp, color = SeniorColors.TextSecondary,
                modifier = Modifier.weight(1f))
            Text("%.1f s".format(view.end), fontSize = 10.sp, color = SeniorColors.TextSecondary)
        }
    }
}

/** Index of the reading nearest to [time], or null for an empty series. */
private fun nearestIndex(series: Series, time: Float): Int? {
    var best: Int? = null
    var bestDist = Float.MAX_VALUE
    for (i in series.t.indices) {
        val d = abs(series.t[i] - time)
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}
