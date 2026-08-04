package com.pup.seenior.network.dto

/** Mirrors backend UserOut (GET/PATCH /auth/me). */
data class UserDto(
    val id: Int,
    val username: String,
    val role: String,
    val barangay: String?,
    val fullName: String?,
    val phone: String?
)

/** Mirrors backend UserUpdate — the family app's Edit Profile screen. */
data class UpdateProfileRequest(
    val fullName: String,
    val phone: String
)

/** Mirrors backend PasswordChangeRequest. */
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)
