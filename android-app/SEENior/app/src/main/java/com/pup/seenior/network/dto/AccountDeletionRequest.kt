package com.pup.seenior.network.dto

/**
 * Mirrors backend `AccountDeletionRequest` / `SeniorDeletionRequest`.
 *
 * [reason] is a stable code from the app's reason picker (e.g. "switching_phone"),
 * never the label shown on screen, so the reasons stay analysable regardless of the
 * user's language. [note] is optional free text.
 *
 * Used by both roles: POST /auth/me/delete (family) and POST /seniors/{syncId}/delete.
 */
data class AccountDeletionRequest(
    val reason: String,
    val note: String? = null,
)
