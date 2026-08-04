package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.ChangePasswordRequest
import com.pup.seenior.network.dto.UpdateProfileRequest
import com.pup.seenior.network.dto.UserDto
import com.pup.seenior.session.FamilySession
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
                error = "Could not load your profile (server error ${e.code()})."
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
                error = "Could not save (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
            } finally {
                isSaving = false
            }
        }
    }

    // Change-password dialog state
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
                passwordError = if (e.code() == 400) "Current password is incorrect."
                    else "Could not change password (server error ${e.code()})."
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
}
