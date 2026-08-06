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

    private var countdownJob: Job? = null

    val hasActiveCode: Boolean get() = inviteCode != null && remainingSeconds > 0

    fun generateInvite() {
        if (hasActiveCode || isLoading) return
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                // withSyncId re-registers and retries if the cached id is unknown to the current
                // backend, so a recreated/switched cloud DB no longer bricks code generation.
                val invite = cloudSync.withSyncId { syncId ->
                    RetrofitClient.api.generateInvite(syncId)
                }
                inviteCode = invite.code
                startCountdown(300) // display-only; the backend enforces the real 5-min expiry
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

    fun formattedRemaining(): String {
        val m = remainingSeconds / 60
        val s = remainingSeconds % 60
        return "%d:%02d".format(m, s)
    }
}
