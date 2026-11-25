package com.pup.seenior.ui.family

import com.pup.seenior.network.dto.RoutineRowDto
import com.pup.seenior.network.dto.RoutineTimingDto
import com.pup.seenior.ui.routine.SeniorRoutineText

/**
 * What the family's routine card says, laid out like the senior's own "My routine": how far the
 * phone has learned, then the four parts of the day, each with its hours, a Learned / Setup answer
 * tag, one plain line and four usual figures. Pure, so it is unit-tested.
 *
 * Third person and without pronouns, because the app does not know the senior's pronouns. The
 * "usually" line says only what the cloud holds: when the phone first saw the senior active in the
 * morning and last active in the afternoon and evening, each from at least 3 days. A part without
 * one gets no line, never an invented time. Display only: detection never reads any of this.
 */
object FamilyRoutineParts {

    data class Line(val label: String, val value: String)

    data class Part(
        val block: String,
        val name: String,
        /** "6:30 AM - 11:30 AM", or null until the senior's phone has sent the block times. */
        val hours: String?,
        val learned: Boolean,
        /** The one plain line under the name; null when the phone has nothing to say yet. */
        val headline: String?,
        val lines: List<Line>
    )

    fun learnedTag(learned: Boolean, fil: Boolean): String =
        if (learned) "✓ ${SeniorRoutineText.learned(fil)}" else "○ ${SeniorRoutineText.setupAnswer(fil)}"

    fun learningNote(fil: Boolean) =
        if (fil) "Ang mga alerto ay ikinukumpara sa sarili nilang routine ng senior, hindi sa iba."
        else "Alerts are judged against this senior's own routine, not against anyone else's."

    /** The bar under the learning title: 0..1, full once nothing is a setup answer any more. */
    fun learnedFraction(summary: RoutineSummary.Summary): Float =
        if (summary.fullyLearned) 1f else (summary.days / 14f).coerceIn(0f, 1f)

    fun usualLine(block: String, learned: Boolean, timing: RoutineTimingDto?, fil: Boolean): String? {
        if (block == "night") return if (fil) "Oras ng pagtulog" else "Time for sleep"
        if (!learned) return RoutineSummary.stillLearning(fil)
        val (minute, en, tl) = when (block) {
            "morning" -> Triple(timing?.wakeMinute, "Usually up by about", "Karaniwang gising ng mga")
            "afternoon" -> Triple(timing?.afternoonLastMinute, "Usually active until about", "Karaniwang aktibo hanggang mga")
            "evening" -> Triple(timing?.eveningLastMinute, "Usually last active about", "Karaniwang huling aktibo ng mga")
            else -> return null
        }
        minute ?: return null
        return "${if (fil) tl else en} ${RoutineTimingWording.clock(minute)}"
    }

    fun parts(rows: List<RoutineRowDto>, timing: RoutineTimingDto?, fil: Boolean): List<Part> {
        val summary = RoutineSummary.summarize(rows)
        fun median(feature: String, block: String) =
            rows.firstOrNull { it.featureName == feature && it.timeBlock == block }?.medianValue
        return summary.blocks.map { b ->
            val lines = buildList {
                median("inactivity_duration", b.block)?.let {
                    add(Line(SeniorRoutineText.labelStill(fil), RoutineWording.duration(it, fil)))
                }
                median("step_count", b.block)?.let {
                    add(Line(SeniorRoutineText.labelSteps(fil), "%,d".format(Math.round(it))))
                }
                median("screen_unlock_count", b.block)?.let {
                    add(Line(SeniorRoutineText.labelUnlocks(fil), SeniorRoutineText.times(Math.round(it).toInt(), fil)))
                }
                median("screen_idle_duration", b.block)?.let {
                    add(Line(SeniorRoutineText.labelIdle(fil), RoutineWording.duration(it, fil)))
                }
            }
            Part(
                block = b.block,
                name = SeniorRoutineText.blockName(b.block, fil),
                hours = timing?.let { RoutineTimingWording.range(b.block, it) },
                learned = b.learned,
                headline = usualLine(b.block, b.learned, timing, fil),
                lines = lines
            )
        }
    }
}
