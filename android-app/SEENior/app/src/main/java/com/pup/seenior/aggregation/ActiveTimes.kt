package com.pup.seenior.aggregation

import com.pup.seenior.database.entities.SensorData
import com.pup.seenior.detection.ScreenEngagement
import java.time.Instant
import java.time.ZoneId

/**
 * When, inside one time block, the senior first and last showed sustained activity. It feeds the
 * family card's "usually first active about 8:10 AM" line and nothing else: detection and
 * escalation never read it.
 *
 * Works on the raw readings, so it must run at roll-up time, the last moment they exist.
 *
 * "Active" reuses what the app already treats as being awake: the screen was on within the last
 * five minutes ([ScreenEngagement.WINDOW_SECONDS]) or the phone moved within the last five
 * minutes (the inactivity counter is that low). One such reading is not enough, because a trip to
 * the bathroom or a nudged table produces one, so an activity only counts when a second active
 * reading sits within [SUSTAIN_MS] of it.
 *
 * Limit, stated honestly: it only sees readings inside the block it is given. The morning block
 * starts at the wake time the senior typed, so a senior who really rises earlier than they typed
 * shows the typed time here, never an earlier one.
 */
object ActiveTimes {

    /** A second active reading within this long makes an activity "sustained". */
    const val SUSTAIN_MS = 30 * 60 * 1000L

    /** Fewer days than this and a "usual" time is only a coincidence; nothing is shown. */
    const val MIN_DAYS = 3

    /** Same window as the baseline, so the observed time and the fingerprint describe the same days. */
    const val WINDOW_DAYS = 14

    fun isActive(row: SensorData): Boolean =
        row.screenIdleDuration <= ScreenEngagement.WINDOW_SECONDS ||
            row.inactivityDuration <= ScreenEngagement.WINDOW_SECONDS

    /** Minute of the day (0-1439) for [timestamp] in the phone's own time zone. */
    fun minuteOfDay(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val t = Instant.ofEpochMilli(timestamp).atZone(zone)
        return t.hour * 60 + t.minute
    }

    /**
     * The first and last sustained-active minute of the day in [rows] (one block's readings),
     * or null for either when the block holds no sustained activity.
     */
    fun firstAndLastMinute(rows: List<SensorData>, zone: ZoneId = ZoneId.systemDefault()): Pair<Int?, Int?> {
        val active = rows.filter(::isActive).map { it.timestamp }.sorted()
        if (active.size < 2) return null to null

        val first = active.indices.firstOrNull { i -> i + 1 < active.size && active[i + 1] - active[i] <= SUSTAIN_MS }
            ?.let { active[it] }
        val last = active.indices.lastOrNull { i -> i > 0 && active[i] - active[i - 1] <= SUSTAIN_MS }
            ?.let { active[it] }
        return first?.let { minuteOfDay(it, zone) } to last?.let { minuteOfDay(it, zone) }
    }

    /** The middle value, rounded down, so one odd morning cannot move a "usual" time. */
    fun median(minutes: List<Int>): Int? {
        if (minutes.isEmpty()) return null
        val sorted = minutes.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
