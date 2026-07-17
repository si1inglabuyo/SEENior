package com.pup.seenior.baseline

import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.SeniorOnboarding
import java.util.Calendar
/**
 * Converts onboarding questionnaire answers into conservative, wide-margin seed
 * Baseline rows (warm-start) so detection can run before 14 days of real sensor
 * data exist. See CLAUDE.md section 6.
 */
object SeedBaselineGenerator {

    private const val MINUTES_PER_DAY = 24 * 60
    private const val SEED_MARGIN_RATIO = 0.4
    private const val NAP_MARGIN_RATIO = 0.6
    private const val SECONDS_PER_MINUTE = 60.0

    // Feature medians above are authored in minutes (matches onboarding questionnaire
    // units); SensorData/MedianMadDetector work in seconds, so these two need conversion
    // before landing in the Baseline table.
    private val MINUTES_BASED_FEATURES = setOf("inactivity_duration", "screen_idle_duration")

    enum class TimeBlock { MORNING, AFTERNOON, EVENING, NIGHT }

    data class TimeBlockWindow(val block: TimeBlock, val startMinute: Int, val durationMinutes: Int)

    private data class ActivityProfile(
        val movementScore: Double,
        val stepCount: Double,
        val inactivityMinutes: Double,
        val screenIdleMinutes: Double,
        val screenUnlockCount: Double,
    )

    // Conservative per-block expectations by self-reported activity level (waking blocks only).
    private val ACTIVITY_PROFILES = mapOf(
        "resting" to ActivityProfile(0.15, 100.0, 45.0, 40.0, 3.0),
        "light" to ActivityProfile(0.30, 400.0, 30.0, 30.0, 5.0),
        "moderate" to ActivityProfile(0.50, 900.0, 20.0, 20.0, 7.0),
        "active" to ActivityProfile(0.70, 1600.0, 12.0, 15.0, 9.0),
    )

    // Night is sleep, not a point on the activity scale -- same profile regardless of activity level.
    private val NIGHT_MOVEMENT_SCORE = 0.03
    private val NIGHT_STEP_COUNT = 20.0
    private val NIGHT_UNLOCK_COUNT = 0.5
    val MIN_MAD_FLOOR = mapOf(
        "inactivity_duration" to 5.0,
        "movement_score" to 0.05,
        "screen_idle_duration" to 5.0,
        "screen_unlock_count" to 1.0,
        "step_count" to 50.0,
    )

    fun generate(seniorId: Int, onboarding: SeniorOnboarding): List<Baseline> {
        val blocks = computeTimeBlocks(onboarding.wakeTime, onboarding.sleepTime)
        val napBlock = findNapBlock(onboarding, blocks)
        val activityProfile = ACTIVITY_PROFILES[onboarding.activityLevel]
            ?: ACTIVITY_PROFILES.getValue("light")

        return blocks.flatMap { window -> baselinesForWindow(seniorId, window, napBlock, activityProfile, onboarding) }
    }

    fun computeTimeBlocks(wakeTime: String, sleepTime: String): List<TimeBlockWindow> {
        val wakeMinute = parseToMinuteOfDay(wakeTime)
        val sleepMinute = parseToMinuteOfDay(sleepTime)
        val awakeDuration = ((sleepMinute - wakeMinute) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val nightDuration = MINUTES_PER_DAY - awakeDuration
        val blockDuration = awakeDuration / 3

        return listOf(
            TimeBlockWindow(TimeBlock.MORNING, wakeMinute, blockDuration),
            TimeBlockWindow(TimeBlock.AFTERNOON, (wakeMinute + blockDuration) % MINUTES_PER_DAY, blockDuration),
            TimeBlockWindow(TimeBlock.EVENING, (wakeMinute + 2 * blockDuration) % MINUTES_PER_DAY, awakeDuration - 2 * blockDuration),
            TimeBlockWindow(TimeBlock.NIGHT, sleepMinute, nightDuration),
        )
    }

    fun resolveTimeBlock(timestamp: Long, wakeTime: String, sleepTime: String): TimeBlock {
        val minuteOfDay = minuteOfDayFor(timestamp)
        val blocks = computeTimeBlocks(wakeTime, sleepTime)
        return blocks.firstOrNull { minuteWithinWindow(minuteOfDay, it.startMinute,
            it.durationMinutes) }
            ?.block
            ?: TimeBlock.NIGHT
    }

    private fun minuteOfDayFor(timestamp: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    private fun findNapBlock(onboarding: SeniorOnboarding, blocks: List<TimeBlockWindow>): TimeBlock? {
        if (!onboarding.hasNap) return null
        val napMinute = onboarding.napTime?.let(::parseToMinuteOfDay) ?: return null
        return blocks.firstOrNull {
            it.block != TimeBlock.NIGHT && minuteWithinWindow(napMinute, it.startMinute, it.durationMinutes)
        }?.block
    }

    private fun baselinesForWindow(
        seniorId: Int,
        window: TimeBlockWindow,
        napBlock: TimeBlock?,
        activityProfile: ActivityProfile,
        onboarding: SeniorOnboarding,
    ): List<Baseline> {
        val isNight = window.block == TimeBlock.NIGHT
        val isNapBlock = window.block == napBlock

        val inactivityMedian = when {
            isNight -> window.durationMinutes.toDouble()
            isNapBlock -> maxOf(activityProfile.inactivityMinutes, onboarding.napDurationMinutes?.toDouble() ?: 0.0)
            else -> activityProfile.inactivityMinutes
        }
        val screenIdleMedian = if (isNight) window.durationMinutes.toDouble() else activityProfile.screenIdleMinutes
        val movementMedian = if (isNight) NIGHT_MOVEMENT_SCORE else activityProfile.movementScore
        val stepMedian = if (isNight) NIGHT_STEP_COUNT else activityProfile.stepCount
        val unlockMedian = if (isNight) NIGHT_UNLOCK_COUNT else activityProfile.screenUnlockCount

        val features = listOf(
            "inactivity_duration" to inactivityMedian,
            "movement_score" to movementMedian,
            "screen_idle_duration" to screenIdleMedian,
            "screen_unlock_count" to unlockMedian,
            "step_count" to stepMedian,
        )

        return features.map { (featureName, medianRaw) ->
            val isMinutesBased = featureName in MINUTES_BASED_FEATURES
            val median = if (isMinutesBased) medianRaw * SECONDS_PER_MINUTE else medianRaw
            val marginRatio = if (isNapBlock && featureName == "inactivity_duration") NAP_MARGIN_RATIO else SEED_MARGIN_RATIO
            val madFloor = MIN_MAD_FLOOR.getValue(featureName).let { if (isMinutesBased) it * SECONDS_PER_MINUTE else it }
            Baseline(
                seniorId = seniorId,
                featureName = featureName,
                timeBlock = window.block.name.lowercase(),
                medianValue = median,
                madValue = maxOf(median * marginRatio, madFloor),
                sampleCount = 0,
                isSeed = true,
            )
        }
    }

    private fun parseToMinuteOfDay(hhmm: String): Int {
        val (hour, minute) = hhmm.split(":").map { it.toInt() }
        return hour * 60 + minute
    }

    private fun minuteWithinWindow(minute: Int, start: Int, duration: Int): Boolean {
        val end = start + duration
        return if (end <= MINUTES_PER_DAY) minute in start until end
        else minute >= start || minute < (end - MINUTES_PER_DAY)
    }
}
