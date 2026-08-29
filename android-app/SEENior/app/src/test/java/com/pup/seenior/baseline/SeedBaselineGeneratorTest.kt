package com.pup.seenior.baseline

import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.SeniorOnboarding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun `higher self-reported activity level raises movement_score baseline`() {
        val levels = listOf("resting", "light", "moderate", "active")
        val scores = levels.map { level ->
            median(SeedBaselineGenerator.generate(1, onboarding(activityLevel = level)), "morning", "movement_score")
        }
        assertEquals(scores.sorted(), scores)
        assertTrue(scores.first() < scores.last())
    }
}
