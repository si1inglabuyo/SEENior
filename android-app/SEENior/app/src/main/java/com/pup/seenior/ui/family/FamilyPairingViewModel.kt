package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.network.ApiErrorCodes
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.apiErrorCode
import com.pup.seenior.network.dto.PairRequest
import com.pup.seenior.network.dto.SeniorDto
import com.pup.seenior.network.dto.VerifyCodeRequest
import com.pup.seenior.session.FamilySession
import com.pup.seenior.session.SessionState
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * Drives the Link tab's pairing flow: code entry (verify), then relationship (connected/pair).
 * Assumes the caller is logged in; used for a family member's first and later seniors.
 */
class FamilyPairingViewModel(application: Application) : AndroidViewModel(application) {

    // Link step — code entry + verify
    var code by mutableStateOf("")
    var isVerifying by mutableStateOf(false)
        private set
    var verifiedSenior by mutableStateOf<SeniorDto?>(null)
        private set

    // Connected step — relationship + pair
    /** One of the offered chips, or null when the family member typed their own instead. */
    var selectedRelationship by mutableStateOf<String?>(null)
        private set

    /** A relationship the chips do not cover ("niece", "neighbour", "kapitbahay"). */
    var otherRelationship by mutableStateOf("")
        private set

    /**
     * What gets stored for the relationship. A typed value wins over the chips. Null means
     * neither was supplied, which keeps the button disabled.
     */
    val relationshipLabel: String?
        get() = otherRelationship.trim().takeIf { it.isNotEmpty() } ?: selectedRelationship

    /** Picking a chip clears anything typed, so only one answer is ever in play. */
    fun chooseRelationship(value: String) {
        selectedRelationship = value
        otherRelationship = ""
    }

    /** Typing clears the chip for the same reason, and stops at what the column can hold. */
    fun typeOtherRelationship(value: String) {
        otherRelationship = value.take(RELATIONSHIP_MAX_LENGTH)
        if (otherRelationship.isNotBlank()) selectedRelationship = null
    }
    var isPairing by mutableStateOf(false)
        private set

    var error by mutableStateOf<FamilyError?>(null)
        private set

    /** The just-linked senior's first name once [pair] succeeded, or null. Drives the success dialog on the Connected screen. */
    var pairedSeniorName by mutableStateOf<String?>(null)
        private set

    /** The navigation callback passed to [pair], held until the success dialog is dismissed. */
    private var pendingContinuation: (() -> Unit)? = null

    /** Clears the per-pairing fields so the Link screen starts fresh for "Add another senior". */
    fun resetForNewLink() {
        code = ""
        verifiedSenior = null
        selectedRelationship = null
        otherRelationship = ""
        error = null
        pairedSeniorName = null
        pendingContinuation = null
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
                error = if (e.code() == 400) FamilyError.InvalidOrExpiredCode
                    else FamilyError.Server(FamilyError.Action.VerifyCode, e.code())
            } catch (e: IOException) {
                error = FamilyError.Network(FamilyError.NetworkVariant.HasInternet)
            } finally {
                isVerifying = false
            }
        }
    }

    fun pair(onPaired: () -> Unit) {
        val relationship = relationshipLabel ?: return
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
                // Hold the navigation until the confirmation has been seen. An empty string when
                // the server returned no name; the screen then uses copy.theSeniorFallback.
                pendingContinuation = onPaired
                pairedSeniorName = verifiedSenior?.firstName.orEmpty()
            } catch (e: HttpException) {
                error = when {
                    e.code() == 400 -> when (e.apiErrorCode()) {
                        ApiErrorCodes.SENIOR_CONTACT_LIMIT -> FamilyError.SeniorContactLimit
                        ApiErrorCodes.FAMILY_SENIOR_LIMIT -> FamilyError.FamilySeniorLimit
                        else -> FamilyError.CodeExpiredOrLimitReached
                    }
                    SessionState.handleIfUnauthorized(getApplication(), e) -> FamilyError.SessionExpired
                    else -> FamilyError.Server(FamilyError.Action.Connect, e.code())
                }
            } catch (e: IOException) {
                error = FamilyError.Network()
            } finally {
                isPairing = false
            }
        }
    }

    /** Family member dismissed the success dialog — run the navigation that [pair] deferred. */
    fun confirmPairSuccess() {
        val continuation = pendingContinuation
        pairedSeniorName = null
        pendingContinuation = null
        continuation?.invoke()
    }

    private companion object {
        /** Matches contacts.relationship_label, String(32). Truncate here rather than let the server reject the pairing. */
        const val RELATIONSHIP_MAX_LENGTH = 32
    }
}
