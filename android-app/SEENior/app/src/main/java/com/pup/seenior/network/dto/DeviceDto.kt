package com.pup.seenior.network.dto

/**
 * Mirrors backend DeviceTokenRegister: this installation's FCM token. Sent on every app
 * start, since FCM rotates tokens and a token the backend never hears about means a family
 * member silently stops receiving alerts.
 */
data class RegisterDeviceRequest(
    val token: String,
    val platform: String = "android"
)

/** Mirrors backend DeviceTokenOut. The token isn't returned by the server; it is a delivery credential. */
data class DeviceDto(
    val id: Int,
    val platform: String,
    val createdAt: String,
    val lastSeenAt: String
)
