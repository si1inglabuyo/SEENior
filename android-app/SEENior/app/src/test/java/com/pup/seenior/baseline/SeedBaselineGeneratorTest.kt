package com.pup.seenior.baseline

import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.SeniorOnboarding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SeedBaselineGeneratorTest {

    private fun onboarding(
        activityLevel: String = "light",
        hasNap: Boolean = false,
        napTime: String? = null,
        napDurationMinutes: Int? = null,
    ) = SeniorOnboarding(
        seniorId = 1,
        wakeTime = "06:00",
        sleepTime = "22:00",
        hasNap = hasNap,
        napTime = napTime,
        napDurationMinutes = napDurationMinutes,
        activityLevel = activityLevel,
        languagePreference = "en",
    )

    private fun median(rows: List<Baseline>, timeBlock: String, featureName: String): Double =
        rows.first { it.timeBlock == timeBlock && it.featureName == featureName }.medianValue

    private fun mad(rows: List<Baseline>, timeBlock: String, featureName: String): Double =
        rows.first { it.timeBlock == timeBlock && it.featureName == featureName }.madValue

    @Test
    fun `generates one row per feature per time block`() {
        val rows = SeedBaselineGenerator.generate(1, onboarding())
        assertEquals(20, rows.size)
        assertTrue(rows.all { it.isSeed })
        assertTrue(rows.all { it.sampleCount == 0 })
    }

    @Test
    fun `night block reflects sleep, not activity level`() {
        val rows = SeedBaselineGenerator.generate(1, onboarding(activityLevel = "active"))
        // 22:00 to 06:00 is 8 hours = 480 minutes, stored as seconds to match SensorData.
        assertEquals(480.0 * 60, median(rows, "night", "inactivity_duration"), 0.001)
        assertEquals(480.0 * 60, median(rows, "night", "screen_idle_duration"), 0.001)
        assertTrue(median(rows, "night", "movement_score") < 0.1)
    }

    @Test
    fun `nap widens inactivity margin only in the block containing the nap`() {
        // 13:00 falls in the afternoon block (11:20-16:40 given a 06:00-22:00 day)
        val withoutNap = SeedBaselineGenerator.generate(1, onboarding())
        val withNap = SeedBaselineGenerator.generate(
            1,
            onboarding(hasNap = true, napTime = "13:00", napDurationMinutes = 90),
        )

        val baseAfternoonInactivity = median(withoutNap, "afternoon", "inactivity_duration")
        val napAfternoonInactivity = median(withNap, "afternoon", "inactivity_duration")
        assertTrue(napAfternoonInactivity > baseAfternoonInactivity)
        assertEquals(90.0 * 60, napAfternoonInactivity, 0.001)

        // Nap should widen the MAD margin for that feature/block too.
        assertTrue(mad(withNap, "afternoon", "inactivity_duration") > mad(withoutNap, "afternoon", "inactivity_duration"))

        // Blocks not containing the nap are unaffected.
        assertEquals(
            median(withoutNap, "morning", "inactivity_duration"),
            median(withNap, "morning", "inactivity_duration"),
            0.001,
        )
    }

    private fun timestampAt(hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `block elapsed is small just after wake time`() {
        // Wake 10:00 — the morning block is five minutes old at 10:05, so the largest honest
        // inactivity reading is 300 seconds however long the senior has really been still. This
        // is what stops a night's stillness being scored against the morning baseline.
        val elapsed = SeedBaselineGenerator.secondsSinceBlockStart(timestampAt(10, 5), "10:00", "23:00")
        assertEquals(300L, elapsed)
    }

    @Test
    fun `block elapsed counts from the start of the night block`() {
        // Sleep 23:00, so at 02:30 the night block is three and a half hours old.
        val elapsed = SeedBaselineGenerator.secondsSinceBlockStart(timestampAt(2, 30), "10:00", "23:00")
        assertEquals(3 * 3600L + 1800L, elapsed)
    }

    @Test
    fun `block elapsed includes the seconds inside the current minute`() {
        val elapsed = SeedBaselineGenerator.secondsSinceBlockStart(timestampAt(10, 5, 20), "10:00", "23:00")
        assertEquals(320L, elapsed)
    }

    @Test
    fun `higher self-reported activity level raises movement_score baseline`() {
        val levels = listOf("resting", "light", "moderate", "active")
        val scores = levels.map { level ->
            median(SeedBaselineGenerator.generate(1, onboarding(activityLevel = level)), "morning", "movement_score")
        }
        assertEquals(scores.sorted(), scores)
        assertTrue(scores.first() < scores.last())
    }

    // Logical-day grouping. Wake 10:00 / sleep 23:00 are the pilot senior's real answers, so
    // these reproduce the handset case rather than an invented one: her night runs 23:00 to
    // 10:00 and therefore straddles two calendar dates.

    private fun septemberAt(day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun dayOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_MONTH)

    @Test
    fun `night after midnight belongs to the previous day`() {
        // 03:00 on Sept 2 is the middle of the night that began at 23:00 on Sept 1.
        val logical = SeedBaselineGenerator.logicalDayMillis(septemberAt(2, 3, 0), "10:00", "23:00")
        assertEquals(1, dayOf(logical))
    }

    @Test
    fun `night before midnight keeps its own day`() {
        val logical = SeedBaselineGenerator.logicalDayMillis(septemberAt(1, 23, 30), "10:00", "23:00")
        assertEquals(1, dayOf(logical))
    }

    @Test
    fun `both halves of one night group under the same day`() {
        val beforeMidnight = SeedBaselineGenerator.logicalDayMillis(septemberAt(1, 23, 30), "10:00", "23:00")
        val afterMidnight = SeedBaselineGenerator.logicalDayMillis(septemberAt(2, 3, 0), "10:00", "23:00")
        assertEquals(dayOf(beforeMidnight), dayOf(afterMidnight))
    }

    @Test
    fun `the last sample before wake time still belongs to the night before`() {
        val logical = SeedBaselineGenerator.logicalDayMillis(septemberAt(2, 9, 59), "10:00", "23:00")
        assertEquals(1, dayOf(logical))
    }

    @Test
    fun `waking blocks are never shifted`() {
        listOf(11, 16, 21).forEach { hour ->
            val logical = SeedBaselineGenerator.logicalDayMillis(septemberAt(2, hour, 0), "10:00", "23:00")
            assertEquals(2, dayOf(logical))
        }
    }

    @Test
    fun `a night that does not cross midnight is left alone`() {
        // Sleeps at 01:00, wakes at 10:00 — the whole night already sits inside one date, and
        // shifting it back would be the very bug logicalDayMillis exists to prevent.
        val logical = SeedBaselineGenerator.logicalDayMillis(septemberAt(2, 3, 0), "10:00", "01:00")
        assertEquals(2, dayOf(logical))
    }

    // --- Any block can be the one that crosses midnight, not only night. -------------------
    // The blocks tile the 24-hour clock, so exactly one contains midnight -- but which one
    // depends on the senior's hours. A senior who goes to bed AFTER midnight has a night that
    // sits inside one date and an EVENING that wraps, which the night-specific version of
    // logicalDayMillis ignored entirely.

    @Test
    fun `evening that crosses midnight belongs to the day it began`() {
        // Wake 08:00 / sleep 03:00 -> evening runs 20:40 through 03:00. 01:00 on Sept 2 is the
        // tail of the evening that began at 20:40 on Sept 1.
        val logical = SeedBaselineGenerator.logicalDayMillis(septemberAt(2, 1, 0), "08:00", "03:00")
        assertEquals(1, dayOf(logical))
    }

    @Test
    fun `evening before midnight keeps its own day`() {
        val logical = SeedBaselineGenerator.logicalDayMillis(septemberAt(1, 22, 0), "08:00", "03:00")
        assertEquals(1, dayOf(logical))
    }

    @Test
    fun `both halves of one wrapping evening group under the same day`() {
        val before = SeedBaselineGenerator.logicalDayMillis(septemberAt(1, 22, 0), "08:00", "03:00")
        val after = SeedBaselineGenerator.logicalDayMillis(septemberAt(2, 1, 0), "08:00", "03:00")
        assertEquals(dayOf(before), dayOf(after))
    }

    @Test
    fun `a night that sits inside one date is never shifted`() {
        // Same schedule, but 05:00 is night (03:00-08:00) and already on the correct date.
        val logical = SeedBaselineGenerator.logicalDayMillis(septemberAt(2, 5, 0), "08:00", "03:00")
        assertEquals(2, dayOf(logical))
    }

    @Test
    fun `waking blocks are never shifted on a post-midnight bedtime`() {
        listOf(9, 15, 21).forEach { hour ->
            val logical =
                SeedBaselineGenerator.logicalDayMillis(septemberAt(2, hour, 0), "08:00", "03:00")
            assertEquals(2, dayOf(logical))
        }
    }

    @Test
    fun `afternoon that crosses midnight belongs to the day it began`() {
        // Wake 17:00 / sleep 11:00 -> afternoon runs 23:00 through 05:00.
        val logical = SeedBaselineGenerator.logicalDayMillis(septemberAt(2, 1, 0), "17:00", "11:00")
        assertEquals(1, dayOf(logical))
    }

    @Test
    fun `morning that crosses midnight belongs to the day it began`() {
        // Wake 20:00 / sleep 14:00 -> morning runs 20:00 through 02:00.
        val logical = SeedBaselineGenerator.logicalDayMillis(septemberAt(2, 1, 0), "20:00", "14:00")
        assertEquals(1, dayOf(logical))
    }

    @Test
    fun `a wrapping block groups one whole block into one logical day`() {
        // The property the aggregation actually depends on: every reading across a block that
        // straddles midnight reports the same logical day, so the block becomes one group and no
        // step delta ever spans the gap that produced the 10,779-step night.
        val schedule = "08:00" to "03:00"
        val readings = listOf(
            septemberAt(1, 20, 45),
            septemberAt(1, 23, 30),
            septemberAt(2, 0, 15),
            septemberAt(2, 2, 55),
        )
        val days = readings.map {
            dayOf(SeedBaselineGenerator.logicalDayMillis(it, schedule.first, schedule.second))
        }
        assertEquals(listOf(1, 1, 1, 1), days)
    }

    @Test
    fun `block elapsed never exceeds the morning block length`() {
        // Morning is 260 minutes wide for wake 10:00 / sleep 23:00. This is the ceiling the
        // aggregation clip relies on — aggregate_id 12 stored 26,652 s inside it.
        val elapsed = SeedBaselineGenerator.secondsSinceBlockStart(timestampAt(14, 19), "10:00", "23:00")
        assertTrue(elapsed < 260 * 60L)
    }
}
