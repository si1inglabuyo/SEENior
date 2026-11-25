package com.pup.seenior.ui.wellness

import com.pup.seenior.database.entities.Alert
import com.pup.seenior.detection.FalseAlarmTolerance
import com.pup.seenior.detection.MedianMad
import com.pup.seenior.ui.alerts.AlertFigures
import kotlin.math.roundToLong

/**
 * The "Why are we asking?" card on the wellness prompt: what is usual for the senior, what the
 * phone saw, the buffer their own days earn, and the point where the phone asks.
 *
 * Built from the figures the alert stored when it fired (`reading_value`, `usual_value`,
 * `usual_spread`), the same ones the Alert details screen reads. Only the two stillness signals
 * (no movement, phone untouched) get a card: they are "longer than usual", which a buffer above
 * the usual explains. Movement is "less than usual", falls and the daily pattern have no usual
 * time, so they keep the single reason sentence. Display only: nothing here feeds detection.
 *
 * The asking point is drawn at the base gate (2.5). If the senior's own false alarms loosened the
 * gate for this signal (`FalseAlarmTolerance`), the phone asked a little later than shown.
 */
internal object WellnessWhy {

    data class Why(
        val usualMinutes: Long,
        val bufferMinutes: Long,
        val askMinutes: Long,
        val usualLabel: String,
        val usualValue: String,
        val readingLabel: String,
        val readingValue: String,
        val gapAmount: String,
        val gapLine: String,
        val bufferName: String,
        val askLabel: String,
        val usualName: String,
        val usualUpTo: String,
        val bufferValue: String,
        val askValue: String,
        val reasonLine: String,
        val sourceTitle: String,
        val sourceLine: String,
        val rowTitle: String
    )

    /** How many minutes past the usual the base gate sits: 2.5 / 0.6745 spreads. */
    private val GATE_SPREADS = FalseAlarmTolerance.BASE_THRESHOLD / MedianMad.MODIFIED_Z_SCALE

    /** Null for every alert that should keep the plain sentence. */
    fun of(alert: Alert, fil: Boolean): Why? {
        val c = AlertFigures.of(alert) ?: return null
        if (c.kind == AlertFigures.Kind.MOVEMENT) return null
        val usualMin = AlertFigures.minutes(c.usual)
        val readingMin = AlertFigures.minutes(c.reading)
        if (readingMin <= usualMin) return null
        val askMin = maxOf(
            usualMin + 1,
            ((c.usual + GATE_SPREADS * c.spread) / 60.0).roundToLong()
        )
        val bufferMin = askMin - usualMin
        val block = alert.timeBlock
        val gap = AlertFigures.long(readingMin - usualMin, fil)

        return Why(
            usualMinutes = usualMin,
            bufferMinutes = bufferMin,
            askMinutes = askMin,
            usualLabel = AlertFigures.usualLabel(c.kind, fil),
            usualValue = AlertFigures.short(usualMin, fil),
            readingLabel = thisBlock(block, fil),
            readingValue = AlertFigures.short(readingMin, fil),
            gapAmount = gap,
            gapLine = if (fil) "na mas matagal kaysa karaniwan." else "longer than usual.",
            bufferName = if (fil) "Palugit" else "Buffer",
            askLabel = if (fil) "Magtatanong pagkalampas ng" else "Phone asks after",
            usualName = if (fil) "Karaniwan" else "Your usual",
            usualUpTo = (if (fil) "hanggang " else "up to ") + AlertFigures.short(usualMin, fil),
            bufferValue = "+ " + AlertFigures.short(bufferMin, fil),
            askValue = AlertFigures.short(askMin, fil),
            reasonLine = if (fil)
                "Mas matagal po ito kaysa sa normal ninyong ${blockWord(block, true)}, kaya kinukumusta namin kayo."
            else
                "That is longer than a normal ${blockWord(block, false)} for you, so we are checking on you.",
            sourceTitle = if (fil) "Saan galing ang palugit?" else "Where does the buffer come from?",
            sourceLine = if (fil)
                "Sa pagkakaiba ng sarili ninyong mga ${blockWord(block, true)} sa nakaraang 14 na araw."
            else
                "From how much your own ${blockPlural(block)} differed over the last 14 days.",
            rowTitle = if (fil) "Bakit po kami nagtatanong?" else "Why are we asking?"
        )
    }

    private fun thisBlock(block: String, fil: Boolean) = if (fil) when (block) {
        "morning" -> "Ngayong umaga"
        "afternoon" -> "Ngayong hapon"
        "evening" -> "Ngayong gabi"
        else -> "Ngayong madaling-araw"
    } else when (block) {
        "morning" -> "This morning"
        "afternoon" -> "This afternoon"
        "evening" -> "This evening"
        else -> "Tonight"
    }

    private fun blockWord(block: String, fil: Boolean) = if (fil) when (block) {
        "morning" -> "umaga"
        "afternoon" -> "hapon"
        "evening" -> "gabi"
        else -> "madaling-araw"
    } else when (block) {
        "morning" -> "morning"
        "afternoon" -> "afternoon"
        "evening" -> "evening"
        else -> "night"
    }

    private fun blockPlural(block: String) = blockWord(block, false) + "s"
}
