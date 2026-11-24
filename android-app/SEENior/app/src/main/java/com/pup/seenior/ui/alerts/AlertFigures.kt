package com.pup.seenior.ui.alerts

import com.pup.seenior.database.entities.Alert
import com.pup.seenior.detection.IsolationForestDetector
import com.pup.seenior.detection.MedianMad
import com.pup.seenior.ui.routine.SeniorRoutineModel
import com.pup.seenior.ui.routine.SeniorRoutineText
import java.util.Locale
import kotlin.math.abs

/**
 * The words and sums behind the "What happened" card of the alert details: what the phone
 * measured, what is usual for the senior in that part of the day, and how far past it they went.
 *
 * Pure functions over the figures the alert stored (`reading_value`, `usual_value`,
 * `usual_spread`, see [Alert]). Display only: nothing here feeds detection. Alerts made before
 * those columns existed carry no figures, and [of] returns null for them so the screen shows the
 * timeline alone instead of inventing a comparison from a routine that has moved since.
 */
internal object AlertFigures {

    enum class Kind { STILL, IDLE, MOVEMENT }

    /** [spread] is the effective MAD the score was divided by: how much ordinary days vary. */
    data class Comparison(val kind: Kind, val usual: Double, val reading: Double, val spread: Double)

    fun of(alert: Alert): Comparison? {
        val reading = alert.readingValue ?: return null
        val usual = alert.usualValue ?: return null
        val spread = alert.usualSpread ?: return null
        val kind = when (alert.triggerType) {
            "inactivity" -> Kind.STILL
            "screen_idle" -> Kind.IDLE
            "movement" -> Kind.MOVEMENT
            else -> return null
        }
        return Comparison(kind, usual, reading, spread)
    }

    // ---------------------------------------------------------------- units

    /** Whole minutes, never 0: "0 minutes" would read as no difference. */
    fun minutes(seconds: Double): Long = Math.round(seconds / 60.0).coerceAtLeast(1)

    /** Movement is a 0-1 score; people read it as a percentage. */
    fun percent(score: Double): Long = Math.round(score * 100.0)

