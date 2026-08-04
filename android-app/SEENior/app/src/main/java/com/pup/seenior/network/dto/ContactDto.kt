package com.pup.seenior.network.dto

/** Mirrors backend InviteCodeOut (senior generates a code). */
data class InviteCodeDto(
    val code: String,
    val expiresAt: String
)

/** Mirrors backend VerifyCodeRequest (family checks a code before committing). */
data class VerifyCodeRequest(
    val inviteCode: String
)

/** Mirrors backend VerifyCodeResponse — the senior to show on the Connected screen. */
data class VerifyCodeResponse(
    val senior: SeniorDto
)

/** Mirrors backend PairRequest. Requires the caller to already be logged in (via
 *  /auth/register, /auth/login, or /auth/google) - this only links that account to
 *  a senior. relationshipLabel is chosen on the Connected screen. */
data class PairRequest(
    val inviteCode: String,
    val relationshipLabel: String
)

/** Mirrors backend ContactOut (family-side: which senior am I linked to). */
data class ContactDto(
    val id: Int,
    val contactType: String,
    val relationshipLabel: String?,
    val createdAt: String,
    val senior: SeniorDto
)

data class TokenDto(
    val accessToken: String,
    val tokenType: String
)

/** Mirrors backend PairResponse. */
data class PairResponseDto(
    val contact: ContactDto,
    val token: TokenDto
)

/** Mirrors backend FamilyContactOut (senior-side: a family member on the Contacts list). */
data class FamilyContactDto(
    val id: Int,
    val fullName: String?,
    val phone: String?,
    val relationshipLabel: String?,
    val contactType: String,
    val createdAt: String
)
