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
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

/**
 * The figures behind one senior's status tiles on the Home tab.
 *
 * `riskLevel` is null until a fetch has actually succeeded: the tile shows "—" rather than
 * asserting "Low", which would be a claim about the senior's safety made from no data.
 */
data class SeniorStatus(
    val riskLevel: String? = null,
    val alertsToday: Int = 0,
    val hasOpenAlert: Boolean = false
)

/** One row of the RECENT ALERTS feed. The feed is merged across every linked senior, so the
 *  name has to travel with the alert — it can't be inferred from the alert alone. */
data class RecentAlert(
    val alert: AlertDto,
    val seniorName: String
)

/**
 * Feeds the family Home tab (designs/family_contact/home_screen_with_linked_senior).
 *
 * Deliberately separate from FamilyAlertsViewModel: that one narrows everything down to the
 * single most urgent open alert to drive the Alerts tab's screen flow, while Home needs
 * per-senior counts and a merged feed. Both read the same authenticated GET /alerts.
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
    var error by mutableStateOf<String?>(null)
        private set

    /** False until a fetch has completed at least once, so the screen can tell "no alerts"
     *  apart from "haven't looked yet" — the two must never render the same way. */
    var loaded by mutableStateOf(false)
        private set

    fun refresh(contacts: List<ContactDto>) {
        val token = FamilySession.getToken(getApplication()) ?: return
        if (contacts.isEmpty()) {
            statuses = emptyMap()
            recent = emptyList()
            loadFailed = false
            loaded = true
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            loadFailed = false
            try {
                val today = LocalDate.now()
                val nextStatuses = mutableMapOf<String, SeniorStatus>()
                val feed = mutableListOf<RecentAlert>()
                for (contact in contacts) {
                    val alerts = RetrofitClient.api.getAlerts(contact.senior.syncId, "Bearer $token")
                    val open = alerts.filter { it.status in OPEN_STATUSES }
                    nextStatuses[contact.senior.syncId] = SeniorStatus(
                        // Highest open risk, not the newest one: a HIGH alert from an hour ago
                        // still outranks a MEDIUM from a minute ago as a summary of how the
                        // senior is doing right now.
                        riskLevel = open.maxByOrNull { RISK_ORDER.indexOf(it.riskLevel) }?.riskLevel
                            ?: "low",
                        // Every alert raised today, not just the open ones — an alert the family
                        // already resolved still happened, and hiding it would understate the day.
                        alertsToday = alerts.count { parseServerTime(it.createdAt)?.toLocalDate() == today },
                        hasOpenAlert = open.isNotEmpty()
                    )
                    val name = "${contact.senior.firstName} ${contact.senior.lastName}".trim()
                    alerts.forEach { feed += RecentAlert(it, name) }
                }
                statuses = nextStatuses
                recent = feed.sortedByDescending { it.alert.createdAt }.take(RECENT_LIMIT)
                loaded = true
            } catch (e: HttpException) {
                error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    SessionState.SESSION_EXPIRED_MESSAGE
                else "Could not load alert activity (server error ${e.code()})."
                loadFailed = true
            } catch (e: IOException) {
                error = "Could not reach the server. Check your internet connection."
                loadFailed = true
            } finally {
                isLoading = false
            }
        }
    }

    companion object {
        private val OPEN_STATUSES = setOf("pending", "acknowledged", "escalated")
        private val RISK_ORDER = listOf("low", "medium", "high")
        private const val RECENT_LIMIT = 5
    }
}

/**
 * Status label for one RECENT ALERTS row.
 *
 * Note the design mock's example row ("Alfreda replied 'I'm okay'") cannot occur here: a senior
 * who answers "I'M SAFE" closes the alert locally as `self_cancelled` and nothing is ever
 * uploaded (CLAUDE.md §11), so the cloud only ever holds alerts that actually escalated.
 */
fun recentAlertChipLabel(status: String): String = when (status) {
    "pending" -> "Pending"
    "acknowledged" -> "Acknowledged"
    "escalated" -> "Escalated"
    "resolved" -> "Resolved"
    "false_positive" -> "False alarm"
    else -> status
}
