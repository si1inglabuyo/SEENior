package com.pup.seenior.ui.family

import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.detection.FalseAlarmTolerance
import com.pup.seenior.detection.MedianMad
import com.pup.seenior.network.dto.RoutineRowDto
import kotlin.math.roundToInt

/**
 * The working behind each line of the Routine card, built only from the figures the phone
 * already syncs (a median, a MAD and a day count per feature per time block). It adds no data:
 * the per-day readings stay on the phone, so this explains how a sentence like "Still for up to
 * 34 min" was reached without being able to show the days themselves.
 *
 * `barangay-dashboard/src/routine.js` mirrors [alertAfterSeconds] and the figures here; change
 * both together.
 */
object RoutineProof {

    data class Block(
        val block: String,
        /** This block's activity as a percentage of the senior's busiest block (the bar). */
        val activityPercent: Int,
        val movement: Double?,
        val steps: Double?,
        /** Middle value, over the days, of the longest stretch still in this block (seconds). */
        val stillMedian: Double?,
        /** How much an ordinary day varies around [stillMedian] (seconds). */
        val stillMad: Double?,
        /** The stillness at which the system would first ask the senior if they are safe (seconds). */
        val alertAfter: Double?,
        val unlocks: Double?,
        val phone: String?,
        /** Days of this block's readings behind the figures. */
        val days: Int,
        /** True while any of this block's figures is still the questionnaire's guess. */
        val hasSeed: Boolean
    )

    /**
     * Stillness at which Layer 1 reaches its 2.5 gate: `0.6745 * (x - median) / MAD = 2.5`, so
     * `x = median + (2.5 / 0.6745) * MAD`. The MAD is floored the way the detector floors it. This
     * is the starting gate; the time of day, phone use and any false-alarm tolerance are applied
     * after it, so it is the earliest the system would ask, not a promise it will.
     */
    fun alertAfterSeconds(median: Double, mad: Double): Double {
        val floor = SeedBaselineGenerator.MIN_MAD_FLOOR.getValue("inactivity_duration")
        return median + (FalseAlarmTolerance.BASE_THRESHOLD / MedianMad.MODIFIED_Z_SCALE) * maxOf(mad, floor)
    }

    /** `2.5 / 0.6745`, shown in the worked sum. */
    val MADS_TO_GATE: Double = FalseAlarmTolerance.BASE_THRESHOLD / MedianMad.MODIFIED_Z_SCALE

    fun build(rows: List<RoutineRowDto>): List<Block> {
        val summary = RoutineSummary.summarize(rows)
        fun row(feature: String, block: String) =
            rows.firstOrNull { it.featureName == feature && it.timeBlock == block }

        return summary.blocks.map { b ->
            val still = row("inactivity_duration", b.block)
            val unlocks = row("screen_unlock_count", b.block)?.medianValue
            val inBlock = rows.filter { it.timeBlock == b.block }
            Block(
                block = b.block,
                activityPercent = (b.activity * 100).roundToInt(),
                movement = row("movement_score", b.block)?.medianValue,
                steps = row("step_count", b.block)?.medianValue,
                stillMedian = still?.medianValue,
                stillMad = still?.madValue,
                alertAfter = still?.let { alertAfterSeconds(it.medianValue, it.madValue) },
                unlocks = unlocks,
                phone = b.phone,
                days = inBlock.maxOfOrNull { it.sampleCount } ?: 0,
                hasSeed = inBlock.any { it.isSeed }
            )
        }
    }

    // ---- wording ----

    fun activityLine(b: Block, fil: Boolean): String? {
        if (b.movement == null && b.steps == null) return null
        val parts = listOfNotNull(
            b.movement?.let { (if (fil) "galaw " else "movement ") + "%.2f".format(it) },
            b.steps?.let { (if (fil) "mga hakbang " else "steps ") + it.roundToInt() }
        ).joinToString(if (fil) " at " else " and ")
        return if (fil) "Aktibidad: $parts. Katumbas ng ${b.activityPercent}% ng pinakaaktibong bahagi ng araw nila."
        else "Activity: $parts. That is ${b.activityPercent}% of their busiest part of the day."
    }

    fun stillLine(b: Block, fil: Boolean): String? {
        val median = b.stillMedian ?: return null
        val mad = b.stillMad ?: return null
        val m = RoutineWording.duration(median, fil)
        val v = RoutineWording.duration(mad, fil)
        return if (fil) "Hindi gumagalaw: bawat araw, itinala ang pinakamahabang pagkakataong hindi sila gumalaw sa bahaging ito. Ang gitnang halaga sa ${b.days} araw ay $m, at ang karaniwang pagbabago sa isang araw ay ± $v."
        else "Stillness: each day, the longest stretch they sat still in this part of the day is recorded. The middle value over ${b.days} days is $m, and an ordinary day varies by ± $v."
    }

    fun alertLine(b: Block, fil: Boolean): String? {
        val median = b.stillMedian ?: return null
        val mad = b.stillMad ?: return null
        val after = b.alertAfter ?: return null
        val m = RoutineWording.duration(median, fil)
        val v = RoutineWording.duration(maxOf(mad, SeedBaselineGenerator.MIN_MAD_FLOOR.getValue("inactivity_duration")), fil)
        val a = RoutineWording.duration(after, fil)
        val sum = "$m + %.2f × $v = $a".format(MADS_TO_GATE)
        return if (fil) "Itatanong ng sistema kung ligtas sila kapag lumampas ang hindi paggalaw sa humigit-kumulang $a: $sum. (2.5 ÷ 0.6745 = %.2f.) Sinusuri pa ang oras ng araw at paggamit ng telepono bago magtanong.".format(MADS_TO_GATE)
        else "The system would first ask if they are safe once stillness passes about $a: $sum. (2.5 ÷ 0.6745 = %.2f.) Time of day and phone use are checked before it asks.".format(MADS_TO_GATE)
    }

    fun phoneLine(b: Block, fil: Boolean): String? {
        val n = b.unlocks ?: return null
        val phone = b.phone ?: return null
        val band = when (phone) {
            RoutineSummary.PHONE_RARELY -> if (fil) "bihira (wala pang 2)" else "rarely (under 2)"
            RoutineSummary.PHONE_SOMETIMES -> if (fil) "paminsan-minsan (2 hanggang 7)" else "sometimes (2 to 7)"
            else -> if (fil) "madalas (8 pataas)" else "often (8 or more)"
        }
        val count = n.roundToInt()
        return if (fil) "Telepono: karaniwang $count pagbukas sa bahaging ito, kaya $band."
        else "Phone: typically $count unlocks in this part of the day, so $band."
    }

    fun seedLine(b: Block, fil: Boolean): String? =
        if (!b.hasSeed) null
        else if (fil) "Ang ilang numero dito ay galing pa sa mga sagot sa setup, hindi pa sa totoong datos."
        else "Some figures here are still from the setup answers, not yet from real readings."

    fun footer(fil: Boolean): String =
        if (fil) "Bawat numero ay ang gitnang halaga (median) ng mga araw na naitala ng telepono ng senior, at ang ± ay ang median absolute deviation (MAD) nito. Ang mga buod na ito lang ang ipinapadala; nananatili sa telepono ang mga pang-araw-araw na pagbasa."
        else "Each figure is the median of the days the senior's phone recorded, and ± is its median absolute deviation (MAD). Only these summaries are sent; the daily readings stay on the phone."
}
