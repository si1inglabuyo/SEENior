package com.pup.seenior.afterhours

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** One number on a hotline. [display] is what the person reads; [dial] is what the dialler gets. */
data class AfterHoursNumber(val display: String = "", val dial: String = "")

data class AfterHoursHotline(val name: String = "", val numbers: List<AfterHoursNumber> = emptyList())

/** The hall's duty hours. [openWeekdays] counts Monday as 0 and Sunday as 6, like the server. */
data class HallHours(
    val timezone: String = "Asia/Manila",
    val openHour: Int = 8,
    val closeHour: Int = 17,
    val openWeekdays: List<Int> = listOf(0, 1, 2, 3, 4)
)

/**
 * What the apps show when the barangay hall is closed: its duty hours and who to call instead.
 * Mirrors `GET /config/after-hours` (backend `app/core/after_hours.py`). The phone caches the last
 * copy it fetched and falls back to [DEFAULT], which must carry the same list, so the notice
 * still works with no signal and before the first fetch.
 *
 * [closedDays] are extra days the hall is shut (Manila dates, `yyyy-MM-dd`). The server does not
 * send them yet; the field is here so the notice picks them up the day it does.
 *
 * Every property has a default so Gson builds these through the no-argument constructor, and a
 * field the server leaves out keeps its default instead of arriving as null.
 *
 * No Android imports, so JUnit can drive it.
 */
data class AfterHoursConfig(
    val version: Int = 0,
    val hall: HallHours = HallHours(),
    val hotlines: List<AfterHoursHotline> = emptyList(),
    val closedDays: List<String> = emptyList()
) {

    /** False for anything a phone should not trust: a half-filled reply from a changed server. */
    fun isUsable(): Boolean =
        runCatching {
            ZoneId.of(hall.timezone)
            hall.openHour in 0..23 && hall.closeHour in 1..24 && hall.openHour < hall.closeHour &&
                hall.openWeekdays.all { it in 0..6 } &&
                hotlines.isNotEmpty() &&
                hotlines.all { h -> h.name.isNotBlank() && h.numbers.isNotEmpty() && h.numbers.all { it.dial.isNotBlank() } }
        }.getOrDefault(false)

    /** Whether a responder is on duty at [nowMs]. Judged in the hall's own time zone. */
    fun isHallOpen(nowMs: Long): Boolean {
        val local = Instant.ofEpochMilli(nowMs).atZone(ZoneId.of(hall.timezone))
        val weekday = local.dayOfWeek.value - 1
        if (weekday !in hall.openWeekdays) return false
        if (local.toLocalDate().toString() in closedDays) return false
        return local.hour >= hall.openHour && local.hour < hall.closeHour
    }

    companion object {
        /** The hotline list as posted by the barangay. Not yet confirmed with each office. */
        val DEFAULT = AfterHoursConfig(
            version = 1,
            hall = HallHours(timezone = "Asia/Manila", openHour = 8, closeHour = 17, openWeekdays = listOf(0, 1, 2, 3, 4)),
            hotlines = listOf(
                AfterHoursHotline("National Emergency Hotline", listOf(AfterHoursNumber("911", "911"))),
                AfterHoursHotline("Q.C. Emergency Hotline", listOf(AfterHoursNumber("122", "122"))),
                AfterHoursHotline(
                    "Brgy. UP Campus (Amorsolo)",
                    listOf(AfterHoursNumber("0995-721-9867", "09957219867"), AfterHoursNumber("8-426-9779", "0284269779"))
                ),
                AfterHoursHotline(
                    "Police Station 9",
                    listOf(AfterHoursNumber("8-434-3687", "0284343687"), AfterHoursNumber("0998-598-7958", "09985987958"))
                ),
                AfterHoursHotline("QC Bureau of Fire Protection", listOf(AfterHoursNumber("(02) 833-02344", "0283302344"))),
                AfterHoursHotline(
                    "QCDRRMO",
                    listOf(AfterHoursNumber("0999-228-7362", "09992287362"), AfterHoursNumber("0919-067-1170", "09190671170"))
                )
            )
        )

        private val DAYS_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        private val DAYS_FIL = listOf("Lunes", "Martes", "Miyerkules", "Huwebes", "Biyernes", "Sabado", "Linggo")

        /** "Monday to Friday", or a comma list when the open days are not one unbroken run. */
        fun daysText(openWeekdays: List<Int>, filipino: Boolean): String {
            val days = openWeekdays.distinct().sorted()
            val names = if (filipino) DAYS_FIL else DAYS_EN
            if (days.isEmpty()) return ""
            val unbroken = days.zipWithNext().all { (a, b) -> b == a + 1 }
            return when {
                days.size == 1 -> names[days[0]]
                unbroken -> "${names[days.first()]} ${if (filipino) "hanggang" else "to"} ${names[days.last()]}"
                else -> days.joinToString(", ") { names[it] }
            }
        }

        /** "8:00 AM", from a 24-hour hour. */
        fun hourText(hour: Int): String {
            val h = hour % 24
            val suffix = if (h < 12) "AM" else "PM"
            val twelve = if (h % 12 == 0) 12 else h % 12
            return String.format(Locale.US, "%d:00 %s", twelve, suffix)
        }
    }
}
