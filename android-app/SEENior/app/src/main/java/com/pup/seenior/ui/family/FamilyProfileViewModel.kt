package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.network.PushTokenRegistrar
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.AccountDeletionRequest
import com.pup.seenior.network.dto.ChangePasswordRequest
import com.pup.seenior.network.dto.SetPasswordRequest
import com.pup.seenior.network.dto.UpdateProfileRequest
import com.pup.seenior.network.dto.UserDto
import com.pup.seenior.session.FamilySession
import com.pup.seenior.session.SessionState
import com.pup.seenior.validation.PhilippinePhone
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** Backs the family app's Profile tab and its Edit Profile / Change Password screens. */
class FamilyProfileViewModel(application: Application) : AndroidViewModel(application) {

    var user by mutableStateOf<UserDto?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // Edit-profile form state
    var fullName by mutableStateOf("")
    var phone by mutableStateOf("")
    var isSaving by mutableStateOf(false)
        private set

    val isEditValid: Boolean
        get() = fullName.isNotBlank() && PhilippinePhone.isValid(phone)

    private fun token() = FamilySession.getToken(getApplication())

    fun refresh() {
        val token = token() ?: return
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val me = RetrofitClient.api.getMe("Bearer $token")
                user = me
                fullName = me.fullName ?: ""
                phone = me.phone ?: ""
            } catch (e: HttpException) {
                error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    SessionState.SESSION_EXPIRED_MESSAGE
                else "Could not load your profile (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
            } finally {
                isLoading = false
            }
        }
    }

    fun saveProfile(onSaved: () -> Unit) {
        val token = token() ?: return
        if (!isEditValid || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                val normalizedPhone = PhilippinePhone.normalize(phone)!!
                val updated = RetrofitClient.api.updateProfile(
                    "Bearer $token",
                    UpdateProfileRequest(fullName = fullName.trim(), phone = normalizedPhone)
                )
                user = updated
                onSaved()
            } catch (e: HttpException) {
                error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    SessionState.SESSION_EXPIRED_MESSAGE
                else "Could not save (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
            } finally {
                isSaving = false
            }
        }
    }

    /** False only for a Google-only account that has not set a password yet — drives the
     *  "Set a password" vs "Change Password" choice on Edit Profile. Defaults to the DTO's
     *  own safe default (true) until a profile fetch says otherwise. */
    val hasPassword: Boolean
        get() = user?.hasPassword ?: true

    // Change-password / set-password dialog state (shared — set-password skips currentPassword)
    var currentPassword by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var isChangingPassword by mutableStateOf(false)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set
    var passwordChanged by mutableStateOf(false)
        private set

    val isPasswordFormValid: Boolean
        get() = currentPassword.isNotBlank() && newPassword.length >= 4 && newPassword == confirmPassword

    /** The set-password form has no current-password field. */
    val isSetPasswordFormValid: Boolean
        get() = newPassword.length >= 4 && newPassword == confirmPassword

    fun changePassword() {
        val token = token() ?: return
        if (!isPasswordFormValid || isChangingPassword) return
        viewModelScope.launch {
            isChangingPassword = true
            passwordError = null
            try {
                RetrofitClient.api.changePassword(
                    "Bearer $token",
                    ChangePasswordRequest(currentPassword = currentPassword, newPassword = newPassword)
                )
                currentPassword = ""
                newPassword = ""
                confirmPassword = ""
                passwordChanged = true
            } catch (e: HttpException) {
                passwordError = when {
                    e.code() == 400 -> "Current password is incorrect."
                    SessionState.handleIfUnauthorized(getApplication(), e) -> SessionState.SESSION_EXPIRED_MESSAGE
                    else -> "Could not change password (server error ${e.code()})."
                }
            } catch (e: IOException) {
                passwordError = "Could not reach the server."
            } finally {
                isChangingPassword = false
            }
        }
    }

    /**
     * Adds a password to a Google-only account. Same dialog as [changePassword] but without a
     * current password — there isn't one. On success the account keeps Google Sign-In and now
     * also accepts email + password, so `user.hasPassword` flips true and Edit Profile shows
     * "Change Password" from here on.
     */
    fun setPassword() {
        val token = token() ?: return
        if (!isSetPasswordFormValid || isChangingPassword) return
        viewModelScope.launch {
            isChangingPassword = true
            passwordError = null
            try {
                RetrofitClient.api.setPassword(
                    "Bearer $token",
                    SetPasswordRequest(newPassword = newPassword)
                )
                currentPassword = ""
                newPassword = ""
                confirmPassword = ""
                passwordChanged = true
                user = user?.copy(hasPassword = true)
            } catch (e: HttpException) {
                passwordError = when {
                    e.code() == 400 -> "This account already has a password. Use Change Password."
                    SessionState.handleIfUnauthorized(getApplication(), e) -> SessionState.SESSION_EXPIRED_MESSAGE
                    else -> "Could not set a password (server error ${e.code()})."
                }
            } catch (e: IOException) {
                passwordError = "Could not reach the server."
            } finally {
                isChangingPassword = false
            }
        }
    }

    fun resetPasswordDialogState() {
        currentPassword = ""
        newPassword = ""
        confirmPassword = ""
        passwordError = null
        passwordChanged = false
    }

    // ---- Delete account ----

    var isDeleting by mutableStateOf(false)
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set

    /**
     * Soft-deletes this family account server-side, then clears the local session.
     *
     * Unlike the senior-side wipe this is NOT best-effort: there is nothing local to
     * fall back to, so a failed server call leaves the account intact and surfaces
     * [deleteError] rather than logging the user out of an account that still exists.
     * On success [PushTokenRegistrar.signOutAsync] releases this device's push token
     * (so it stops ringing for seniors this account no longer sees) and clears
     * [FamilySession]; [onDeleted] then navigates back to the pre-auth flow.
     *
     * [reason] is a stable code ("duplicate", …), not the on-screen label.
     */
    fun deleteAccount(reason: String, note: String?, onDeleted: () -> Unit) {
        val token = token() ?: return
        if (isDeleting) return
        isDeleting = true
        deleteError = null
        viewModelScope.launch {
            try {
                RetrofitClient.api.deleteMyAccount(
                    "Bearer $token",
                    AccountDeletionRequest(reason, note)
                )
                PushTokenRegistrar.signOutAsync(getApplication())
                onDeleted()
            } catch (e: HttpException) {
                deleteError = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    SessionState.SESSION_EXPIRED_MESSAGE
                else "Could not delete your account (server error ${e.code()}). Please try again."
            } catch (e: IOException) {
                deleteError = "Could not reach the server. Check your connection and try again."
            } finally {
                isDeleting = false
            }
        }
    }
}
