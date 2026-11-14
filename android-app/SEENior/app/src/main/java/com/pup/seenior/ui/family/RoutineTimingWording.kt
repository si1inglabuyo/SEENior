package com.pup.seenior.ui.family

import com.pup.seenior.network.dto.RoutineTimingDto

/**
 * Plain-language wording for when each time block runs and when the senior's phone usually saw
 * them first and last active. Pure, so it is unit-tested.
 *
 * Two different things are shown and must never be blurred:
 *  - the **range** is what the senior typed at setup (wake and sleep time), the same boundaries
 *    detection uses;
 *  - the **usual first / last active time** is what the phone actually saw, a median over the last
 *    14 days, shown only once at least 3 days stand behind it.
 * The second is information only. It does not move the blocks and detection never reads it.
 */
object RoutineTimingWording {

    /** "6:00 AM", "12:30 PM". The same in English and Filipino, as Filipino clocks are read. */
    fun clock(minuteOfDay: Int): String {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        val hour24 = m / 60
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (val h = hour24 % 12) { 0 -> 12; else -> h }
        return "%d:%02d %s".format(hour12, m % 60, suffix)
    }

    private fun startOf(block: String, t: RoutineTimingDto): Int? = when (block) {
        "morning" -> t.morningStart
        "afternoon" -> t.afternoonStart
        "evening" -> t.eveningStart
        "night" -> t.nightStart
        else -> null
    }

    /** A block ends where the next one starts, and night runs on to the next morning. */
    private fun endOf(block: String, t: RoutineTimingDto): Int? = when (block) {
        "morning" -> t.afternoonStart
        "afternoon" -> t.eveningStart
        "evening" -> t.nightStart
        "night" -> t.morningStart
        else -> null
    }

    /** "6:00 AM – 11:00 AM", or null for an unknown block. */
    fun range(block: String, t: RoutineTimingDto): String? {
        val start = startOf(block, t) ?: return null
        val end = endOf(block, t) ?: return null
        return "${clock(start)} – ${clock(end)}"
    }

    /**
     * What the phone usually saw, for the two blocks where it can say: when the senior is first
     * active in the morning and last active in the evening. Null for the others, and for a block
     * with fewer than 3 days behind it.
     */
    fun usuallyActive(block: String, t: RoutineTimingDto, fil: Boolean): String? {
        val (minute, days, first) = when (block) {
            "morning" -> Triple(t.wakeMinute, t.wakeDays, true)
            "evening" -> Triple(t.eveningLastMinute, t.eveningLastDays, false)
            else -> return null
        }
        if (minute == null) return null
        val at = clock(minute)
        return if (fil) {
            val what = if (first) "Karaniwang unang gumagalaw" else "Karaniwang huling gumagalaw"
            "$what bandang $at (batay sa $days araw)"
        } else {
            val what = if (first) "Usually first active about" else "Usually last active about"
            "$what $at (based on $days days)"
        }
    }

    /** Said once under the blocks, only when at least one "usually" line was shown. */
    fun note(fil: Boolean) =
        if (fil) "Ang oras ng bawat bahagi ng araw ay batay sa sagot ng senior sa setup. Ang \"karaniwan\" ay ang aktuwal na nakita ng telepono."
        else "The hours of each part of the day come from the senior's setup answers. \"Usually\" is what the phone actually saw."
}
