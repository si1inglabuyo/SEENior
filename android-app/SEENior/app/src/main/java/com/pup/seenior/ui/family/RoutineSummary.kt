package com.pup.seenior.ui.family

import com.pup.seenior.network.dto.RoutineRowDto

/**
 * The plain-language reading of a Routine Fingerprint: how busy each part of the day is
 * compared with this same senior's other parts, how long they sit still, how much they use the
 * phone. Pure, so it is unit-tested. `barangay-dashboard/src/routine.js` mirrors these rules
 * and cut-offs; change both together.
 *
 * Activity is ranked within the senior, never against a population, which matches the rest of
 * the system (a quiet person is not "low", only quieter at night than in the afternoon).
 */
object RoutineSummary {

    /** Levels, busiest to quietest. "busiest" goes to exactly one block. */
    const val BUSIEST = "busiest"
    const val ACTIVE = "active"
    const val LIGHT = "light"
    const val QUIET = "quiet"

    /** A block at least this fraction as active as the busiest one is "active". */
    private const val ACTIVE_RATIO = 0.66
    private const val LIGHT_RATIO = 0.33

    /** Phone unlocks in a block: fewer than this is "rarely", this many or more is "often". */
    private const val SOMETIMES_FROM = 2.0
    private const val OFTEN_FROM = 8.0

    /** A block still on its setup guess with fewer real days than this shows no "usually" line. */
    const val MIN_LEARNED_DAYS = 7

    const val PHONE_RARELY = "rarely"
    const val PHONE_SOMETIMES = "sometimes"
    const val PHONE_OFTEN = "often"

    data class Block(
        val block: String,
        val level: String,
        /** 0..1 against the busiest block, for the bar. */
        val activity: Double,
        /** Null when the phone has not sent it. */
        val stillSeconds: Double?,
        val phone: String?,
        /** Usual steps and phone unlocks in this block (medians), null when not sent. */
        val steps: Double? = null,
        val unlocks: Double? = null,
        /** False while the block is still mostly the setup guess, so no "usually" line is drawn. */
        val learned: Boolean = true
    )

    data class Summary(
        val blocks: List<Block>,
        val busiest: String?,
        val quietest: String?,
        /** Days of real data behind the fingerprint, 0 to 14. */
        val days: Int,
        val seedCount: Int,
        val total: Int
    ) {
        val fullyLearned: Boolean get() = total > 0 && seedCount == 0
    }

    fun summarize(rows: List<RoutineRowDto>): Summary {
        fun median(feature: String, block: String) =
            rows.firstOrNull { it.featureName == feature && it.timeBlock == block }?.medianValue

        val blocks = RoutineWording.BLOCK_ORDER.filter { b -> rows.any { it.timeBlock == b } }

        // Each metric is scaled to its own busiest block, then averaged, so steps (hundreds)
        // and movement (hundredths) count equally.
        val metrics = listOf("movement_score", "step_count").map { f -> blocks.associateWith { median(f, it) } }
        val raw = blocks.associateWith { b ->
            val ratios = metrics.mapNotNull { m ->
                val max = m.values.filterNotNull().maxOrNull() ?: 0.0
                val v = m[b]
                if (max > 0.0 && v != null) v / max else null
            }
            if (ratios.isEmpty()) 0.0 else ratios.average()
        }
        val peak = raw.values.maxOrNull() ?: 0.0
        val hasActivity = peak > 0.0
        val busiest = if (hasActivity) blocks.maxByOrNull { raw.getValue(it) } else null

        val out = blocks.map { b ->
            val activity = if (hasActivity) raw.getValue(b) / peak else 0.0
            val level = when {
                b == busiest -> BUSIEST
                activity >= ACTIVE_RATIO -> ACTIVE
                activity >= LIGHT_RATIO -> LIGHT
                else -> QUIET
            }
            val unlocks = median("screen_unlock_count", b)
            Block(
                block = b,
                level = level,
                activity = activity,
                stillSeconds = median("inactivity_duration", b),
                steps = median("step_count", b),
                unlocks = unlocks,
                learned = rows.filter { it.timeBlock == b }.none { it.isSeed && it.sampleCount < MIN_LEARNED_DAYS },
                phone = unlocks?.let {
                    when {
                        it < SOMETIMES_FROM -> PHONE_RARELY
                        it < OFTEN_FROM -> PHONE_SOMETIMES
                        else -> PHONE_OFTEN
                    }
                }
            )
        }

        val quietest = if (hasActivity && out.size > 1) out.minByOrNull { it.activity }?.block else null
        return Summary(
            blocks = out,
            busiest = busiest,
            // Stated only when it differs from the busiest, e.g. not when every block ties.
            quietest = quietest?.takeIf { it != busiest },
            days = (rows.maxOfOrNull { it.sampleCount } ?: 0).coerceIn(0, 14),
            seedCount = rows.count { it.isSeed },
            total = rows.size
        )
    }

