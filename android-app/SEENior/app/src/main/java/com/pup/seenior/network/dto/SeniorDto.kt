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
