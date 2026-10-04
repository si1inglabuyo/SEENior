package com.pup.seenior.network.dto

/** Mirrors backend RegisterRequest — the family app's Sign Up screen. */
data class RegisterRequest(
    val fullName: String,
    val phone: String,
    val email: String,
    val password: String
)

/** Mirrors backend GoogleSignInRequest: sent with the ID token from Google Sign-In, which the
 *  backend verifies before issuing our own JWT. */
data class GoogleSignInRequest(
    val idToken: String
)

/** Mirrors backend FirebaseSignInRequest: sent with the ID token from Firebase Auth, which the
 *  backend verifies before issuing our own JWT. */
data class FirebaseSignInRequest(
    val idToken: String,
    val isSignUp: Boolean = false
)
