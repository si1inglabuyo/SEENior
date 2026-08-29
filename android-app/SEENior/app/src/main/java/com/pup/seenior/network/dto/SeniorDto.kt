package com.pup.seenior.network.dto

/** Mirrors backend SeniorCreate. Field names are camelCase; Gson maps them to the
 *  backend's snake_case JSON via the naming policy set in RetrofitClient. */
data class CreateSeniorRequest(
    val firstName: String,
    val lastName: String,
    val age: Int,
    val gender: String,
    val barangay: String,
    val address: String,
    val mobileNumber: String
)

/** Mirrors backend SeniorUpdate. Every field is sent on every save — the backend overwrites
 *  all of them, so this is a full replace despite the PATCH verb (same shape as the family
 *  side's UpdateProfileRequest). */
data class UpdateSeniorRequest(
    val firstName: String,
    val lastName: String,
    val age: Int,
    val gender: String,
    val barangay: String,
    val address: String,
    val mobileNumber: String
)

/**
 * Mirrors backend SeniorHeartbeat. Both fields are nullable because a reading can genuinely be
 * unavailable, and a check-in that carries nothing but "still running" is still worth sending.
 */
data class HeartbeatRequest(
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    /**
     * This handset's FCM token, so the server can wake it when it stops checking in.
     *
     * Carried by the heartbeat rather than by a registration call of its own: the senior
     * has no account to authenticate a separate endpoint with, and refreshing the token on
     * exactly the schedule that proves the phone is alive means the two can never drift.
     * Null when a token could not be obtained -- the check-in still counts.
     */
    val pushToken: String?
)

/** Mirrors backend SeniorOut. */
data class SeniorDto(
    val syncId: String,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val gender: String,
    val barangay: String,
    val address: String,
    val mobileNumber: String,
    val createdAt: String,
    /**
     * Device health, not behaviour — the current reading only, never a series (CLAUDE.md §11).
     * All three are null until the senior's phone has checked in at least once, and against an
     * older backend they simply stay null rather than breaking the parse.
     */
    val lastSeenAt: String? = null,
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null
)