    // ---- wording ----

    fun levelLabel(level: String, fil: Boolean): String = when (level) {
        BUSIEST -> if (fil) "Pinakaaktibo" else "Busiest"
        ACTIVE -> if (fil) "Aktibo" else "Active"
        LIGHT -> if (fil) "Magaan" else "Light"
        else -> if (fil) "Tahimik" else "Quiet"
    }

    fun phoneLabel(phone: String, fil: Boolean): String = when (phone) {
        PHONE_RARELY -> if (fil) "Phone: bihirang gamitin" else "Phone: rarely used"
        PHONE_SOMETIMES -> if (fil) "Phone: paminsan-minsan" else "Phone: used sometimes"
        else -> if (fil) "Phone: madalas gamitin" else "Phone: used often"
    }

    fun stillLabel(seconds: Double, fil: Boolean): String =
        if (fil) "Hindi gumagalaw nang hanggang ${RoutineWording.duration(seconds, true)}"
        else "Still for up to ${RoutineWording.duration(seconds, false)}"

    // ---- "what's normal" lines (Option B wording) ----

    private fun roundedSteps(steps: Double): Int = (Math.round(steps / 10.0) * 10).toInt()

    /** "Usually active movement (~1,200 steps)". Level comes from this block against the senior's busiest. */
    fun normalMovement(b: Block, fil: Boolean): String {
        val word = when (b.level) {
            BUSIEST, ACTIVE -> if (fil) "aktibo" else "active"
            LIGHT -> if (fil) "banayad" else "light"
            else -> if (fil) "kaunti" else "low"
        }
        val steps = b.steps?.takeIf { it > 0 }?.let { roundedSteps(it) }
        val tail = steps?.let { " (~%,d %s)".format(it, if (fil) "hakbang" else "steps") } ?: ""
        return if (fil) "Karaniwang $word ang galaw$tail" else "Usually $word movement$tail"
    }

    /** "Checked phone around 8 times". */
    fun normalPhone(unlocks: Double, fil: Boolean): String {
        val n = Math.round(unlocks).toInt()
        return when {
            n <= 0 -> if (fil) "Bihirang i-check ang phone" else "Phone rarely checked"
            fil -> "Mga $n beses nag-check ng phone"
            else -> "Checked phone around $n times"
        }
    }

    /** "Usually inactive for up to 2 hr 30 min". */
    fun normalStill(seconds: Double, fil: Boolean): String =
        if (fil) "Karaniwang walang galaw nang hanggang ${RoutineWording.duration(seconds, true)}"
        else "Usually inactive for up to ${RoutineWording.duration(seconds, false)}"

    fun stillLearning(fil: Boolean) =
        if (fil) "Nag-aaral pa sa bahaging ito ng araw." else "Still learning this part of the day."

    fun normalFootnote(fil: Boolean) =
        if (fil) "Ang mga hakbang at pagbukas ay binilang ng phone sa nakaraang 14 na araw. Naglalarawan ito ng phone, hindi diagnosis."
        else "Steps and phone checks are counted by the phone over the last 14 days. This describes the phone, not a diagnosis."

    private fun whenPhrase(block: String, fil: Boolean): String = when (block) {
        "morning" -> if (fil) "sa umaga" else "in the morning"
        "afternoon" -> if (fil) "sa hapon" else "in the afternoon"
        "evening" -> if (fil) "sa gabi" else "in the evening"
        else -> if (fil) "sa oras ng tulog" else "at night"
    }

    /** A fact, not a judgement: null when there is nothing meaningful to say yet. */
    fun headline(s: Summary, fil: Boolean): String? {
        val busy = s.busiest ?: return null
        val quiet = s.quietest ?: return if (fil) "Pinakaaktibo ${whenPhrase(busy, true)}."
        else "Busiest ${whenPhrase(busy, false)}."
        return if (fil) "Pinakaaktibo ${whenPhrase(busy, true)}; pinakatahimik ${whenPhrase(quiet, true)}."
        else "Busiest ${whenPhrase(busy, false)}; quietest ${whenPhrase(quiet, false)}."
    }

    fun progress(s: Summary, fil: Boolean): String = when {
        s.fullyLearned -> if (fil) "Natutunan mula sa nakaraang 14 na araw." else "Learned from the last 14 days."
        s.days == 0 -> if (fil) "Kakasimula pa lang: batay pa sa mga sagot sa setup."
        else "Just started: based on the setup answers for now."
        else -> if (fil) "Nag-aaral pa: araw ${s.days} sa 14." else "Still learning: day ${s.days} of 14."
    }
}
