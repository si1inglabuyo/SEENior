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
    val createdAt: String
)
