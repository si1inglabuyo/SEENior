package com.pup.seenior.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.ui.SeniorStrings
import com.pup.seenior.ui.profile.GreenBackHeader
import com.pup.seenior.ui.routine.CardBox
import com.pup.seenior.ui.routine.Line
import com.pup.seenior.ui.routine.LineList
import com.pup.seenior.ui.routine.SeniorRoutineModel
import com.pup.seenior.ui.routine.SeniorRoutineText
import com.pup.seenior.ui.routine.Tag
import com.pup.seenior.ui.routine.TagKind
import com.pup.seenior.ui.theme.SeniorColors
import com.pup.seenior.ui.wellness.OutcomeMessages
import com.pup.seenior.ui.wellness.WellnessMessages
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AmberBg = Color(0xFFFDF3E7)
private val AmberBorder = Color(0xFFE9C98A)

/**
 * One alert on its own screen, opened from the Alerts tab: why the phone asked, what happened,
 * what (if anything) it changed, and, collapsed, the working. Display only. Nothing here
 * reopens, escalates or edits an alert.
 *
 * It deliberately shows no number for "how long you were still": the alert stores only its
 * score, and the usual has moved since, so a figure rebuilt now could disagree with what the
 * phone saw at the time. The wording of the finding ("noticeably", "far outside") is used instead.
 */
