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
    val updatedAt: String? = null
)
