package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.AlertDto
import com.pup.seenior.network.dto.ContactDto
import com.pup.seenior.session.FamilySession
import com.pup.seenior.session.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

/**
 * The figures behind one senior's status tiles on Home. `riskLevel` is null until a fetch
 * has succeeded, so the tile shows "—" instead of claiming "Low" from no data.
 */
data class SeniorStatus(
    val riskLevel: String? = null,
    val alertsToday: Int = 0,
    val hasOpenAlert: Boolean = false
)

/** One row of the RECENT ALERTS feed, merged across seniors, so the name travels with the alert. */
data class RecentAlert(
    val alert: AlertDto,
    val seniorName: String
)

/**
 * Feeds the family Home tab. Separate from FamilyAlertsViewModel, which narrows to the single
 * most urgent alert; Home needs per-senior counts and a merged feed. Both read the same
 * authenticated GET /alerts.
 */
class FamilyHomeViewModel(application: Application) : AndroidViewModel(application) {
    /** Keyed by senior sync_id. */
    var statuses by mutableStateOf<Map<String, SeniorStatus>>(emptyMap())
        private set
    var recent by mutableStateOf<List<RecentAlert>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var error by mutableStateOf<FamilyError?>(null)
        private set

    /** False until a fetch has completed, so "no alerts" and "haven't looked yet" never render the same. */
    var loaded by mutableStateOf(false)
        private set


    private var pollJob: Job? = null

    /**
     * Re-fetches every [POLL_INTERVAL_MS] while the Home tab is resumed, so an SOS shows up
     * while the family member is looking at this screen. This is a foreground stopgap, not the
     * notification channel (FCM covers a closed app).
     */
    fun startPolling(contacts: List<ContactDto>) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                load(contacts)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /** One-shot fetch, used by the retry buttons. */
    fun refresh(contacts: List<ContactDto>) {
        viewModelScope.launch { load(contacts) }
    }

    private suspend fun load(contacts: List<ContactDto>) {
        val token = FamilySession.getToken(getApplication()) ?: return
        if (contacts.isEmpty()) {
            statuses = emptyMap()
            recent = emptyList()
            loadFailed = false
            loaded = true
            return
        }
        isLoading = true
        error = null
        try {
            val today = LocalDate.now()
            val nextStatuses = mutableMapOf<String, SeniorStatus>()
            val feed = mutableListOf<RecentAlert>()
            for (contact in contacts) {
                val alerts = RetrofitClient.api.getAlerts(contact.senior.syncId, "Bearer $token")
                val open = alerts.filter { it.status in OPEN_STATUSES }
                nextStatuses[contact.senior.syncId] = SeniorStatus(
                    // Highest open risk, not the newest: an hour-old HIGH outranks a minute-old MEDIUM.
                    riskLevel = open.maxByOrNull { RISK_ORDER.indexOf(it.riskLevel) }?.riskLevel
                        ?: "low",
                    // Every alert raised today, not just open ones; a resolved alert still happened.
                    alertsToday = alerts.count { parseServerTime(it.createdAt)?.toLocalDate() == today },
                    hasOpenAlert = open.isNotEmpty()
                )
                val name = "${contact.senior.firstName} ${contact.senior.lastName}".trim()
                alerts.forEach { feed += RecentAlert(it, name) }
            }
            statuses = nextStatuses
            recent = feed.sortedByDescending { it.alert.createdAt }.take(RECENT_LIMIT)
            loaded = true
            loadFailed = false
        } catch (e: HttpException) {
            error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                FamilyError.SessionExpired
            else FamilyError.Server(FamilyError.Action.LoadHomeActivity, e.code())
            // A failed poll must not wipe data already on screen; the last known alerts stay up.
            if (!loaded) loadFailed = true
        } catch (e: IOException) {
            error = FamilyError.Network(FamilyError.NetworkVariant.CheckConnection)
            if (!loaded) loadFailed = true
        } finally {
            isLoading = false
        }
    }


    companion object {
        private val OPEN_STATUSES = setOf("pending", "acknowledged", "escalated")
        private val RISK_ORDER = listOf("low", "medium", "high")
        private const val RECENT_LIMIT = 5

        /** Short enough that an SOS surfaces while it matters, long enough not to hammer a free-tier backend. */
        private const val POLL_INTERVAL_MS = 20_000L
    }
}

// The status label for a RECENT ALERTS row is FamilyStrings.Copy.recentAlertChipLabel.
//
// A senior who answers "I'M SAFE" closes the alert locally as `self_cancelled` and nothing is
// uploaded, so the cloud only holds alerts that actually escalated.
