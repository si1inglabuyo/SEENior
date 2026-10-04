package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.FirebaseSignInRequest
import com.pup.seenior.network.dto.GoogleSignInRequest
import com.pup.seenior.network.dto.UpdateProfileRequest
import com.pup.seenior.session.FamilySession
import com.pup.seenior.validation.PhilippinePhone
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bridges a Firebase/Play-services Task into a suspend call, without adding kotlinx-coroutines-play-services for one use. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val exception = task.exception
        if (exception != null) cont.resumeWithException(exception) else cont.resume(task.result)
    }
}

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Drives the family app's Sign Up and Log In screens. Both end with a saved token and the
 *  dashboard with zero seniors linked; pairing happens later via the Link tab. */
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
            var createdFirebaseUser: com.google.firebase.auth.FirebaseUser? = null
            try {
                val email = signUpEmail.trim()
                val result = FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(email, signUpPassword)
                    .awaitResult()
                val firebaseUser = result.user ?: error("Firebase did not return a user")
                createdFirebaseUser = firebaseUser
                val idToken = firebaseUser.getIdToken(false).awaitResult().token
                    ?: error("Firebase did not return an ID token")
                val response = RetrofitClient.api.firebaseSignIn(FirebaseSignInRequest(idToken, isSignUp = true))
                FamilySession.saveToken(getApplication(), response.accessToken)
                // Firebase's token carries no phone number or reliable name for a new account,
                // so send the two fields this screen collected to the profile just created.
                RetrofitClient.api.updateProfile(
                    "Bearer ${response.accessToken}",
                    UpdateProfileRequest(
                        fullName = "${signUpFirstName.trim()} ${signUpLastName.trim()}",
                        phone = PhilippinePhone.normalize(signUpPhone)!!
                    )
                )
                onSuccess()
            } catch (e: FirebaseAuthUserCollisionException) {
                signUpError = "An account with this email already exists."
            } catch (e: FirebaseAuthWeakPasswordException) {
                signUpError = "Please choose a stronger password."
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                signUpError = "That email address doesn't look valid."
            } catch (e: HttpException) {
                if (e.code() == 400) {
                    // The email belongs to an existing account Firebase didn't know about, so the
                    // clash only showed once the backend checked. Roll back the Firebase account
                    // just created, or the email would be stuck.
                    createdFirebaseUser?.let { user ->
                        try { user.delete().awaitResult() } catch (_: Exception) {}
                    }
                    signUpError = "An account with this email already exists. Please log in instead."
                } else {
                    signUpError = "Could not sign up (server error ${e.code()})."
                }
            } catch (e: IOException) {
                signUpError = "Could not reach the server."
            } catch (e: Exception) {
                signUpError = "Could not sign up. Please try again."
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
                val result = FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(loginEmail.trim(), loginPassword)
                    .awaitResult()
                val firebaseUser = result.user ?: error("Firebase did not return a user")
                val idToken = firebaseUser.getIdToken(false).awaitResult().token
                    ?: error("Firebase did not return an ID token")
                val response = RetrofitClient.api.firebaseSignIn(FirebaseSignInRequest(idToken))
                FamilySession.saveToken(getApplication(), response.accessToken)
                onSuccess()
            } catch (e: FirebaseAuthInvalidUserException) {
                loginError = "Incorrect email or password."
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                loginError = "Incorrect email or password."
            } catch (e: HttpException) {
                loginError = "Could not log in (server error ${e.code()})."
            } catch (e: IOException) {
                loginError = "Could not reach the server."
            } catch (e: Exception) {
                loginError = "Could not log in. Please try again."
            } finally {
                isLoggingIn = false
            }
        }
    }

    // Forgot Password (Log In screen's "Forget Password" link)
    var forgotPasswordEmail by mutableStateOf("")
    var isSendingReset by mutableStateOf(false)
        private set
    var resetSent by mutableStateOf(false)
        private set
    var resetError by mutableStateOf<String?>(null)
        private set

    val isForgotPasswordValid: Boolean
        get() = EMAIL_PATTERN.matches(forgotPasswordEmail.trim())

    fun sendPasswordReset() {
        if (!isForgotPasswordValid || isSendingReset) return
        viewModelScope.launch {
            isSendingReset = true
            resetError = null
            try {
                FirebaseAuth.getInstance().sendPasswordResetEmail(forgotPasswordEmail.trim()).awaitResult()
                resetSent = true
            } catch (e: FirebaseAuthInvalidUserException) {
                // Same success state as a real account, since saying "no account" would leak which emails exist.
                resetSent = true
            } catch (e: Exception) {
                resetError = "Could not send the reset email. Check your connection and try again."
            } finally {
                isSendingReset = false
            }
        }
    }

    /** Called when leaving Forgot Password, so old success/error state doesn't reappear. */
    fun resetForgotPasswordState() {
        forgotPasswordEmail = ""
        resetSent = false
        resetError = null
    }

    /** Called on logout. This ViewModel is shared across the family flow, so without this the
     *  next person to sign in would see the previous account's typed-in fields. */
    fun clearAuthFields() {
        signUpFirstName = ""
        signUpLastName = ""
        signUpPhone = ""
        signUpEmail = ""
        signUpPassword = ""
        signUpError = null
        loginEmail = ""
        loginPassword = ""
        loginError = null
        googleError = null
        resetForgotPasswordState()
    }

    // Google Sign-In (Sign Up screen only, per the design)
    var isGoogleSigningIn by mutableStateOf(false)
        private set
    var googleError by mutableStateOf<String?>(null)
        private set

    /**
     * [onSuccess] receives true when the account has no mobile number, so the caller routes to
     * the "complete your details" step. Google's token has no phone number, and the number is
     * needed for the senior's Contacts list and the SMS fallback.
     */
    fun onGoogleIdToken(idToken: String, onSuccess: (needsPhone: Boolean) -> Unit) {
        viewModelScope.launch {
            isGoogleSigningIn = true
            googleError = null
            try {
                val response = RetrofitClient.api.googleSignIn(GoogleSignInRequest(idToken))
                FamilySession.saveToken(getApplication(), response.accessToken)
                onSuccess(hasMissingPhone(response.accessToken))
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

    // ---- Completing a Google account's missing mobile number ----

    /** Cached so the PATCH doesn't re-fetch just to resend full_name, which the endpoint requires. */
    private var pendingFullName: String? = null

    var completePhone by mutableStateOf("")
    var isSavingPhone by mutableStateOf(false)
        private set
    var completePhoneError by mutableStateOf<String?>(null)
        private set

    val isCompletePhoneValid: Boolean
        get() = PhilippinePhone.isValid(completePhone)

    /**
     * True when the signed-in account has no mobile number. Fails "false" (no prompt): sign-in
     * already succeeded, so a failed lookup shouldn't strand the user. Profile -> Edit profile
     * remains the way to add one.
     */
    private suspend fun hasMissingPhone(accessToken: String): Boolean = try {
        val me = RetrofitClient.api.getMe("Bearer $accessToken")
        pendingFullName = me.fullName
        me.phone.isNullOrBlank()
    } catch (e: HttpException) {
        false
    } catch (e: IOException) {
        false
    }

    fun saveMissingPhone(onSaved: () -> Unit) {
        if (!isCompletePhoneValid || isSavingPhone) return
        val token = FamilySession.getToken(getApplication()) ?: return
        viewModelScope.launch {
            isSavingPhone = true
            completePhoneError = null
            try {
                // PATCH /auth/me requires full_name, and a blank one would wipe Google's. Re-read it if the cache is gone.
                val fullName = pendingFullName
                    ?: RetrofitClient.api.getMe("Bearer $token").fullName.orEmpty()
                RetrofitClient.api.updateProfile(
                    "Bearer $token",
                    UpdateProfileRequest(
                        fullName = fullName,
                        phone = PhilippinePhone.normalize(completePhone)!!
                    )
                )
                completePhone = ""
                onSaved()
            } catch (e: HttpException) {
                completePhoneError = "Could not save your number (server error ${e.code()})."
            } catch (e: IOException) {
                completePhoneError = "Could not reach the server. Check your internet connection."
            } finally {
                isSavingPhone = false
            }
        }
    }
}
