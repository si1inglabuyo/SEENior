package com.pup.seenior.ui.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.pup.seenior.ui.theme.SeniorColors
import kotlin.math.abs

/** One sensor series: times in seconds (relative to the graph's zero) and values, oldest first. */
class Series(val t: FloatArray, val v: FloatArray) {
    val isEmpty: Boolean get() = t.isEmpty()

    companion object {
        val EMPTY = Series(FloatArray(0), FloatArray(0))
    }
}

/** A horizontal threshold line or a vertical event marker. */
data class GraphLine(val at: Float, val label: String, val color: Color)

/** The visible time range, in seconds. Shared by graphs that should zoom together. */
data class GraphView(val start: Float, val end: Float) {
    val span: Float get() = end - start
}

/**
 * A phyphox-style graph: pinch to zoom the time axis, drag to pan, tap to read the nearest
 * reading's time and value. Each dot is one real sensor reading; the line between dots is only
 * drawn to make the shape readable.
 */
@Composable
fun ZoomGraph(
    series: Series,
    color: Color,
    yMax: Float,
    unit: String,
    thresholds: List<GraphLine>,
    markers: List<GraphLine>,
    view: GraphView,
    onViewChange: (GraphView) -> Unit,
    limits: GraphView,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    height: Int = 150
) {
    val measurer = rememberTextMeasurer()
    var picked by remember(series) { mutableStateOf<Int?>(null) }
    // The gesture handlers outlive recompositions, so they read the latest view through these.
    val currentView by rememberUpdatedState(view)
    val currentOnViewChange by rememberUpdatedState(onViewChange)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            picked?.let { i ->
                if (i < series.t.size) "t = %+.2f s · %.2f %s".format(series.t[i], series.v[i], unit) else ""
            } ?: if (interactive) "Pinch to zoom, drag to move, tap a point to read it" else " ",
            fontSize = 11.sp,
            fontWeight = if (picked != null) FontWeight.Bold else FontWeight.Normal,
            color = if (picked != null) SeniorColors.TextPrimary else SeniorColors.TextSecondary
        )
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
                                val minSpan = 0.1f
                                val maxSpan = (limits.end - limits.start).coerceAtLeast(minSpan)
                                val v = currentView
                                val newSpan = (v.span / zoom).coerceIn(minSpan, maxSpan)
                                val anchor = v.start + v.span * (centroid.x / w)
                                var start = anchor - newSpan * (centroid.x / w) - pan.x / w * newSpan
                                start = start.coerceIn(limits.start, (limits.end - newSpan).coerceAtLeast(limits.start))
                                currentOnViewChange(GraphView(start, start + newSpan))
                            }
                        }
                        .pointerInput(series, view) {
                            detectTapGestures { offset ->
                                val tap = view.start + view.span * (offset.x / size.width)
                                var best: Int? = null
                                var bestDist = Float.MAX_VALUE
                                for (i in series.t.indices) {
                                    val d = abs(series.t[i] - tap)
                                    if (d < bestDist) {
                                        bestDist = d
                                        best = i
                                    }
                                }
                                picked = best
                            }
                        }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height((height - 8).dp)) {
                val w = size.width
                val h = size.height
                fun x(t: Float) = (t - view.start) / view.span * w
                fun y(v: Float) = h - (v.coerceIn(0f, yMax) / yMax) * h
                val small = TextStyle(fontSize = 9.sp)

                for (line in thresholds) {
                    val yy = y(line.at)
                    drawLine(line.color, Offset(0f, yy), Offset(w, yy), 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
                    drawText(measurer, line.label, Offset(4f, (yy - 14f).coerceAtLeast(0f)),
                        small.copy(color = line.color))
                }
                for (m in markers) {
                    if (m.at < view.start || m.at > view.end) continue
                    val xx = x(m.at)
                    drawLine(m.color, Offset(xx, 0f), Offset(xx, h), 2f)
                    drawText(measurer, m.label, Offset(xx + 3f, 2f), small.copy(color = m.color))
                }

                // The readings in view, joined; dots once zoomed in enough to see them apart.
                val path = Path()
                var started = false
                var shown = 0
                for (i in series.t.indices) {
                    val t = series.t[i]
                    if (t < view.start - 0.05f || t > view.end + 0.05f) continue
                    val px = x(t)
                    val py = y(series.v[i])
                    if (!started) {
                        path.moveTo(px, py)
                        started = true
                    } else {
                        path.lineTo(px, py)
                    }
                    shown++
                }
                drawPath(path, color, style = Stroke(width = 2.5f))
                if (shown in 1..120) {
                    for (i in series.t.indices) {
                        val t = series.t[i]
                        if (t < view.start || t > view.end) continue
                        drawCircle(color, 3.5f, Offset(x(t), y(series.v[i])))
                    }
                }
                picked?.let { i ->
                    if (i < series.t.size) {
                        val px = x(series.t[i])
                        val py = y(series.v[i])
                        drawLine(Color.Black, Offset(px, 0f), Offset(px, h), 1f)
                        drawCircle(Color.Black, 6f, Offset(px, py), style = Stroke(2f))
                    }
                }
                drawText(measurer, "%.0f %s".format(yMax, unit), Offset(w - 70f, 2f),
                    small.copy(color = SeniorColors.TextSecondary))
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("%+.2f s".format(view.start), fontSize = 10.sp, color = SeniorColors.TextSecondary,
                modifier = Modifier.weight(1f))
            Text("%+.2f s".format(view.end), fontSize = 10.sp, color = SeniorColors.TextSecondary)
        }
    }
}
