package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.RoutineDto
import com.pup.seenior.network.dto.RoutineRowDto
import com.pup.seenior.network.dto.RoutineTimingDto
import com.pup.seenior.session.FamilySession
import com.pup.seenior.session.SessionState
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Loads one paired senior's Routine Fingerprint from the cloud. Read-only: the phone is the
 * only writer. A state of its own (not `FamilyError`) because a failure here only hides this
 * card and must not disturb the rest of the senior's screen.
 */
class FamilyRoutineViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface State {
        data object Loading : State
        data class Loaded(val routine: RoutineDto) : State
        data object Failed : State
    }

    var state by mutableStateOf<State>(State.Loading)
        private set

    fun load(seniorSyncId: String) {
        val token = FamilySession.getToken(getApplication()) ?: return
        state = State.Loading
        viewModelScope.launch {
            state = try {
                State.Loaded(RetrofitClient.api.getRoutine(seniorSyncId, "Bearer $token"))
            } catch (e: HttpException) {
                SessionState.handleIfUnauthorized(getApplication(), e)
                State.Failed
            } catch (e: IOException) {
                State.Failed
            }
        }
    }
}

/** Plain-language wording for the fingerprint. Pure, so it is unit-tested. */
object RoutineWording {

    val BLOCK_ORDER = listOf("morning", "afternoon", "evening", "night")

    /** Display order inside a block; unknown features (a newer phone) are left out. */
    val FEATURE_ORDER = listOf(
        "inactivity_duration",
        "movement_score",
        "step_count",
        "screen_unlock_count",
        "screen_idle_duration"
    )

    fun blockLabel(block: String, fil: Boolean): String = when (block) {
        "morning" -> if (fil) "Umaga" else "Morning"
        "afternoon" -> if (fil) "Hapon" else "Afternoon"
        "evening" -> if (fil) "Gabi" else "Evening"
        "night" -> if (fil) "Madaling-araw / Tulog" else "Night"
        else -> block
    }

    fun featureLabel(feature: String, fil: Boolean): String = when (feature) {
        "inactivity_duration" -> if (fil) "Karaniwang tagal na hindi gumagalaw" else "Usually still for"
        "movement_score" -> if (fil) "Antas ng galaw" else "Movement level"
        "step_count" -> if (fil) "Mga hakbang" else "Steps"
        "screen_unlock_count" -> if (fil) "Pagbukas ng phone" else "Phone unlocks"
        "screen_idle_duration" -> if (fil) "Tagal na hindi ginagalaw ang phone" else "Phone untouched for"
        else -> feature
    }

    /** "45 sec", "25 min", "1 hr 5 min". */
    fun duration(seconds: Double, fil: Boolean): String {
        val total = seconds.coerceAtLeast(0.0).toLong()
        val hr = if (fil) "oras" else "hr"
        val min = if (fil) "min" else "min"
        val sec = if (fil) "seg" else "sec"
        return when {
            total < 60 -> "$total $sec"
            total < 3600 -> "${(total + 30) / 60} $min"
            else -> {
                val minutes = (total + 30) / 60
                val rem = minutes % 60
                if (rem == 0L) "${minutes / 60} $hr" else "${minutes / 60} $hr $rem $min"
            }
        }
    }

    /** The median in the units a person reads, e.g. "25 min" or "412". */
    fun value(row: RoutineRowDto, fil: Boolean): String = when (row.featureName) {
        "inactivity_duration", "screen_idle_duration" -> duration(row.medianValue, fil)
        "movement_score" -> "%.2f".format(row.medianValue)
        else -> Math.round(row.medianValue).toString()
    }

    /** How far it normally swings, from the MAD: the width of an ordinary day around the median. */
    fun swing(row: RoutineRowDto, fil: Boolean): String = when (row.featureName) {
        "inactivity_duration", "screen_idle_duration" -> "± ${duration(row.madValue, fil)}"
        "movement_score" -> "± %.2f".format(row.madValue)
        else -> "± ${Math.round(row.madValue)}"
    }