    /** "1 hour and 20 minutes", "45 minutes", "2 hours" (Filipino: "1 oras at 20 minuto"). */
    fun long(totalMinutes: Long, fil: Boolean): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (fil) when {
            h == 0L -> "$m minuto"
            m == 0L -> "$h oras"
            else -> "$h oras at $m minuto"
        } else when {
            h == 0L -> "$m ${plural(m, "minute")}"
            m == 0L -> "$h ${plural(h, "hour")}"
            else -> "$h ${plural(h, "hour")} and $m ${plural(m, "minute")}"
        }
    }

    private fun plural(n: Long, word: String) = if (n == 1L) word else "${word}s"

    /** Short form for the boxes: "1 hr 20 min" / "45 min". */
    fun short(totalMinutes: Long, fil: Boolean): String = SeniorRoutineText.duration(totalMinutes * 60, fil)

    private fun amount(c: Comparison, value: Double, fil: Boolean, longForm: Boolean): String = when (c.kind) {
        Kind.MOVEMENT -> "${percent(value)}%"
        else -> minutes(value).let { if (longForm) long(it, fil) else short(it, fil) }
    }

    /** How far past the usual, in the same units: 25 minutes, or 34 points of movement. */
    private fun gap(c: Comparison, fil: Boolean, longForm: Boolean): String = when (c.kind) {
        Kind.MOVEMENT -> "${abs(percent(c.usual) - percent(c.reading))}%"
        else -> abs(minutes(c.reading) - minutes(c.usual)).coerceAtLeast(1)
            .let { if (longForm) long(it, fil) else short(it, fil) }
    }

    // ---------------------------------------------------------------- the card

    fun header(kind: Kind, fil: Boolean): String = when (kind) {
        Kind.STILL -> if (fil) "Tagal na walang galaw" else "Time without movement"
        Kind.IDLE -> if (fil) "Tagal na hindi nagamit ang telepono" else "Time the phone was not used"
        Kind.MOVEMENT -> if (fil) "Dami ng galaw" else "Movement level"
    }

    fun usualLabel(kind: Kind, fil: Boolean): String = when (kind) {
        Kind.MOVEMENT -> if (fil) "Karaniwan ninyo" else "Your usual"
        else -> if (fil) "Karaniwan, hanggang" else "Your usual, up to"
    }

    fun thatLabel(block: String, fil: Boolean): String {
        val name = SeniorRoutineText.blockName(block, fil).lowercase()
        return if (fil) "Noong $name" else "That $name"
    }

    fun usualValue(c: Comparison, fil: Boolean) = amount(c, c.usual, fil, longForm = false)
    fun readingValue(c: Comparison, fil: Boolean) = amount(c, c.reading, fil, longForm = false)

    fun bufferLabel(c: Comparison, fil: Boolean): String =
        "± ${amount(c, c.spread, fil, longForm = false)} buffer"

    fun pastLabel(c: Comparison, fil: Boolean): String {
        val g = gap(c, fil, longForm = false)
        return when {
            c.kind == Kind.MOVEMENT && fil -> "$g na mas mababa"
            c.kind == Kind.MOVEMENT -> "$g below usual"
            fil -> "$g lampas sa karaniwan"
            else -> "$g past usual"
        }
    }

    /** The plain-language reason, in the senior's language. */
    fun sentence(c: Comparison, fil: Boolean): String {
        val usual = amount(c, c.usual, fil, longForm = true)
        val reading = amount(c, c.reading, fil, longForm = true)
        val g = gap(c, fil, longForm = true)
        val buffer = amount(c, c.spread, fil, longForm = true)
        return when (c.kind) {
            Kind.STILL -> if (fil)
                "Karaniwang hindi ka gumagalaw nang hanggang $usual. Umabot ito nang $reading ngayon ($g na lampas sa karaniwan, higit sa normal mong $buffer na buffer), kaya nag-check in ang phone mo."
            else
                "You typically stay still for up to $usual. This time you were inactive for $reading, which is $g past your usual and beyond your normal $buffer buffer, so your phone prompted a check-in."
            Kind.IDLE -> if (fil)
                "Madalas, hanggang $usual lang hindi nagagamit ang phone mo. Ngayon, umabot ito nang $reading, $g na lampas sa karaniwan at higit sa normal mong $buffer na buffer, kaya nag-check in ang phone mo."
            else
                "Your phone typically stays idle for up to $usual. This time it was left for $reading, which is $g past your usual and beyond your normal $buffer buffer, so your phone prompted a check-in."
            Kind.MOVEMENT -> if (fil)
                "Karaniwang may $usual na galaw ang phone mo sa oras na ito. Ngayon, $reading lang ito ($g na mas mababa sa karaniwan, higit sa normal mong $buffer na buffer), kaya nag-check in ang phone mo."
            else
                "Your phone usually senses a movement level of about $usual at this time. This time it was $reading, which is $g below your usual and beyond your normal $buffer buffer, so your phone prompted a check-in."
        }
    }

    // ---------------------------------------------------------------- the "?" pop-up

    fun popupTitle(fil: Boolean) = if (fil) "Paano ito kinuwenta ng phone" else "How the phone worked this out"
    fun close(fil: Boolean) = if (fil) "Isara" else "Close"
    fun helpLabel(fil: Boolean) = if (fil) "Paano ito kinuwenta" else "How this was worked out"

    /** The sum behind a Median-MAD alert, one line per figure. */
    fun working(c: Comparison, block: String, score: Double?, fil: Boolean): String {
        val name = SeniorRoutineText.blockName(block, fil).lowercase()
        val lines = mutableListOf(
            (if (fil) "karaniwan ($name) = " else "usual ($name) = ") + amount(c, c.usual, fil, false),
            "buffer = " + amount(c, c.spread, fil, false),
            (if (fil) "nabasa = " else "reading = ") + amount(c, c.reading, fil, false)
        )
        if (score != null) {
            lines += "${if (fil) "iskor" else "score"} = ${MedianMad.MODIFIED_Z_SCALE} × |${raw(c.reading, c)} − ${raw(c.usual, c)}| ÷ ${raw(c.spread, c)} = ${fmt(score)}"
            lines += if (fil) "karaniwang nagtatanong mula sa iskor 2.5" else "normally asks from score 2.5"
        }
        return lines.joinToString("\n")
    }

    private fun raw(value: Double, c: Comparison): String =
        if (c.kind == Kind.MOVEMENT) fmt(value) else minutes(value).toString()

    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    fun dailyScoreWorking(score: Double, fil: Boolean): String {
        val threshold = fmt(IsolationForestDetector.THRESHOLD)
        return if (fil) "iskor ng araw = ${fmt(score)}\nnagfa-flag mula sa $threshold"
        else "daily pattern score = ${fmt(score)}\nflags from $threshold"
    }

    // ---------------------------------------------------------------- the daily pattern

    /** One signal that leaned the worrying way for that part of the day. */
    data class Item(val feature: String, val day: Double, val usual: Double, val lower: Boolean)

    /**
     * The signals of a scored block that sat on the worrying side of the usual by a clear margin.
     * Only the worrying side counts (the forest keeps only that side too): fewer steps and
     * unlocks, longer stillness and phone-untouched spells. Display only.
     */
    fun dailyItems(usual: SeniorRoutineModel.Figures, day: SeniorRoutineModel.Figures): List<Item> = buildList {
        fun low(feature: String, d: Int?, u: Int?, minUsual: Int) {
            if (d != null && u != null && u >= minUsual && d < u * ITEM_MARGIN_LOW) add(Item(feature, d.toDouble(), u.toDouble(), true))
        }
        fun high(feature: String, d: Long?, u: Long?) {
            if (d != null && u != null && u >= MIN_LONG_USUAL_SECONDS && d > u * ITEM_MARGIN_HIGH) add(Item(feature, d.toDouble(), u.toDouble(), false))
        }
        low(SeniorRoutineModel.STEPS, day.steps, usual.steps, 100)
        low(SeniorRoutineModel.UNLOCKS, day.unlocks, usual.unlocks, 3)
        high(SeniorRoutineModel.STILL, day.stillSeconds, usual.stillSeconds)
        high(SeniorRoutineModel.IDLE, day.idleSeconds, usual.idleSeconds)
    }

    private const val ITEM_MARGIN_LOW = 0.8
    private const val ITEM_MARGIN_HIGH = 1.25
    private const val MIN_LONG_USUAL_SECONDS = 600L

    fun itemName(feature: String, fil: Boolean): String = when (feature) {
        SeniorRoutineModel.STEPS -> if (fil) "Mga hakbang" else "Steps"
        SeniorRoutineModel.UNLOCKS -> if (fil) "Pagbukas ng phone" else "Phone checks"
        SeniorRoutineModel.STILL -> if (fil) "Pinakamatagal na hindi gumalaw" else "Longest still spell"
        else -> if (fil) "Pinakamatagal na hindi nagamit ang phone" else "Longest time phone untouched"
    }

    fun itemLine(item: Item, fil: Boolean): String {
        val day = SeniorRoutineText.value(item.feature, item.day, fil)
        val usual = SeniorRoutineText.value(item.feature, item.usual, fil)
        return when {
            item.feature == SeniorRoutineModel.STILL || item.feature == SeniorRoutineModel.IDLE ->
                if (fil) "$day noong araw na iyon, karaniwang hanggang $usual" else "$day that day, usually up to $usual"
            else -> if (fil) "$day noong araw na iyon, karaniwang $usual" else "$day that day, usually $usual"
        }
    }

    fun itemChip(item: Item, fil: Boolean): String =
        if (item.lower) (if (fil) "Mas kaunti" else "Lower") else (if (fil) "Mas matagal" else "Longer")

    fun dailySentence(block: String, fil: Boolean): String {
        val name = SeniorRoutineText.blockName(block, fil).lowercase()
        return if (fil)
            "Walang isang bagay na malayo sa karaniwan, pero ang buong $name ay mas tahimik kaysa sa mga karaniwan mong araw, kaya nag-check in ang phone mo."
        else
            "No single thing was far off, but your whole $name was quieter than your usual days, so your phone prompted a check-in."
    }

    fun dailyFooter(fil: Boolean) =
        if (fil) "Hindi ito nagti-trigger kapag mas aktibo ka kaysa karaniwan."
        else "A busier day than usual never raises this."
}
