package com.pup.seenior.ui.wellness

import com.pup.seenior.database.entities.Alert
import com.pup.seenior.detection.FalseAlarmTolerance
import com.pup.seenior.detection.MedianMad
import com.pup.seenior.ui.alerts.AlertFigures
import com.pup.seenior.ui.routine.SeniorRoutineText
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * The "Why are we asking?" card on the wellness prompt. Closed until tapped; one short card per
 * kind of check-in:
 *
 *  - [Kind.STILLNESS]: a routine anomaly. Three lines the senior can check against the clock: what is
 *    usual, when the phone last sensed movement (or last saw the phone used), and when it asked.
 *    The line under "asked" says where that moment comes from: the usual plus an extra that follows
 *    how much the senior's own days vary.
 *  - [Kind.FALL]: the four checks the detector made (free fall, impact, rotation, stillness) with
 *    the figures, the same ones the Alert details screen shows.
 *  - [Kind.DAILY]: the signals that leaned the worrying way on the day the nightly pattern flagged.
 *
 * Built from what the alert stored when it fired (`reading_value`, `usual_value`, `usual_spread`,
 * `fall_evidence`, `triggered_at`) plus, for the daily pattern, the day's figures the view model
 * loads. Movement alerts and alerts without those figures keep the single reason sentence. Display
 * only: nothing here feeds detection.
 *
 * "Last moved" is the alert's own time minus the stillness it measured, so it can be off by up to
 * the 5 minute sampling step. The asking point is drawn at the base gate (2.5); if the senior's own
 * false alarms loosened it for this signal, the phone asked a little later than that sum suggests.
 */
internal object WellnessWhy {

    enum class Kind { STILLNESS, FALL, DAILY }

    /**
     * One line of the card. [value] is empty for a fall check (it is drawn with a tick instead),
     * and [small] is the grey line under the label.
     */
    data class Row(
        val label: String,
        val small: String?,
        val value: String,
        val highlight: Boolean = false
    )

    data class Why(
        val kind: Kind,
        val rowTitle: String,
        val rows: List<Row>,
        /** A closing sentence under the rows (the daily pattern's explanation), or null. */
        val footnote: String? = null
    )

    /** How many minutes past the usual the base gate sits: 2.5 / 0.6745 spreads. */
    private val GATE_SPREADS = FalseAlarmTolerance.BASE_THRESHOLD / MedianMad.MODIFIED_Z_SCALE

    fun rowTitle(fil: Boolean) = if (fil) "Bakit po kami nagtatanong?" else "Why are we asking?"

    /**
     * Null for every alert that should keep the plain sentence.
     * [dailyItems] are the flagged day's worrying signals, loaded by the view model for a daily-pattern alert.
     */
    fun of(
        alert: Alert,
        fil: Boolean,
        dailyItems: List<AlertFigures.Item> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Why? = when (alert.triggerType) {
        "fall_pattern" -> fall(alert, fil)
        "ml_flag" -> daily(alert, fil, dailyItems)
        "inactivity", "screen_idle" -> stillness(alert, fil, zone)
        else -> null
    }

    // ---------------------------------------------------------------- a routine anomaly

    private fun stillness(alert: Alert, fil: Boolean, zone: ZoneId): Why? {
        val c = AlertFigures.of(alert) ?: return null
        if (c.kind == AlertFigures.Kind.MOVEMENT) return null
        val usualMin = AlertFigures.minutes(c.usual)
        val readingMin = AlertFigures.minutes(c.reading)
        if (readingMin <= usualMin) return null
        val askMin = maxOf(usualMin + 1, ((c.usual + GATE_SPREADS * c.spread) / 60.0).roundToLong())
        val extraMin = askMin - usualMin
        val idle = c.kind == AlertFigures.Kind.IDLE

        val askedAt = clock(alert.triggeredAt, zone)
        val lastAt = clock(alert.triggeredAt - (c.reading * 1000).toLong(), zone)

        return Why(
            kind = Kind.STILLNESS,
            rowTitle = rowTitle(fil),
            rows = listOf(
                Row(
                    label = if (fil) "Karaniwan ninyo" else "Your usual",
                    small = inThe(alert.timeBlock, fil),
                    value = (if (fil) "hanggang " else "up to ") + AlertFigures.short(usualMin, fil)
                ),
                Row(
                    label = if (idle) (if (fil) "Huling ginamit ang phone" else "Last used phone")
                    else (if (fil) "Huling gumalaw" else "Last moved"),
                    small = if (idle) (if (fil) "huling beses na ginamit ang phone" else "the last time your phone was used")
                    else (if (fil) "huling naramdaman ng phone ang galaw" else "the last time your phone sensed movement"),
                    value = lastAt
                ),
                Row(
                    label = if (fil) "Nagtanong ang phone" else "Phone asked",
                    small = if (fil)
                        "Karaniwan na ${AlertFigures.short(usualMin, true)} + ${AlertFigures.short(extraMin, true)} na dagdag"
                    else
                        "Your usual ${AlertFigures.short(usualMin, false)} + ${AlertFigures.short(extraMin, false)} extra",
                    value = askedAt,
                    highlight = true
                )
            )
        )
    }

    // ---------------------------------------------------------------- a fall

    private fun fall(alert: Alert, fil: Boolean): Why? {
        val view = AlertFigures.fall(alert, fil) ?: return null
        return Why(
            kind = Kind.FALL,
            rowTitle = rowTitle(fil),
            rows = view.rows.map { Row(label = it.name, small = it.line, value = "") }
        )
    }

    // ---------------------------------------------------------------- the daily pattern

    private fun daily(alert: Alert, fil: Boolean, items: List<AlertFigures.Item>): Why? {
        if (items.isEmpty()) return null
        return Why(
            kind = Kind.DAILY,
            rowTitle = rowTitle(fil),
            rows = items.map {
                Row(
                    label = AlertFigures.itemName(it.feature, fil),
                    small = SeniorRoutineText.usualSuffix(SeniorRoutineText.value(it.feature, it.usual, fil), fil),
                    value = SeniorRoutineText.value(it.feature, it.day, fil),
                    highlight = true
                )
            },
            footnote = AlertFigures.dailySentence(alert.timeBlock, fil) + " " + AlertFigures.dailyFooter(fil)
        )
    }

    // ---------------------------------------------------------------- small helpers

    private fun clock(epochMillis: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return SeniorRoutineText.clock(t.hour * 60 + t.minute)
    }

    private fun inThe(block: String, fil: Boolean) = if (fil) when (block) {
        "morning" -> "sa umaga"
        "afternoon" -> "sa hapon"
        "evening" -> "sa gabi"
        else -> "sa madaling-araw"
    } else when (block) {
        "morning" -> "in the morning"
        "afternoon" -> "in the afternoon"
        "evening" -> "in the evening"
        else -> "at night"
    }
}
