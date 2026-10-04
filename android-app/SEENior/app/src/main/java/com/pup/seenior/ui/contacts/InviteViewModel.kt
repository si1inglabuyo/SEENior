package com.pup.seenior.ui.contacts

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class InviteViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeniorAppDatabase.getInstance(application)
    private val cloudSync = SeniorCloudSync(db)

    var inviteCode by mutableStateOf<String?>(null)
        private set
    var remainingSeconds by mutableStateOf(0)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * The name of a family member who linked with the live code, or null. Set by the pairing
     * watch so the screen can confirm "someone connected"; the server clears the code, so the
     * senior otherwise has no signal that it worked.
     */
    var pairedContactName by mutableStateOf<String?>(null)
        private set

    private var countdownJob: Job? = null
    private var pairingWatchJob: Job? = null

    val hasActiveCode: Boolean get() = inviteCode != null && remainingSeconds > 0

    fun generateInvite() {
        if (hasActiveCode || isLoading) return
        viewModelScope.launch {
            isLoading = true
            error = null
            pairedContactName = null
            try {
                // withSyncId re-registers and retries if the cached id is unknown to the backend.
                val invite = cloudSync.withSyncId { syncId ->
                    RetrofitClient.api.generateInvite(syncId)
                }
                inviteCode = invite.code
                startCountdown(300) // display-only; the backend enforces the real 5-min expiry
                startPairingWatch()
            } catch (e: HttpException) {
                error = if (e.code() == 429)
                    "A code is still active. Wait for it to expire before generating a new one."
                else "Could not generate a code (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server. Make sure the backend is running."
            } catch (e: IllegalStateException) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    /** Dismisses the "family member linked" confirmation. */
    fun acknowledgePaired() {
        pairedContactName = null
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        remainingSeconds = seconds
        countdownJob = viewModelScope.launch {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds -= 1
            }
            inviteCode = null // code expired — return to the "Generate code" state
        }
    }

    /**
     * Polls the senior's own family-contact list while the code is live. There is no push to
     * the senior app for a pairing, so a short poll is the right tool. Stops when it sees a
     * new contact or the code expires.
     */
    private fun startPairingWatch() {
        pairingWatchJob?.cancel()
        pairingWatchJob = viewModelScope.launch {
            // Taken from the first fetch that succeeds, never a failed one, or a network blip
            // returning an empty list would make an already-linked contact look new.
            var baseline: Set<Int>? = null

            while (hasActiveCode) {
                val fetched = runCatching {
                    cloudSync.withSyncId { id -> RetrofitClient.api.getFamilyContacts(id) }
                }.getOrNull()

                if (fetched != null) {
                    val known = baseline
                    if (known == null) {
                        baseline = fetched.map { it.id }.toSet()
                    } else {
                        val newContact = fetched.firstOrNull { it.id !in known }
                        if (newContact != null) {
                            pairedContactName = newContact.fullName?.takeIf { it.isNotBlank() }
                                ?: "A family member"
                            // The server already consumed the code; retire it here so the card stops offering a dead code.
                            countdownJob?.cancel()
                            inviteCode = null
                            remainingSeconds = 0
                            break
                        }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun formattedRemaining(): String {
        val m = remainingSeconds / 60
        val s = remainingSeconds % 60
        return "%d:%02d".format(m, s)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
