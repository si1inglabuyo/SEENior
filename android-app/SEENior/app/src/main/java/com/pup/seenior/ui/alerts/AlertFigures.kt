package com.pup.seenior.ui.alerts

import com.pup.seenior.database.entities.Alert
import com.pup.seenior.detection.FallContext
import com.pup.seenior.detection.FallDetector
import com.pup.seenior.detection.FallEvidence
import com.pup.seenior.detection.FallTolerance
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
        Kind.IDLE -> if (fil) "Tagal na hindi nagamit ang phone" else "Time the phone was not used"
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

    // ---------------------------------------------------------------- a fall

    /** One of the four checks a fall has to pass, with what the phone measured and what was needed. */
    data class FallRow(val name: String, val line: String)

    /**
     * What the "What happened" card says about a fall. A fall has no usual to compare with: it is
     * judged by the detector's own four checks, so the card lists those with the figure and the
     * limit each had to clear. [adjusted] explains raised limits, [context] is the record of what
     * the phone was doing just before the drop. Display only; the detector never reads either.
     */
    data class FallView(val sentence: String, val rows: List<FallRow>, val adjusted: String?, val context: String?)

    private fun secs(ms: Long): String = String.format(Locale.US, "%.1f", ms / 1000.0).removeSuffix(".0")
    private fun one(v: Float): String = String.format(Locale.US, "%.1f", v)

    /** Null for any other trigger and for falls stored before the evidence was recorded. */
    fun fall(alert: Alert, fil: Boolean): FallView? {
        if (alert.triggerType != "fall_pattern") return null
        val evidence = FallEvidence.decode(alert.fallEvidence) ?: return null
        val note = FallTolerance.decodeNote(alert.fallTolerance)
        val config = note?.config ?: FallDetector.Config()
        val turned = evidence.rotationPeak != null

        val rows = buildList {
            add(FallRow(
                if (fil) "Malayang pagbagsak" else "Free fall",
                if (fil) "${secs(evidence.freeFallMs)} s na bumagsak, kailangan ${secs(config.freeFallMinMs)} hanggang ${secs(config.freeFallMaxMs)} s"
                else "${secs(evidence.freeFallMs)} s in free fall, needed ${secs(config.freeFallMinMs)} to ${secs(config.freeFallMaxMs)} s"
            ))
            add(FallRow(
                if (fil) "Tama sa lupa" else "Impact",
                if (fil) "${one(evidence.impactPeak)} na lakas, kailangan ${one(config.impactMin)} pataas"
                else "${one(evidence.impactPeak)} strength, needed ${one(config.impactMin)} or more"
            ))
            if (turned) add(FallRow(
                if (fil) "Pag-ikot ng katawan" else "Rotation",
                if (fil) "${one(evidence.rotationPeak!!)} na bilis ng pag-ikot, kailangan ${one(config.rotationMin)} pataas"
                else "${one(evidence.rotationPeak!!)} turning speed, needed ${one(config.rotationMin)} or more"
            ))
            add(FallRow(
                if (fil) "Hindi paggalaw" else "Stillness",
                if (fil) "${secs(evidence.stillnessMs)} s na hindi gumalaw, kailangan ${secs(config.stillnessMs)} s pataas"
                else "${secs(evidence.stillnessMs)} s without moving, needed ${secs(config.stillnessMs)} s or more"
            ))
        }

        val still = secs(evidence.stillnessMs)
        val sentence = if (fil) {
            val felt = if (turned) "ang biglang pagbagsak, malakas na tama at mabilis na pag-ikot" else "ang biglang pagbagsak at malakas na tama"
            "Naramdaman ng phone $felt, tapos $still segundong hindi gumalaw. " + if (turned)
                "Natugunan ang apat na pagsusuri, kaya nagtanong ito kung ligtas kayo."
            else
                "Walang sensor ng pag-ikot ang phone ninyo, kaya hindi ito sinuri. Natugunan ang tatlo pa, kaya nagtanong ito kung ligtas kayo."
        } else {
            val felt = if (turned) "a sudden drop, a hard hit and a quick turn" else "a sudden drop and a hard hit"
            "Your phone felt $felt, and then it stayed still for $still seconds. " + if (turned)
                "All four checks were met, so it asked if you are safe."
            else
                "Your phone has no turn sensor, so that check was skipped. The other three were met, so it asked if you are safe."
        }

        val adjusted = note?.takeIf { it.raised }?.let { n ->
            val back = if (n.realAlerts > 0) (if (fil) " Binawi ng ${n.realAlerts} totoong pagbagsak ang bahagi nito." else " ${n.realAlerts} real fall(s) took part of this back.") else ""
            if (fil)
                "Bakit mas mataas ang mga pagsusuri: ${n.falseAlarms} naunang pagbagsak sa nakaraang ${FallTolerance.WINDOW_DAYS} araw ang lumabas na maling alarma, kaya mas malakas na tama (${one(n.config.impactMin)} sa halip na ${one(n.baseConfig.impactMin)}) at mas matagal na pagtigil (${secs(n.config.stillnessMs)} s sa halip na ${secs(n.baseConfig.stillnessMs)} s) na ang kailangan ng phone. May ${FallTolerance.EXTENDED_WINDOW_SECONDS} segundo rin kayong sumagot sa halip na ${FallTolerance.BASE_WINDOW_SECONDS}.$back"
            else
                "Why the checks were higher: ${n.falseAlarms} earlier drops in the last ${FallTolerance.WINDOW_DAYS} days turned out to be false alarms, so your phone now needs a harder hit (${one(n.config.impactMin)} instead of ${one(n.baseConfig.impactMin)}) and a longer stillness (${secs(n.config.stillnessMs)} s instead of ${secs(n.baseConfig.stillnessMs)} s). You also get ${FallTolerance.EXTENDED_WINDOW_SECONDS} seconds to answer instead of ${FallTolerance.BASE_WINDOW_SECONDS}.$back"
        }

        val context = FallContext.decode(alert.fallContext)?.preFallMotion?.let { dev ->
            when (FallContext.levelOf(dev)) {
                FallContext.Level.MOVING ->
                    if (fil) "Bago ang pagbagsak, gumagalaw ang phone (malamang kumikilos kayo)." else "Just before the drop, the phone was moving (you were likely up and about)."
                FallContext.Level.GENTLE ->
                    if (fil) "Bago ang pagbagsak, bahagyang gumagalaw ang phone." else "Just before the drop, the phone was barely moving."
                FallContext.Level.STILL ->
                    if (fil) "Bago ang pagbagsak, hindi gumagalaw ang phone (malamang nagpapahinga kayo)." else "Just before the drop, the phone was still (you were likely resting)."
            }
        }
        return FallView(sentence, rows, adjusted, context)
    }

    /** The pop-up for a fall: the detector's own lines, plus what moved the limits when something did. */
    fun fallWorking(alert: Alert, fil: Boolean, evidenceLines: String?): String? {
        val note = FallTolerance.decodeNote(alert.fallTolerance)
        val extra = note?.line(fil)
        return when {
            evidenceLines != null && extra != null -> evidenceLines + "\n\n" + extra
            else -> evidenceLines
        }
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
