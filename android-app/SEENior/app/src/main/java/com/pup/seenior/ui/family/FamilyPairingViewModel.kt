package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.PairRequest
import com.pup.seenior.network.dto.SeniorDto
import com.pup.seenior.network.dto.VerifyCodeRequest
import com.pup.seenior.session.FamilySession
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** Drives the whole family pairing flow: account setup -> link (verify) -> connected (pair). */
class FamilyPairingViewModel(application: Application) : AndroidViewModel(application) {

    // Step 1 — account setup (stands in for the separate account-setup signup flow)
    var fullName by mutableStateOf("")
    var phone by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    val isSetupValid: Boolean
        get() = fullName.isNotBlank() && phone.isNotBlank() && username.isNotBlank() && password.length >= 4

    // Step 2 — link (code entry + verify)
    var code by mutableStateOf("")
    var isVerifying by mutableStateOf(false)
        private set
    var verifiedSenior by mutableStateOf<SeniorDto?>(null)
        private set

    // Step 3 — connected (relationship + pair)
    var selectedRelationship by mutableStateOf<String?>(null)
    var isPairing by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun verify(onVerified: () -> Unit) {
        if (code.length != 6 || isVerifying) return
        viewModelScope.launch {
            isVerifying = true
            error = null
            try {
                val response = RetrofitClient.api.verifyCode(VerifyCodeRequest(inviteCode = code))
                verifiedSenior = response.senior
                onVerified()
            } catch (e: HttpException) {
                error = if (e.code() == 400) "Invalid or expired code. Ask the senior to generate a new one."
                    else "Could not verify (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server. Make sure you have internet."
            } finally {
                isVerifying = false
            }
        }
    }

    fun pair(onPaired: () -> Unit) {
        val senior = verifiedSenior ?: return
        val relationship = selectedRelationship ?: return
        if (isPairing) return
        viewModelScope.launch {
            isPairing = true
            error = null
            try {
                val response = RetrofitClient.api.pairContact(
                    PairRequest(
                        inviteCode = code,
                        fullName = fullName.trim(),
                        phone = phone.trim(),
                        username = username.trim(),
                        password = password,
                        relationshipLabel = relationship
                    )
                )
                FamilySession.save(
                    context = getApplication(),
                    token = response.token.accessToken,
                    seniorSyncId = response.contact.senior.syncId,
                    seniorName = "${response.contact.senior.firstName} ${response.contact.senior.lastName}",
                    relationship = relationship
                )
                onPaired()
            } catch (e: HttpException) {
                error = if (e.code() == 400) "That username is taken, or the code just expired."
                    else "Could not connect (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
            } finally {
                isPairing = false
            }
        }
    }
}
