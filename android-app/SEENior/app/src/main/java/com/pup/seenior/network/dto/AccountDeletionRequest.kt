package com.pup.seenior.network.dto

/**
 * Mirrors backend `AccountDeletionRequest` / `SeniorDeletionRequest`. [reason] is a stable
 * code from the reason picker (e.g. "switching_phone"), never the on-screen label. [note] is
 * optional free text. Used by POST /auth/me/delete (family) and POST /seniors/{syncId}/delete.
 */
data class AccountDeletionRequest(
    val reason: String,
    val note: String? = null,
)
