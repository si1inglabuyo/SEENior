package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.GoogleSignInRequest
import com.pup.seenior.network.dto.RegisterRequest
import com.pup.seenior.session.FamilySession
import com.pup.seenior.validation.PhilippinePhone
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Drives the family app's Sign Up and Log In screens (designs/family_contact/account_setup).
 *  Both end the same way: a token saved via FamilySession, landing on FamilyDashboard with
 *  zero seniors linked - pairing happens later, separately, via the Link tab. */
class FamilyAuthViewModel(application: Application) : AndroidViewModel(application) {

    // Sign Up fields
    var signUpFirstName by mutableStateOf("")
    var signUpLastName by mutableStateOf("")
    var signUpPhone by mutableStateOf("")
    var signUpEmail by mutableStateOf("")
    var signUpPassword by mutableStateOf("")
    var isSigningUp by mutableStateOf(false)
        private set
    var signUpError by mutableStateOf<String?>(null)
        private set

    val isSignUpValid: Boolean
        get() = signUpFirstName.isNotBlank() &&
            signUpLastName.isNotBlank() &&
            PhilippinePhone.isValid(signUpPhone) &&
            EMAIL_PATTERN.matches(signUpEmail.trim()) &&
            signUpPassword.length >= 4

    fun signUp(onSuccess: () -> Unit) {
        if (!isSignUpValid || isSigningUp) return
        viewModelScope.launch {
            isSigningUp = true
            signUpError = null
            try {
                val fullName = "${signUpFirstName.trim()} ${signUpLastName.trim()}"
                val response = RetrofitClient.api.register(
                    RegisterRequest(
                        fullName = fullName,
                        phone = PhilippinePhone.normalize(signUpPhone)!!,
                        email = signUpEmail.trim(),
                        password = signUpPassword
                    )
                )
                FamilySession.saveToken(getApplication(), response.accessToken)
                onSuccess()
            } catch (e: HttpException) {
                signUpError = if (e.code() == 400) "An account with this email already exists."
                    else "Could not sign up (server error ${e.code()})."
            } catch (e: IOException) {
                signUpError = "Could not reach the server."
            } finally {
                isSigningUp = false
            }
        }
    }

    // Log In fields
    var loginEmail by mutableStateOf("")
    var loginPassword by mutableStateOf("")
    var isLoggingIn by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    val isLoginValid: Boolean
        get() = loginEmail.isNotBlank() && loginPassword.isNotBlank()

    fun logIn(onSuccess: () -> Unit) {
        if (!isLoginValid || isLoggingIn) return
        viewModelScope.launch {
            isLoggingIn = true
            loginError = null
            try {
                val response = RetrofitClient.api.login(loginEmail.trim(), loginPassword)
                FamilySession.saveToken(getApplication(), response.accessToken)
                onSuccess()
            } catch (e: HttpException) {
                loginError = if (e.code() == 401) "Incorrect username or password."
                    else "Could not log in (server error ${e.code()})."
            } catch (e: IOException) {
                loginError = "Could not reach the server."
            } finally {
                isLoggingIn = false
            }
        }
    }

    // Google Sign-In (Sign Up screen only, per the design)
    var isGoogleSigningIn by mutableStateOf(false)
        private set
    var googleError by mutableStateOf<String?>(null)
        private set

    fun onGoogleIdToken(idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isGoogleSigningIn = true
            googleError = null
            try {
                val response = RetrofitClient.api.googleSignIn(GoogleSignInRequest(idToken))
                FamilySession.saveToken(getApplication(), response.accessToken)
                onSuccess()
            } catch (e: HttpException) {
                googleError = if (e.code() == 503) "Google Sign-In isn't configured on the server yet."
                    else "Could not sign in with Google (server error ${e.code()})."
            } catch (e: IOException) {
                googleError = "Could not reach the server."
            } finally {
                isGoogleSigningIn = false
            }
        }
    }

    fun onGoogleError(message: String) {
        googleError = message
    }
}