@Composable
fun AlertDetailScreen(
    alert: Alert,
    language: String,
    copy: SeniorStrings.Copy,
    viewModel: AlertsViewModel,
    onBack: () -> Unit
) {
    val fil = language == WellnessMessages.FILIPINO
    val description = AlertDescriptions.describe(language, alert.triggerType, alert.timeBlock, alert.deviationScore)
    val computation = AlertDescriptions.computationLine(
        language, alert.triggerType, alert.deviationScore,
        viewModel.mlFlagScores[alert.alertId], alert.fallEvidence, alert.fallTolerance
    )
    val care = remember(viewModel.alerts, viewModel.baseline) {
        SeniorRoutineModel.care(viewModel.alerts, viewModel.baseline, System.currentTimeMillis())
    }
    val changedBy = care.firstOrNull { it.block == alert.timeBlock && it.trigger == alert.triggerType }
        ?.takeIf { c -> c.answeredOn.contains(dayOf(alert.triggeredAt)) }
    var showWork by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        GreenBackHeader(title = AlertDetailText.title(fil), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                AlertDetailText.dateTime(alert.triggeredAt, fil),
                color = SeniorColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Row { Tag(outcomeLabel(alert.status, copy), outcomeKind(alert.status)) }

            // Why the phone asked: the same sentence the prompt gave at the time.
            CardBox {
                SectionTitle(AlertDetailText.whyHeading(fil))
                Text(description, color = SeniorColors.TextPrimary, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
                LineList(listOf(Line(AlertDetailText.partOfDay(fil), SeniorRoutineText.blockName(alert.timeBlock, fil))))
            }

            // What happened, from what the phone recorded.
            val said = outcomeAnswer(alert, language)
            val answeredAt = if (alert.status == "self_cancelled") alert.resolvedAt else null
            val happened = buildList {
                add(Line(AlertDetailText.asked(fil), AlertDetailText.time(alert.triggeredAt)))
                answeredAt?.let { add(Line(AlertDetailText.answered(fil), AlertDetailText.time(it))) }
                said?.let { add(Line(AlertDetailText.said(fil), it)) }
                if (alert.triggerType != "sos") {
                    add(Line(AlertDetailText.family(fil), AlertDetailText.familyValue(familyWasTold(alert), fil)))
                }
            }
            CardBox {
                SectionTitle(AlertDetailText.happenedHeading(fil))
                LineList(happened)
            }

            // Only when this alert is one of the ones loosening the trigger right now.
            changedBy?.let { c ->
                CardBox(bg = AmberBg, border = AmberBorder) {
                    SectionTitle(AlertDetailText.changedHeading(fil))
                    Text(
                        AlertDetailText.changedText(c.answeredOn.size, c.block, fil),
                        color = SeniorColors.TextPrimary, fontSize = 14.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        AlertDetailText.changedWhere(fil),
                        color = SeniorColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // The working, for anyone who wants it. Collapsed so a senior is not shown scores.
            if (computation != null) {
                CardBox(bg = SeniorColors.BlueLightBg, border = SeniorColors.BlueBorder) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showWork = !showWork },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(AlertDetailText.workHeading(fil), color = Color(0xFF2F5FA8), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (showWork) "⌃" else "⌄", color = Color(0xFF2F5FA8), fontSize = 14.sp)
                    }
                    if (showWork) {
                        Spacer(Modifier.height(8.dp))
                        ComputationLine(computation)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color(0xFF4D4D4D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

private fun dayOf(millis: Long): java.time.LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

internal fun outcomeLabel(status: String, copy: SeniorStrings.Copy): String = when (status) {
    "self_cancelled" -> "✓ ${copy.alertStatusSelfCancelled}"
    else -> statusLabel(status, copy)
}

internal fun outcomeKind(status: String): TagKind = when (status) {
    "self_cancelled", "false_positive" -> TagKind.OK
    "auto_cleared" -> TagKind.WAIT
    "pending", "acknowledged_family", "escalated_barangay" -> TagKind.SOFT
    else -> TagKind.SEED
}

/** What the senior tapped after "I'm safe", with their own words for "Others" when they typed any. */
private fun outcomeAnswer(alert: Alert, language: String): String? {
    val code = alert.outcomeReason ?: return null
    val label = OutcomeMessages.forAlert(language, alert.triggerType)?.options?.firstOrNull { it.code == code }?.label
        ?: return null
    val detail = alert.outcomeDetail?.takeIf { it.isNotBlank() }
    return if (detail != null) "$label: $detail" else label
}

/** True when the escalation timeline has a family step, or the family acted on the alert. */
internal fun familyWasTold(alert: Alert): Boolean =
    alert.status == "acknowledged_family" || alert.escalationSteps.contains("escalated_family")

/** The words only this screen uses, English and Filipino. */
internal object AlertDetailText {
    fun title(fil: Boolean) = if (fil) "Detalye ng alerto" else "Alert details"
    fun whyHeading(fil: Boolean) = if (fil) "Bakit nagtanong ang telepono" else "Why your phone asked"
    fun happenedHeading(fil: Boolean) = if (fil) "Ano ang nangyari" else "What happened"
    fun changedHeading(fil: Boolean) = if (fil) "Ano ang binago nito" else "What it changed"
    fun workHeading(fil: Boolean) = if (fil) "Paano ito kinuwenta" else "How this was worked out"
    fun partOfDay(fil: Boolean) = if (fil) "Bahagi ng araw" else "Part of the day"
    fun asked(fil: Boolean) = if (fil) "Nagtanong ang telepono" else "Phone asked"
    fun answered(fil: Boolean) = if (fil) "Sumagot kayo" else "You answered"
    fun said(fil: Boolean) = if (fil) "Sinabi ninyo" else "You said"
    fun family(fil: Boolean) = if (fil) "Pamilya" else "Family"
    fun familyValue(told: Boolean, fil: Boolean) = when {
        told && fil -> "Naabisuhan"
        told -> "Told"
        fil -> "Hindi naabisuhan"
        else -> "Not told"
    }

    fun changedText(count: Int, block: String, fil: Boolean): String {
        val name = SeniorRoutineText.blockName(block, fil).lowercase()
        return if (fil) "Isa ito sa $count sagot ninyo kamakailan sa $name, kaya medyo mas matagal nang maghihintay ang telepono doon."
        else "This was 1 of $count recent answers for your $name, so your phone now waits a little longer there."
    }

    fun changedWhere(fil: Boolean) =
        if (fil) "Makikita ang buong paliwanag sa Profile › Aking routine › Pagbabago."
        else "The full explanation is in Profile › My routine › Changes."

    fun time(millis: Long): String = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(millis))

    fun dateTime(millis: Long, fil: Boolean): String =
        SimpleDateFormat("EEE d MMM, h:mm a", if (fil) Locale("fil", "PH") else Locale.ENGLISH).format(Date(millis))
}
