package com.pup.seenior.network.dto

/**
 * Mirrors backend RoutineRow: one cell of the Routine Fingerprint, a median and a MAD for one
 * feature in one time block. A statistical summary over 14 days, never a sensor reading.
 */
data class RoutineRowDto(
    val featureName: String,
    val timeBlock: String,
    val medianValue: Double,
    val madValue: Double,
    val sampleCount: Int,
    val isSeed: Boolean
)

/** Mirrors backend RoutineSync. */
data class RoutineSyncRequest(val rows: List<RoutineRowDto>)

/** Mirrors backend RoutineOut. [updatedAt] is null until the senior's phone has synced once. */
data class RoutineDto(
    val rows: List<RoutineRowDto> = emptyList(),
    val updatedAt: String? = null,
    /** Null until a phone that knows about block times has synced; the card then shows bare names. */
    val timing: RoutineTimingDto? = null
)

/**
 * Mirrors backend RoutineTiming. The four `*Start` figures are minutes after midnight and come from
 * the wake and sleep times typed at setup. The observed pair is a median over up to 14 days and is
 * null until at least [com.pup.seenior.aggregation.ActiveTimes.MIN_DAYS] days stand behind it.
 */
data class RoutineTimingDto(
    val morningStart: Int,
    val afternoonStart: Int,
    val eveningStart: Int,
    val nightStart: Int,
    val wakeMinute: Int? = null,
    val wakeDays: Int = 0,
    val eveningLastMinute: Int? = null,
    val eveningLastDays: Int = 0
)

/**
 * Mirrors backend RoutineHistoryRow: one cell of the fingerprint on one day. The same figures
 * as [RoutineRowDto] plus the date. [isEstimate] is true for days the phone worked out afterwards
 * from its stored daily summaries, false for a copy it saved that day.
 */
data class RoutineHistoryRowDto(
    val day: String,
    val featureName: String,
    val timeBlock: String,
    val medianValue: Double,
    val madValue: Double,
    val sampleCount: Int,
    val isSeed: Boolean,
    val isEstimate: Boolean
)

/** Mirrors backend RoutineHistorySync: at most 30 days of 20 cells. */
data class RoutineHistorySyncRequest(val rows: List<RoutineHistoryRowDto>)

/** Mirrors backend RoutineHistoryOut. */
data class RoutineHistoryDto(val rows: List<RoutineHistoryRowDto> = emptyList())
