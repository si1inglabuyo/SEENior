package com.pup.seenior.baseline

import com.pup.seenior.aggregation.ActiveTimes
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SeniorOnboarding
import com.pup.seenior.network.dto.RoutineTimingDto
import java.time.LocalDate

/**
 * What the family card needs to say "Morning, 6:00 AM to 11:00 AM" and "usually first active about
 * 8:10 AM": the four block start times, and a median over the last 14 days of when the senior was
 * first active in the morning and last active in the evening.
 *
 * The block starts come from the wake and sleep times typed at setup (the same
 * [SeedBaselineGenerator.computeTimeBlocks] detection uses, so the card cannot disagree with it).
 * The observed times are a statistical summary and are withheld until [ActiveTimes.MIN_DAYS] days
 * stand behind them. Display only: nothing in detection reads this.
 */
object RoutineTimingBuilder {

    fun build(
        onboarding: SeniorOnboarding,
        aggregates: List<DailyAggregate>,
        today: LocalDate
    ): RoutineTimingDto {
        val windows = SeedBaselineGenerator.computeTimeBlocks(onboarding.wakeTime, onboarding.sleepTime)
        fun start(block: SeedBaselineGenerator.TimeBlock) = windows.first { it.block == block }.startMinute

        // Dates are ISO strings, so they compare as text.
        val since = today.minusDays(ActiveTimes.WINDOW_DAYS.toLong() - 1).toString()
        val recent = aggregates.filter { it.date >= since && it.date <= today.toString() }

        val wake = recent.filter { it.timeBlock == "morning" }.mapNotNull { it.firstActiveMinute }
        val last = recent.filter { it.timeBlock == "evening" }.mapNotNull { it.lastActiveMinute }

        return RoutineTimingDto(
            morningStart = start(SeedBaselineGenerator.TimeBlock.MORNING),
            afternoonStart = start(SeedBaselineGenerator.TimeBlock.AFTERNOON),
            eveningStart = start(SeedBaselineGenerator.TimeBlock.EVENING),
            nightStart = start(SeedBaselineGenerator.TimeBlock.NIGHT),
            wakeMinute = if (wake.size >= ActiveTimes.MIN_DAYS) ActiveTimes.median(wake) else null,
            wakeDays = wake.size,
            eveningLastMinute = if (last.size >= ActiveTimes.MIN_DAYS) ActiveTimes.median(last) else null,
            eveningLastDays = last.size
        )
    }
}
