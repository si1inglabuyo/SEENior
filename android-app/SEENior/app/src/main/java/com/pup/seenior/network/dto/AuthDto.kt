package com.pup.seenior.network.dto

/** Mirrors backend RegisterRequest — the family app's Sign Up screen. */
data class RegisterRequest(
    val fullName: String,
    val phone: String,
    val email: String,
    val password: String
)

/** Mirrors backend GoogleSignInRequest — sent after Android's Google Sign-In flow
 *  returns an ID token, which the backend verifies against Google before issuing
 *  our own JWT. */
data class GoogleSignInRequest(
    val idToken: String
)
