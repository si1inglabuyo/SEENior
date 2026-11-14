package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
 * at most (migration 0024). Read-only. On any failure the card simply stays hidden, since this
 * is a supporting view and must not disturb the rest of the senior's screen.
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

/** The plain-language reading of the history. Pure, so it is unit-tested. */
object RoutineHistorySummary {

    /** How long the senior is usually still in a block, on one day. */
    data class Point(val date: String, val seconds: Double, val isEstimate: Boolean)

    data class BlockTrend(val block: String, val points: List<Point>) {
        /** The questionnaire's starting guess: the earliest day shown. */
        val start: Point get() = points.first()
        val latest: Point get() = points.last()
    }

    data class Trend(
        val blocks: List<BlockTrend>,
        val hasEstimate: Boolean,
        /** First day the phone itself saved a copy, if any. */
        val firstSavedDate: String?
    )

    /** One line per block, built from the usual-stillness figure. Null when there is not yet a trend to draw. */
    fun summarize(rows: List<RoutineHistoryRowDto>): Trend? {
        val stillness = rows.filter { it.featureName == "inactivity_duration" }
        if (stillness.map { it.day }.distinct().size < 2) return null

        val blocks = RoutineWording.BLOCK_ORDER.mapNotNull { block ->
            val points = stillness.filter { it.timeBlock == block }
                .sortedBy { it.day }
                .map { Point(it.day, it.medianValue, it.isEstimate) }
            if (points.size < 2) null else BlockTrend(block, points)
        }
        if (blocks.isEmpty()) return null

        return Trend(
            blocks = blocks,
            hasEstimate = stillness.any { it.isEstimate },
            firstSavedDate = stillness.filter { !it.isEstimate }.minOfOrNull { it.day }
        )
    }

    private val MONTHS_EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTHS_FIL = listOf("Ene", "Peb", "Mar", "Abr", "May", "Hun", "Hul", "Ago", "Set", "Okt", "Nob", "Dis")

    fun shortDate(iso: String, fil: Boolean): String {
        val d = LocalDate.parse(iso)
        return "${(if (fil) MONTHS_FIL else MONTHS_EN)[d.monthValue - 1]} ${d.dayOfMonth}"
    }

    fun title(fil: Boolean) =
        if (fil) "Paano natutunan ang gawaing ito" else "How this routine was learned"

    fun heading(fil: Boolean) =
        if (fil) "Karaniwang pinakamahabang hindi paggalaw" else "Usual longest stillness"

    fun startedAt(block: BlockTrend, fil: Boolean): String {
        val from = RoutineWording.duration(block.start.seconds, fil)
        val now = RoutineWording.duration(block.latest.seconds, fil)
        return if (fil) "Nagsimula sa hulang $from, ngayon ay $now"
        else "Started at a guess of $from, now $now"
    }

    /** Said once under the charts, in plain words, when any of the days were worked out afterwards. */
    fun estimateNote(fil: Boolean) =
        if (fil) "Ang mga naunang araw ay tantiya lamang, batay sa naitalang pang-araw-araw na kilos."
        else "Earlier days are an estimate worked out from your parent's saved daily activity."

    fun savedNote(date: String, fil: Boolean) =
        if (fil) "Mula ${shortDate(date, true)}, ito ay naitala ng app tuwing gabi."
        else "From ${shortDate(date, false)}, the app saved this each night."
}

@Composable
fun RoutineHistoryCard(
    seniorSyncId: String,
    viewModel: FamilyRoutineHistoryViewModel = viewModel()
) {
    val copy = LocalFamilyCopy.current
    val fil = copy.language == "fil"

    LaunchedEffect(seniorSyncId) { viewModel.load(seniorSyncId) }

    val trend = RoutineHistorySummary.summarize(viewModel.rows) ?: return

    Text(
        RoutineHistorySummary.title(fil),
        color = FamilyColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FamilyColors.FieldBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(RoutineHistorySummary.heading(fil), color = FamilyColors.TextSecondary, fontSize = 13.sp)

        trend.blocks.forEach { block ->
            HorizontalDivider(color = FamilyColors.FieldBorder)
            Text(
                RoutineWording.blockLabel(block.block, fil),
                color = FamilyColors.Blue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            TrendLine(block.points)
            Text(RoutineHistorySummary.startedAt(block, fil), color = FamilyColors.TextSecondary, fontSize = 13.sp)
        }

        HorizontalDivider(color = FamilyColors.FieldBorder)
        if (trend.hasEstimate) {
            Text(RoutineHistorySummary.estimateNote(fil), color = FamilyColors.TextSecondary, fontSize = 12.sp)
        }
        trend.firstSavedDate?.let {
            Text(RoutineHistorySummary.savedNote(it, fil), color = FamilyColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

/** Days the app saved are solid; days worked out afterwards are dotted. */
@Composable
private fun TrendLine(points: List<RoutineHistorySummary.Point>) {
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
