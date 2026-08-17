package com.pup.seenior.network.dto

/**
 * Mirrors backend DeviceTokenRegister — this installation's FCM registration token.
 *
 * Sent on every app start rather than only after login: FCM rotates tokens on its own
 * schedule (reinstall, cleared data, or Google's own decision), and a token the backend
 * never hears about is a family member who silently stops receiving alerts with nothing
 * on screen to suggest anything is wrong.
 */
data class RegisterDeviceRequest(
    val token: String,
    val platform: String = "android"
)

/** Mirrors backend DeviceTokenOut. The token itself is deliberately not returned by the
 *  server — it is a delivery credential, and echoing it back only widens where it leaks. */
data class DeviceDto(
    val id: Int,
    val platform: String,
    val createdAt: String,
    val lastSeenAt: String
)
