package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.AlertDispatchRequest
import com.pup.seenior.network.dto.AlertDto
import com.pup.seenior.network.dto.ContactDto
import com.pup.seenior.session.FamilySession
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class AlertScreen { LOADING, ALL_CLEAR, LOAD_FAILED, DETAIL, ACKNOWLEDGED, CALL_SENIOR, LOCATION, DISPATCH, RESOLVED }

/** A closed alert's summary, kept only for the Resolved screen right after resolving it
 *  (designs/family_contact/dashboard_notification/Resolved.png) — not persisted anywhere else. */
data class ResolvedSummary(
    val alertShortId: String,
    val triggeredAt: String,
    val resolvedAt: String,
    val durationMinutes: Long
)

/**
 * Drives the family Alerts tab (designs/family_contact/dashboard_notification). Watches every
 * linked senior's alerts and surfaces the single most urgent open one (status pending/
 * acknowledged/escalated); falls back to an "All Clear" state for the first linked senior when
 * nothing is open. One active alert at a time matches the mockups, which are single-senior.
 */
class FamilyAlertsViewModel(application: Application) : AndroidViewModel(application) {
    // Starts at LOADING, never ALL_CLEAR: "All Clear" is a positive assertion that the senior is
    // safe, and the app must not make that claim before it has actually heard from the server.
    var screen by mutableStateOf(AlertScreen.LOADING)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var activeAlert by mutableStateOf<AlertDto?>(null)
        private set
    var activeSenior by mutableStateOf<ContactDto?>(null)
        private set
    var resolvedSummary by mutableStateOf<ResolvedSummary?>(null)
        private set

    private var actionInFlight = false

    fun refresh(contacts: List<ContactDto>) {
        val token = FamilySession.getToken(getApplication()) ?: return
        if (contacts.isEmpty()) {
            activeAlert = null
            activeSenior = null
            screen = AlertScreen.ALL_CLEAR
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            screen = AlertScreen.LOADING
            try {
                var bestAlert: AlertDto? = null
                var bestContact: ContactDto? = null
                for (contact in contacts) {
                    val alerts = RetrofitClient.api.getAlerts(contact.senior.syncId, "Bearer $token")
                    val open = alerts.filter { it.status in OPEN_STATUSES }
                    val newest = open.maxByOrNull { it.createdAt }
                    if (newest != null && (bestAlert == null || newest.createdAt > bestAlert!!.createdAt)) {
                        bestAlert = newest
                        bestContact = contact
                    }
                }
                activeAlert = bestAlert
                activeSenior = bestContact ?: contacts.first()
                screen = if (bestAlert != null) {
                    if (bestAlert.status == "pending") AlertScreen.DETAIL else AlertScreen.ACKNOWLEDGED
                } else {
                    AlertScreen.ALL_CLEAR
                }
            } catch (e: HttpException) {
                error = "Could not load alerts (server error ${e.code()})."
                screen = AlertScreen.LOAD_FAILED
            } catch (e: IOException) {
                error = "Could not reach the server. Check your internet connection."
                screen = AlertScreen.LOAD_FAILED
            } finally {
                isLoading = false
            }
        }
    }

    /** Re-runs the fetch after a LOAD_FAILED state; the screen keeps the contacts it was given. */
    fun retry(contacts: List<ContactDto>) {
        refresh(contacts)
    }

    fun acknowledge() {
        val alert = activeAlert ?: return
        val token = FamilySession.getToken(getApplication()) ?: return
        if (actionInFlight) return
        viewModelScope.launch {
            actionInFlight = true
            try {
                activeAlert = RetrofitClient.api.acknowledgeAlert(alert.syncId, "Bearer $token")
                screen = AlertScreen.ACKNOWLEDGED
            } catch (e: HttpException) {
                error = "Could not acknowledge (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
            } finally {
                actionInFlight = false
            }
        }
    }

    fun dispatchBarangay(reason: String, notes: String?) {
        val alert = activeAlert ?: return
        val token = FamilySession.getToken(getApplication()) ?: return
        if (actionInFlight) return
        viewModelScope.launch {
            actionInFlight = true
            try {
                activeAlert = RetrofitClient.api.dispatchAlert(
                    alert.syncId,
                    AlertDispatchRequest(reason = reason, notes = notes.takeUnless { it.isNullOrBlank() }),
                    "Bearer $token"
                )
                screen = AlertScreen.ACKNOWLEDGED
            } catch (e: HttpException) {
                error = "Could not dispatch barangay (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
            } finally {
                actionInFlight = false
            }
        }
    }

    fun markResolved() {
        val alert = activeAlert ?: return
        val token = FamilySession.getToken(getApplication()) ?: return
        if (actionInFlight) return
        viewModelScope.launch {
            actionInFlight = true
            try {
                val resolved = RetrofitClient.api.resolveAlert(alert.syncId, "Bearer $token")
                resolvedSummary = buildSummary(resolved)
                activeAlert = resolved
                screen = AlertScreen.RESOLVED
            } catch (e: HttpException) {
                error = "Could not resolve (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
            } finally {
                actionInFlight = false
            }
        }
    }

    fun goTo(target: AlertScreen) {
        screen = target
    }

    /** Called after leaving the Resolved screen — goes back to All Clear rather than
     *  re-fetching, since the just-resolved alert is correctly excluded from OPEN_STATUSES. */
    fun backToAllClear() {
        activeAlert = null
        resolvedSummary = null
        screen = AlertScreen.ALL_CLEAR
    }

    private fun buildSummary(alert: AlertDto): ResolvedSummary {
        val created = runCatching { LocalDateTime.parse(alert.createdAt) }.getOrNull()
        val resolved = runCatching { alert.resolvedAt?.let(LocalDateTime::parse) }.getOrNull()
        val minutes = if (created != null && resolved != null) Duration.between(created, resolved).toMinutes() else 0L
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
        return ResolvedSummary(
            alertShortId = "#SNR-" + alert.syncId.take(4).uppercase(),
            triggeredAt = created?.format(timeFmt) ?: "-",
            resolvedAt = resolved?.format(timeFmt) ?: "-",
            durationMinutes = minutes
        )
    }

    companion object {
        private val OPEN_STATUSES = setOf("pending", "acknowledged", "escalated")
    }
}

/** Plain-language "why we're asking" text derived from Alert.triggerType, mirroring the
 *  senior-side wellness-prompt requirement in CLAUDE.md §7 so families get the same context. */
fun alertReasonText(triggerType: String): String = when (triggerType) {
    "inactivity" -> "No movement for a while during their usual active hours. No response to the check-in prompt."
    "movement" -> "Movement pattern looks unusual compared to their normal routine."
    "screen_idle" -> "Phone hasn't been used in longer than usual for this time of day."
    "charging" -> "Device has been charging far longer than expected with no normal activity."
    "sos" -> "They pressed the SOS button."
    "ml_flag" -> "Today's overall activity pattern looks unusual compared to their routine."
    "fall_pattern" -> "A possible fall was detected."
    else -> "An unusual pattern was detected in their routine."
}

fun relativeTimeAgo(iso: String): String {
    val then = runCatching { LocalDateTime.parse(iso) }.getOrNull() ?: return ""
    val minutes = Duration.between(then, LocalDateTime.now()).toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} hr ago"
    }
}

fun formatClockTime(iso: String): String {
    val then = runCatching { LocalDateTime.parse(iso) }.getOrNull() ?: return ""
    return then.format(DateTimeFormatter.ofPattern("h:mm a"))
}
