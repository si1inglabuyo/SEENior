package com.pup.seenior.ui.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pup.seenior.ui.profile.GreenBackHeader
import com.pup.seenior.ui.routine.SeniorRoutineModel.DayState
import com.pup.seenior.ui.routine.SeniorRoutineModel.Figures
import com.pup.seenior.ui.routine.SeniorRoutineModel.Verdict
import com.pup.seenior.ui.theme.SeniorColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class RoutineTab { ROUTINE, YESTERDAY, TODAY, CHANGES }

private val AmberBg = Color(0xFFFDF3E7)
private val AmberBorder = Color(0xFFE9C98A)

internal enum class TagKind(val bg: Color, val fg: Color) {
    OK(Color(0xFFDFF1DC), Color(0xFF2A6325)),
    SEED(Color(0xFFECEBE6), Color(0xFF605F59)),
    SOFT(Color(0xFFFDF0D4), Color(0xFF74540F)),
    WAIT(Color(0xFFE4E8F4), Color(0xFF40507A))
}

/**
 * The senior's "My routine": what the phone has learned about their day, how yesterday and
 * today compare, and why anything moved. Reached from Profile, Account. Read-only and passive:
 * nothing here asks the senior to do anything, and nothing on it feeds detection.
 */
@Composable
fun SeniorRoutineScreen(onBack: () -> Unit, viewModel: SeniorRoutineViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state = viewModel.state
    val fil = state.fil
    var tab by rememberSaveable { mutableStateOf(RoutineTab.ROUTINE) }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        GreenBackHeader(title = SeniorRoutineText.title(fil), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TabBar(tab, fil) { tab = it }
            if (!state.loaded) {
                Text(SeniorRoutineText.loading(fil), color = SeniorColors.TextSecondary, fontSize = 15.sp)
            } else when (tab) {
                RoutineTab.ROUTINE -> RoutineTabContent(state)
                RoutineTab.YESTERDAY -> YesterdayTabContent(state)
                RoutineTab.TODAY -> TodayTabContent(state)
                RoutineTab.CHANGES -> ChangesTabContent(state)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun TabBar(selected: RoutineTab, fil: Boolean, onSelect: (RoutineTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SeniorColors.FieldBackground)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        RoutineTab.values().forEach { t ->
            val on = t == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (t) {
                        RoutineTab.ROUTINE -> SeniorRoutineText.tabRoutine(fil)
                        RoutineTab.YESTERDAY -> SeniorRoutineText.tabYesterday(fil)
                        RoutineTab.TODAY -> SeniorRoutineText.tabToday(fil)
                        RoutineTab.CHANGES -> SeniorRoutineText.tabChanges(fil)
                    },
                    color = if (on) SeniorColors.TextPrimary else SeniorColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun Tag(text: String, kind: TagKind) {
    Text(
        text,
        color = kind.fg,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(kind.bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        maxLines = 1
    )
}

@Composable
internal fun CardBox(
    bg: Color = SeniorColors.GreenLightBg,
    border: Color = SeniorColors.GreenBorder,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { content() }
}

/** One labelled line: name on the left, value on the right, an optional muted note after it. */
internal data class Line(val label: String, val value: String, val note: String? = null)

/** The list-in-a-card the routine tabs share. */
@Composable
internal fun LineList(lines: List<Line>) {
    if (lines.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, SeniorColors.GreenBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        lines.forEachIndexed { i, l ->
            if (i > 0) HorizontalDivider(color = SeniorColors.FieldBorder.copy(alpha = 0.5f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Both sides are weighted so a long value can never squeeze the label down to a
                // sliver (it used to take whatever width it asked for and leave one letter per line).
                Text(l.label, color = Color(0xFF4D4D4D), fontSize = 13.sp, modifier = Modifier.weight(0.6f))
                Column(modifier = Modifier.weight(0.4f).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        l.value, color = SeniorColors.TextPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End
                    )
                    l.note?.let { Text(it, color = SeniorColors.TextSecondary, fontSize = 11.sp, textAlign = TextAlign.End) }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = SeniorColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
}

/** Header line of a block: name, hours, then a tag on the right. */
@Composable
private fun BlockHeader(name: String, hours: String, extra: String? = null, tag: Pair<String, TagKind>?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = SeniorColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            extra?.let { "$hours  ·  $it" } ?: hours,
            color = SeniorColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 8.dp).weight(1f)
        )
        tag?.let { Tag(it.first, it.second) }
    }
}

private fun usualLines(f: Figures, fil: Boolean): List<Line> = buildList {
    f.stillSeconds?.let { add(Line(SeniorRoutineText.labelStill(fil), SeniorRoutineText.duration(it, fil))) }
    f.steps?.let { add(Line(SeniorRoutineText.labelSteps(fil), "%,d".format(it))) }
    f.unlocks?.let { add(Line(SeniorRoutineText.labelUnlocks(fil), SeniorRoutineText.times(it, fil))) }
    f.idleSeconds?.let { add(Line(SeniorRoutineText.labelIdle(fil), SeniorRoutineText.duration(it, fil))) }
}

/**
 * A day's figures, each with a small muted note: the usual figure for a finished block, or
 * "so far" for one still in progress (a part-day is never set against a whole block's usual).
 */
private fun dayLines(day: Figures, usual: Figures, inProgress: Boolean, fil: Boolean): List<Line> {
    fun note(usualText: String?): String? =
        if (inProgress) SeniorRoutineText.soFar(fil) else usualText?.let { SeniorRoutineText.usualSuffix(it, fil) }
    return buildList {
        day.stillSeconds?.let {
            add(Line(SeniorRoutineText.labelStill(fil), SeniorRoutineText.duration(it, fil),
                note(usual.stillSeconds?.let { u -> SeniorRoutineText.duration(u, fil) })))
        }
        day.steps?.let {
            add(Line(SeniorRoutineText.labelSteps(fil), "%,d".format(it), note(usual.steps?.let { u -> "%,d".format(u) })))
        }
        day.unlocks?.let {
            add(Line(SeniorRoutineText.labelUnlocks(fil), SeniorRoutineText.times(it, fil),
                note(usual.unlocks?.let { u -> SeniorRoutineText.times(u, fil) })))
        }
        day.idleSeconds?.let {
            add(Line(SeniorRoutineText.labelIdle(fil), SeniorRoutineText.duration(it, fil),
                note(usual.idleSeconds?.let { u -> SeniorRoutineText.duration(u, fil) })))
        }
    }
}

// ------------------------------------------------------------------ routine tab

@Composable
private fun RoutineTabContent(state: SeniorRoutineState) {
    val fil = state.fil
    if (state.usual.isEmpty()) {
        Note(SeniorRoutineText.loading(fil))
        return
    }
    CardBox(bg = SeniorColors.BlueLightBg, border = SeniorColors.BlueBorder) {
        Text(
            SeniorRoutineText.learning(state.days, fil),
            color = SeniorColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFFDFE6DC))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((state.days / 14f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(SeniorColors.Green)
            )
        }
        Note(SeniorRoutineText.learningDetail(fil))
    }

    CardBox {
        state.usual.forEachIndexed { i, u ->
            if (i > 0) HorizontalDivider(color = SeniorColors.GreenBorder.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                BlockHeader(
                    name = SeniorRoutineText.blockName(u.window.block, fil),
                    hours = SeniorRoutineText.hours(u.window),
                    tag = if (u.learned) "✓ ${SeniorRoutineText.learned(fil)}" to TagKind.OK
                    else "○ ${SeniorRoutineText.setupAnswer(fil)}" to TagKind.SEED
                )
                Text(
                    SeniorRoutineText.usualHeadline(u.window.block, u.times, u.learned, fil),
                    color = SeniorColors.TextPrimary, fontSize = 15.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                LineList(usualLines(u.usual, fil))
            }
        }
    }
    Note(SeniorRoutineText.legend(fil))
}

// ------------------------------------------------------------------ yesterday / today

@Composable
private fun DayBlocks(blocks: List<DayBlock>, fil: Boolean) {
    CardBox {
        blocks.forEachIndexed { i, b ->
            if (i > 0) HorizontalDivider(color = SeniorColors.GreenBorder.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))
            DayBlockRow(b, fil)
        }
    }
}

@Composable
private fun DayBlockRow(b: DayBlock, fil: Boolean) {
    val name = SeniorRoutineText.blockName(b.window.block, fil)
    val inProgress = b.state == DayState.IN_PROGRESS
    val notYet = b.state == DayState.NOT_YET

    val verdictText = SeniorRoutineText.verdict(b.verdict, inProgress, fil)
    val tag: Pair<String, TagKind>? = when {
        notYet -> SeniorRoutineText.notYet(fil) to TagKind.WAIT
        b.verdict == Verdict.AS_USUAL -> verdictText!! to TagKind.OK
        b.verdict == Verdict.QUIETER || b.verdict == Verdict.BUSIER -> verdictText!! to TagKind.SOFT
        else -> null
    }

    val headline: String? = when {
        notYet -> SeniorRoutineText.usualHeadline(b.window.block, b.usualTimes, b.learned, fil)
        b.window.block == "morning" -> b.times.firstMinute?.let { SeniorRoutineText.upAt(it, b.usualTimes.firstMinute, fil) }
        b.window.block == "afternoon" -> b.times.lastMinute?.let { SeniorRoutineText.activeUntil(it, b.usualTimes.lastMinute, fil) }
        b.window.block == "evening" -> b.times.lastMinute?.let { SeniorRoutineText.lastActive(it, b.usualTimes.lastMinute, fil) }
        else -> null
    }

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        BlockHeader(
            name = name,
            hours = SeniorRoutineText.hours(b.window),
            extra = if (inProgress) SeniorRoutineText.inProgress(fil) else null,
            tag = tag
        )
        headline?.let {
            Text(
                it,
                color = if (notYet) SeniorColors.TextSecondary else SeniorColors.TextPrimary,
                fontSize = 15.sp, modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (!notYet && b.figures != null) {
            LineList(dayLines(b.figures, b.usual, inProgress, fil))
        }
    }
}

@Composable
private fun YesterdayTabContent(state: SeniorRoutineState) {
    val fil = state.fil
    val rows = state.yesterday
    val date = state.yesterdayDate
    if (rows == null || date == null) {
        Note(SeniorRoutineText.noYesterday(fil))
        return
    }
    Text(SeniorRoutineText.yesterdayLine(date, fil), color = Color(0xFF4D4D4D), fontSize = 13.sp)
    DayBlocks(rows, fil)
    Note(SeniorRoutineText.yesterdayNote(fil))
}

@Composable
private fun TodayTabContent(state: SeniorRoutineState) {
    val fil = state.fil
    val date = state.todayDate ?: return
    val time = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    Text(SeniorRoutineText.todayLine(date, time, fil), color = Color(0xFF4D4D4D), fontSize = 13.sp)
    DayBlocks(state.today, fil)
    CardBox(bg = SeniorColors.BlueLightBg, border = SeniorColors.BlueBorder) {
        Text(SeniorRoutineText.todayNote(fil), color = Color(0xFF4D4D4D), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

// ------------------------------------------------------------------ changes

@Composable
private fun ChangesTabContent(state: SeniorRoutineState) {
    val fil = state.fil
    Text(
        SeniorRoutineText.changesSummary(state.changes.items.map { it.block }.distinct().size, state.care.size, fil),
        color = Color(0xFF4D4D4D), fontSize = 13.sp
    )

    // 1. What the phone learned: the routine itself moving because the days did.
    CardBox {
        Text(SeniorRoutineText.learnedHeading(fil), color = Color(0xFF4D4D4D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (state.changes.items.isEmpty()) {
            Text(SeniorRoutineText.noRoutineChange(fil), color = SeniorColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        } else {
            val byBlock = state.changes.items.groupBy { it.block }
            SeniorRoutineModel.BLOCKS.filter { it in byBlock }.forEach { block ->
                val items = byBlock.getValue(block)
                Text(
                    SeniorRoutineText.blockName(block, fil),
                    color = SeniorColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                LineList(items.map { c ->
                    Line(
                        SeniorRoutineText.feature(c.feature, fil),
                        "${SeniorRoutineText.value(c.feature, c.from, fil)} → ${SeniorRoutineText.value(c.feature, c.to, fil)}"
                    )
                })
            }
            val steps = state.changes.items.filter { it.feature == SeniorRoutineModel.STEPS }
            val why = when {
                steps.isNotEmpty() && steps.sumOf { it.to - it.from } < 0 -> SeniorRoutineText.learnedWhyQuieter(fil)
                steps.isNotEmpty() -> SeniorRoutineText.learnedWhyBusier(fil)
                else -> SeniorRoutineText.learnedWhyOther(fil)
            }
            Text(why, color = Color(0xFF4D4D4D), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    // 2. How careful alerts are: false alarms loosening the trigger. Not a change to the routine.
    CardBox(bg = AmberBg, border = AmberBorder) {
        Text(SeniorRoutineText.careHeading(fil), color = Color(0xFF4D4D4D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (state.care.isEmpty()) {
            Text(SeniorRoutineText.noCare(fil), color = SeniorColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        } else {
            Text(SeniorRoutineText.careIntro(fil), color = Color(0xFF3F3F3F), fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
            val today = state.todayDate
            state.care.forEach { c -> if (today != null) CareBlock(c, today, fil) }
            Text(SeniorRoutineText.careNote(fil), color = SeniorColors.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }

    if (state.changes.includesEstimates) Note(SeniorRoutineText.estimateNote(fil))
}

// ------------------------------------------------------------------ care card

private val CareAmber = Color(0xFFB87408)
private val CareTrack = Color(0xFFEFE4C6)
private val CareTick = Color(0xFFE3D8BB)
private val CareToday = Color(0xFF4D4D4D)

/**
 * One part of the day where false alarms have loosened the trigger: its name, the minutes added
 * (for the stillness signals), and a line from the first answer to the day it goes back to normal.
 */
@Composable
private fun CareBlock(c: SeniorRoutineModel.Care, today: java.time.LocalDate, fil: Boolean) {
    val strip = remember(c, today) { SeniorRoutineModel.strip(c, today) }
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, AmberBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                SeniorRoutineText.blockName(c.block, fil),
                color = SeniorColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Tag(SeniorRoutineText.careChip(c.extraMinutes, fil), TagKind.SOFT)
        }
        CareLine(strip)
        // Under the line: the first day it covers, and today on the right.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(SeniorRoutineText.shortDate(today.minusDays(SeniorRoutineModel.STRIP_CELLS - 1L), fil), fontSize = 11.sp, color = SeniorColors.TextSecondary)
            Text(SeniorRoutineText.careToday(fil), fontSize = 11.sp, color = SeniorColors.TextSecondary)
        }
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(CareAmber, ring = false, text = SeniorRoutineText.careFine(fil))
            LegendDot(CareToday, ring = true, text = SeniorRoutineText.careToday(fil))
        }
    }
}

@Composable
private fun LegendDot(color: Color, ring: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (ring) Color.White else color)
                .then(if (ring) Modifier.border(2.dp, color, androidx.compose.foundation.shape.CircleShape) else Modifier)
        )
        Text(text, fontSize = 11.sp, color = SeniorColors.TextSecondary, modifier = Modifier.padding(start = 4.dp))
    }
}

/** The line itself: the last 14 days, a tick per day, amber dots on the days the senior said they were fine and a ring for today at the right edge. */
@Composable
private fun CareLine(strip: SeniorRoutineModel.Strip) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 8.dp)
    ) {
        val w = size.width
        val cy = size.height / 2
        drawLine(CareTrack, androidx.compose.ui.geometry.Offset(0f, cy), androidx.compose.ui.geometry.Offset(w, cy), strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        for (i in 0..strip.days) {
            drawCircle(CareTick, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(w * i / strip.days, cy))
        }
        // Today, at the right edge: a dark ring.
        val todayAt = androidx.compose.ui.geometry.Offset(w, cy)
        drawCircle(Color.White, 7.dp.toPx(), todayAt)
        drawCircle(CareToday, 7.dp.toPx(), todayAt, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        // Answers: amber dots, drawn last so one on the same day as today stays visible.
        strip.answered.forEach { f ->
            val at = androidx.compose.ui.geometry.Offset(w * f, cy)
            drawCircle(Color.White, 8.dp.toPx(), at)
            drawCircle(CareAmber, 6.5.dp.toPx(), at)
        }
    }
}
