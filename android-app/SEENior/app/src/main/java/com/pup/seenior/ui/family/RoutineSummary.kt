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
        val phone: String?
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
        PHONE_RARELY -> if (fil) "Telepono: bihirang gamitin" else "Phone: rarely used"
        PHONE_SOMETIMES -> if (fil) "Telepono: paminsan-minsan" else "Phone: used sometimes"
        else -> if (fil) "Telepono: madalas gamitin" else "Phone: used often"
    }

    fun stillLabel(seconds: Double, fil: Boolean): String =
        if (fil) "Hindi gumagalaw nang hanggang ${RoutineWording.duration(seconds, true)}"
        else "Still for up to ${RoutineWording.duration(seconds, false)}"

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