    fun seedCount(rows: List<RoutineRowDto>): Int = rows.count { it.isSeed }
}

@Composable
fun RoutineCard(
    seniorSyncId: String,
    viewModel: FamilyRoutineViewModel = viewModel(),
    historyViewModel: FamilyRoutineHistoryViewModel = viewModel()
) {
    val copy = LocalFamilyCopy.current
    val fil = copy.language == "fil"

    LaunchedEffect(seniorSyncId) {
        viewModel.load(seniorSyncId)
        historyViewModel.load(seniorSyncId)
    }

    Text(
        if (fil) "Pang-araw-araw na gawain" else "Daily routine",
        color = FamilyColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (val s = viewModel.state) {
            FamilyRoutineViewModel.State.Loading ->
                Text(if (fil) "Naglo-load…" else "Loading…", color = FamilyColors.TextSecondary, fontSize = 14.sp)

            FamilyRoutineViewModel.State.Failed ->
                Text(
                    if (fil) "Hindi ma-load ang gawain ngayon. Subukan muli mamaya."
                    else "Could not load the routine right now. Try again later.",
                    color = FamilyColors.TextSecondary,
                    fontSize = 14.sp
                )

            is FamilyRoutineViewModel.State.Loaded -> RoutineBody(s.routine.rows, s.routine.timing, fil)
        }
    }
}

/** The family's blue, softened to a gradient, for the two cards below. */
private val CardGradient = Brush.verticalGradient(listOf(Color(0xFFF6F9FE), Color(0xFFE4EDF9)))
private val CardBorder = FamilyColors.BlueBorder
private val TagLearnedBg = Color(0xFFDCE8F8)
private val TagSetupBg = Color(0xFFECEBE6)
private val TagSetupFg = Color(0xFF605F59)

@Composable
private fun GradientCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardGradient)
            .border(1.5.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { content() }
}

@Composable
private fun RoutineBody(rows: List<RoutineRowDto>, timing: RoutineTimingDto?, fil: Boolean) {
    if (rows.isEmpty()) {
        Text(
            if (fil) "Wala pang naibabahagi. Lalabas ito kapag nakakonekta na ang phone ng senior."
            else "Nothing shared yet. It appears once the senior's phone has connected.",
            color = FamilyColors.TextSecondary,
            fontSize = 14.sp
        )
        return
    }

    val summary = RoutineSummary.summarize(rows)
    val parts = FamilyRoutineParts.parts(rows, timing, fil)

    GradientCard {
        Text(
            RoutineSummary.progress(summary, fil),
            color = FamilyColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFFD3E1F4))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(FamilyRoutineParts.learnedFraction(summary))
                    .height(8.dp)
                    .background(FamilyColors.Blue)
            )
        }
        Text(FamilyRoutineParts.learningNote(fil), color = FamilyColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
    }

    GradientCard {
        parts.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = CardBorder.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 4.dp))
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = FamilyColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        p.hours ?: "",
                        color = FamilyColors.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp).weight(1f)
                    )
                    Text(
                        FamilyRoutineParts.learnedTag(p.learned, fil),
                        color = if (p.learned) FamilyColors.BlueDark else TagSetupFg,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (p.learned) TagLearnedBg else TagSetupBg)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
                p.headline?.let {
                    Text(
                        it,
                        color = if (p.learned || p.block == "night") FamilyColors.TextPrimary else FamilyColors.TextSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                if (p.lines.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFBFDFF))
                            .border(1.dp, Color(0xFFC9DAF0), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        p.lines.forEachIndexed { j, l ->
                            if (j > 0) HorizontalDivider(color = Color(0xFFC9DAF0))
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                Text(l.label, color = Color(0xFF4D4D4D), fontSize = 13.sp, modifier = Modifier.weight(0.6f))
                                Text(
                                    l.value,
                                    color = FamilyColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(0.4f).padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
