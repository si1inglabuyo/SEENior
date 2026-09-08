package com.pup.seenior.network.dto

/** Mirrors backend UserOut (GET/PATCH /auth/me). */
data class UserDto(
    val id: Int,
    val username: String,
    val role: String,
    val barangay: String?,
    val fullName: String?,
    val phone: String?,
    val email: String? = null,
    /** False for a Google-only account. Defaults true so the UI never offers "Set a
     *  password" before a fetch has confirmed there isn't one. */
    val hasPassword: Boolean = true
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

/** Mirrors backend PasswordSetRequest — adds a password to a Google-only account. */
data class SetPasswordRequest(
    val newPassword: String
)
