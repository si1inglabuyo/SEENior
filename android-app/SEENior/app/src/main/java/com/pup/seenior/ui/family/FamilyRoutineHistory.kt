package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.RoutineHistoryRowDto
import com.pup.seenior.session.FamilySession
import com.pup.seenior.session.SessionState
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

/**
 * Loads how a paired senior's routine was learned: one copy of the fingerprint per day, 30 days
 * at most (migration 0024). Read-only. On any failure it stays empty, since this is a supporting
 * view inside the Daily routine card and must not disturb the rest of the senior's screen.
 */
class FamilyRoutineHistoryViewModel(application: Application) : AndroidViewModel(application) {

    var rows by mutableStateOf<List<RoutineHistoryRowDto>>(emptyList())
        private set

    fun load(seniorSyncId: String) {
        val token = FamilySession.getToken(getApplication()) ?: return
        viewModelScope.launch {
            rows = try {
                RetrofitClient.api.getRoutineHistory(seniorSyncId, "Bearer $token").rows
            } catch (e: HttpException) {
                SessionState.handleIfUnauthorized(getApplication(), e)
                emptyList()
            } catch (e: IOException) {
                emptyList()
            }
        }
    }
}

/**
 * The plain-language reading of the history, shown inside each block of the Daily routine card.
 * Pure, so it is unit-tested.
 */
object RoutineHistorySummary {

    /** How long the senior is usually still in a block, on one day. */
    data class Point(val date: String, val seconds: Double, val isEstimate: Boolean, val samples: Int)

    data class BlockTrend(val block: String, val points: List<Point>) {
        /** The questionnaire's starting guess: the earliest day shown. */
        val start: Point get() = points.first()
        val latest: Point get() = points.last()

        /**
         * Whether any real day has moved this block off the setup answer. Seed cells carry a
         * sample count of 0, so a block with only seed points is a flat line at the guess, which
         * says nothing and is not drawn.
         */
        val learned: Boolean get() = points.any { it.samples > 0 }
    }

    data class Trend(
        val blocks: List<BlockTrend>,
        val hasEstimate: Boolean,
        /** First day the phone itself saved a copy, if any. */
        val firstSavedDate: String?
    ) {
        val anyLearned: Boolean get() = blocks.any { it.learned }

        /** The trend to draw for [block], or null when it has learned nothing yet. */
        fun forBlock(block: String): BlockTrend? = blocks.firstOrNull { it.block == block && it.learned }
    }

    /** One line per block, built from the usual-stillness figure. Null when there is not yet a trend to draw. */
    fun summarize(rows: List<RoutineHistoryRowDto>): Trend? {
        val stillness = rows.filter { it.featureName == "inactivity_duration" }
        if (stillness.map { it.day }.distinct().size < 2) return null

        val blocks = RoutineWording.BLOCK_ORDER.mapNotNull { block ->
            val points = stillness.filter { it.timeBlock == block }
                .sortedBy { it.day }
                .map { Point(it.day, it.medianValue, it.isEstimate, it.sampleCount) }
            if (points.size < 2) null else BlockTrend(block, points)
        }
        if (blocks.isEmpty()) return null

        // The notes describe the drawn lines, so they only count blocks that are actually drawn.
        val drawn = blocks.filter { it.learned }.flatMap { it.points }
        return Trend(
            blocks = blocks,
            hasEstimate = drawn.any { it.isEstimate },
            firstSavedDate = drawn.filter { !it.isEstimate }.minOfOrNull { it.date }
        )
    }

    private val MONTHS_EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTHS_FIL = listOf("Ene", "Peb", "Mar", "Abr", "May", "Hun", "Hul", "Ago", "Set", "Okt", "Nob", "Dis")

    fun shortDate(iso: String, fil: Boolean): String {
        val d = LocalDate.parse(iso)
        return "${(if (fil) MONTHS_FIL else MONTHS_EN)[d.monthValue - 1]} ${d.dayOfMonth}"
    }

    fun startedAt(block: BlockTrend, fil: Boolean): String {
        val from = RoutineWording.duration(block.start.seconds, fil)
        val now = RoutineWording.duration(block.latest.seconds, fil)
        return if (fil) "Nagsimula sa hulang $from, ngayon ay $now"
        else "Started at a guess of $from, now $now"
    }

    /**
     * The usual stillness for [block] as the fingerprint stood on the latest day before [today]
     * (normally yesterday). It is the app's idea of the senior's normal on that day, not what the
     * senior did, so it must never be worded as activity. Null when no earlier day exists, or when
     * that day was still only the setup answer (nothing real behind it, so nothing to compare).
     */
    fun previousDay(rows: List<RoutineHistoryRowDto>, block: String, today: LocalDate): Point? =
        rows.filter { it.featureName == "inactivity_duration" && it.timeBlock == block }
            .filter { LocalDate.parse(it.day).isBefore(today) }
            .maxByOrNull { it.day }
            ?.takeIf { it.sampleCount > 0 }
            ?.let { Point(it.day, it.medianValue, it.isEstimate, it.sampleCount) }

    /** "Yesterday, the usual was: still for up to 46 min". Names the date when it was not yesterday. */
    fun previousDayLabel(point: Point, today: LocalDate, fil: Boolean): String {
        val duration = RoutineWording.duration(point.seconds, fil)
        val isYesterday = LocalDate.parse(point.date) == today.minusDays(1)
        val whenText = when {
            isYesterday -> if (fil) "Kahapon" else "Yesterday"
            else -> shortDate(point.date, fil)
        }
        return if (fil) "$whenText, ang karaniwan ay hindi gumagalaw nang hanggang $duration"
        else "$whenText, the usual was still for up to $duration"
    }

    /** Said once under the blocks, in plain words, when any drawn day was worked out afterwards. */
    fun estimateNote(fil: Boolean) =
        if (fil) "Ang mga naunang araw ay tantiya lamang, batay sa naitalang pang-araw-araw na kilos."
        else "Earlier days are an estimate worked out from your parent's saved daily activity."

    fun savedNote(date: String, fil: Boolean) =
        if (fil) "Mula ${shortDate(date, true)}, ito ay naitala ng app tuwing gabi."
        else "From ${shortDate(date, false)}, the app saved this each night."
}

/** Days the app saved are solid; days worked out afterwards are dotted. */
@Composable
internal fun TrendLine(points: List<RoutineHistorySummary.Point>) {
    val color = FamilyColors.Blue
    Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        val low = points.minOf { it.seconds }
        val range = (points.maxOf { it.seconds } - low).coerceAtLeast(1.0)
        val pad = 6f
        val stepX = size.width / (points.size - 1)
        fun x(i: Int) = i * stepX
        fun y(v: Double) = size.height - pad - ((v - low) / range * (size.height - 2 * pad)).toFloat()

        for (i in 1 until points.size) {
            drawLine(
                color = color,
                start = Offset(x(i - 1), y(points[i - 1].seconds)),
                end = Offset(x(i), y(points[i].seconds)),
                strokeWidth = 4f,
                pathEffect = if (points[i].isEstimate) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
            )
        }
    }
}
