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

/**
 * Drives the Link tab's pairing flow: code entry (verify) -> relationship (connected/pair).
 * Assumes the caller is already logged in (account creation is its own earlier step via
 * FamilyAuthViewModel) - used both for a family member's 1st senior and their 2nd/3rd.
 */
class FamilyPairingViewModel(application: Application) : AndroidViewModel(application) {

    // Link step — code entry + verify
    var code by mutableStateOf("")
    var isVerifying by mutableStateOf(false)
        private set
    var verifiedSenior by mutableStateOf<SeniorDto?>(null)
        private set

    // Connected step — relationship + pair
    var selectedRelationship by mutableStateOf<String?>(null)
    var isPairing by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Clears the per-pairing fields so the Link screen starts fresh for "Add another senior". */
    fun resetForNewLink() {
        code = ""
        verifiedSenior = null
        selectedRelationship = null
        error = null
    }

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
        val relationship = selectedRelationship ?: return
        val token = FamilySession.getToken(getApplication()) ?: return
        if (isPairing) return
        viewModelScope.launch {
            isPairing = true
            error = null
            try {
                RetrofitClient.api.pairContact(
                    PairRequest(inviteCode = code, relationshipLabel = relationship),
                    auth = "Bearer $token"
                )
                onPaired()
            } catch (e: HttpException) {
                error = when (e.code()) {
                    400 -> "That code just expired, or you're already at the 3-senior limit."
                    else -> "Could not connect (server error ${e.code()})."
                }
            } catch (e: IOException) {
                error = "Could not reach the server."
            } finally {
                isPairing = false
            }
        }
    }
}
