package com.pup.seenior.network.dto

/** Mirrors backend SeniorCreate. Field names are camelCase; Gson maps them to snake_case via RetrofitClient. */
data class CreateSeniorRequest(
    val firstName: String,
    val lastName: String,
    val age: Int,
    val gender: String,
    val barangay: String,
    val address: String,
    val mobileNumber: String
)

/** Mirrors backend SeniorUpdate. Every field is sent on every save, so it is a full replace
 *  despite the PATCH verb. */
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
 * Mirrors backend SeniorHeartbeat. Both fields are nullable since a reading can be
 * unavailable, and a check-in with nothing but "still running" is still worth sending.
 */
data class HeartbeatRequest(
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    /**
     * This handset's FCM token, so the server can wake it when it stops checking in. Carried
     * by the heartbeat because the senior has no account to authenticate a separate endpoint,
     * and it keeps the token refreshed on the schedule that proves the phone is alive. Null
     * when none could be obtained.
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
     * Device health, not behaviour: the current reading only, never a series (spec section 11).
     * Null until the phone has checked in, and against an older backend.
     */
    val lastSeenAt: String? = null,
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null
)
