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
import com.pup.seenior.network.dto.LanguagePreferenceRequest
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
    var error by mutableStateOf<FamilyError?>(null)
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
                    FamilyError.SessionExpired
                else FamilyError.Server(FamilyError.Action.LoadProfile, e.code())
            } catch (e: IOException) {
                error = FamilyError.Network()
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
                    FamilyError.SessionExpired
                else FamilyError.Server(FamilyError.Action.SaveProfile, e.code())
            } catch (e: IOException) {
                error = FamilyError.Network()
            } finally {
                isSaving = false
            }
        }
    }

    /** False only for a Google-only account that hasn't set a password. Drives "Set a password"
     *  vs "Change Password" on Edit Profile. Defaults to true until a fetch says otherwise. */
    val hasPassword: Boolean
        get() = user?.hasPassword ?: true

    /** "en" / "fil", read from the fetched profile ("en" until [refresh] lands). Drives
     *  [FamilyStrings.forLanguage] wherever this view model is hoisted (FamilyDashboard). */
    val language: String
        get() = user?.languagePreference ?: "en"

    /**
     * Profile -> Language. Saves to the server immediately (no Save button, as on the senior
     * side) and updates [user] optimistically so screens switch at once.
     */
    fun setLanguage(code: String) {
        val token = token() ?: return
        val current = user ?: return
        if (current.languagePreference == code) return
        viewModelScope.launch {
            try {
                user = RetrofitClient.api.updateLanguage(
                    "Bearer $token",
                    LanguagePreferenceRequest(language = code)
                )
            } catch (e: HttpException) {
                if (SessionState.handleIfUnauthorized(getApplication(), e)) {
                    error = FamilyError.SessionExpired
                }
                // Otherwise leave the language as it was; a failed write shouldn't look like success.
            } catch (e: IOException) {
                // Could not reach the server -- same reasoning, no local override.
            }
        }
    }

    // Change-password / set-password dialog state (shared — set-password skips currentPassword)
    var currentPassword by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var isChangingPassword by mutableStateOf(false)
        private set
    var passwordError by mutableStateOf<FamilyError?>(null)
        private set
    var passwordChanged by mutableStateOf(false)
        private set

    val isPasswordFormValid: Boolean
        get() = currentPassword.isNotBlank() && newPassword.length >= MIN_PASSWORD_LENGTH && newPassword == confirmPassword

    /** The set-password form has no current-password field. */
    val isSetPasswordFormValid: Boolean
        get() = newPassword.length >= MIN_PASSWORD_LENGTH && newPassword == confirmPassword

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
                    e.code() == 400 -> FamilyError.CurrentPasswordIncorrect
                    SessionState.handleIfUnauthorized(getApplication(), e) -> FamilyError.SessionExpired
                    else -> FamilyError.Server(FamilyError.Action.ChangePassword, e.code())
                }
            } catch (e: IOException) {
                passwordError = FamilyError.Network()
            } finally {
                isChangingPassword = false
            }
        }
    }

    /**
     * Adds a password to a Google-only account. Like [changePassword] but with no current
     * password. The account keeps Google Sign-In and also accepts email + password, so
     * `user.hasPassword` becomes true.
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
                    e.code() == 400 -> FamilyError.AccountAlreadyHasPassword
                    SessionState.handleIfUnauthorized(getApplication(), e) -> FamilyError.SessionExpired
                    else -> FamilyError.Server(FamilyError.Action.SetPassword, e.code())
                }
            } catch (e: IOException) {
                passwordError = FamilyError.Network()
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
    var deleteError by mutableStateOf<FamilyError?>(null)
        private set

    /**
     * Soft-deletes this family account on the server, then clears the local session. Not best
     * effort: a failed server call leaves the account intact and sets [deleteError]. On success
     * [PushTokenRegistrar.signOutAsync] releases the push token and clears [FamilySession], and
     * [onDeleted] navigates to the pre-auth flow. [reason] is a stable code ("duplicate"), not the label.
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
                    FamilyError.SessionExpired
                else FamilyError.Server(FamilyError.Action.DeleteAccount, e.code())
            } catch (e: IOException) {
                deleteError = FamilyError.Network(FamilyError.NetworkVariant.TryAgain)
            } finally {
                isDeleting = false
            }
        }
    }
}
