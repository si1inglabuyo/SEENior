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
import com.pup.seenior.session.SessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class AlertScreen { LOADING, ALL_CLEAR, LOAD_FAILED, DETAIL, ACKNOWLEDGED, CALL_SENIOR, LOCATION, DISPATCH, RESOLVED }

/** A closed alert's summary, kept only for the Resolved screen right after resolving it
 *  (designs/family_contact/dashboard_notification/Resolved.png) — not persisted anywhere else. */
data class ResolvedSummary(
    val alertShortId: String,
    val triggeredAt: String,
    val resolvedAt: String,
    val durationMinutes: Long,
    /** Who closed it, from the audit timeline — see [resolverName]. */
    val resolvedBy: String
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

    private var pollJob: Job? = null

    // Set when the family member taps through from the Home popup, so the next refresh opens the
    // alert they were actually shown instead of whatever "newest open" resolves to.
    private var requestedSyncId: String? = null

    /**
     * Asks the next [refresh] to open one specific alert.
     *
     * Without this, tapping "View" on a popup about a pending alert could land on an entirely
     * different alert that happened to be newer — e.g. an SOS someone already acknowledged —
     * so the screen would answer a question nobody asked.
     */
    fun focusAlert(syncId: String) {
        requestedSyncId = syncId
    }

    /** One-shot fetch that may claim the screen. Used on entry and by the retry button. */
    fun refresh(contacts: List<ContactDto>) {
        viewModelScope.launch { load(contacts, background = false) }
    }

    /**
     * Re-fetches every [POLL_INTERVAL_MS] for as long as the Alerts tab is resumed.
     *
     * The tab previously fetched only when the linked-senior list changed, which in practice meant
     * once — so a family member could sit watching this screen read "All clear" while the alert
     * was already on the server. Of every screen in the app this is the worst one to leave stale.
     */
    fun startPolling(contacts: List<ContactDto>) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var first = true
            while (isActive) {
                // Only the first fetch of a session may take over the screen, and only when there
                // is nothing on it yet. Every later one runs silently: this tab is a flow the
                // family member walks through, and a poll that reset it would throw away the step
                // they were on mid-emergency.
                load(contacts, background = !first || screen != AlertScreen.LOADING)
                first = false
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun load(contacts: List<ContactDto>, background: Boolean) {
        val token = FamilySession.getToken(getApplication()) ?: return
        if (contacts.isEmpty()) {
            activeAlert = null
            activeSenior = null
            if (mayClaimScreen(background)) screen = AlertScreen.ALL_CLEAR
            return
        }
        run {
            isLoading = !background
            error = null
            if (!background) screen = AlertScreen.LOADING
            try {
                var bestAlert: AlertDto? = null
                var bestContact: ContactDto? = null
                var requestedAlert: AlertDto? = null
                var requestedContact: ContactDto? = null
                val wanted = requestedSyncId
                for (contact in contacts) {
                    val alerts = RetrofitClient.api.getAlerts(contact.senior.syncId, "Bearer $token")
                    if (wanted != null) {
                        alerts.firstOrNull { it.syncId == wanted }?.let {
                            requestedAlert = it
                            requestedContact = contact
                        }
                    }
                    val open = alerts.filter { it.status in OPEN_STATUSES }
                    val newest = open.maxByOrNull { it.createdAt }
                    if (newest != null && (bestAlert == null || newest.createdAt > bestAlert!!.createdAt)) {
                        bestAlert = newest
                        bestContact = contact
                    }
                }
                // An explicitly requested alert outranks "newest open": the family member tapped
                // through to that one. Falls back to the usual pick if it has since vanished.
                if (requestedAlert != null) {
                    bestAlert = requestedAlert
                    bestContact = requestedContact
                }

                if (mayClaimScreen(background)) {
                    // Consumed only when it is actually acted on, so a silent poll cannot swallow
                    // the family member's "View this one" before the screen has honoured it.
                    requestedSyncId = null
                    activeAlert = bestAlert
                    activeSenior = bestContact ?: contacts.first()
                    screen = if (bestAlert != null) {
                        if (bestAlert.status == "pending") AlertScreen.DETAIL else AlertScreen.ACKNOWLEDGED
                    } else {
                        AlertScreen.ALL_CLEAR
                    }
                }
            } catch (e: HttpException) {
                error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    SessionState.SESSION_EXPIRED_MESSAGE
                else "Could not load alerts (server error ${e.code()})."
                if (mayClaimScreen(background)) screen = AlertScreen.LOAD_FAILED
            } catch (e: IOException) {
                error = "Could not reach the server. Check your internet connection."
                if (mayClaimScreen(background)) screen = AlertScreen.LOAD_FAILED
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Whether this fetch is allowed to change what is on screen.
     *
     * A foreground fetch always is. A background poll only may while the screen is still just
     * reporting the situation — once the family member has acknowledged, opened the dispatch form
     * or is looking at a resolved summary, they are mid-task and the screen belongs to them.
     */
    private fun mayClaimScreen(background: Boolean): Boolean =
        !background || screen in PASSIVE_SCREENS

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
                error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    SessionState.SESSION_EXPIRED_MESSAGE
                else "Could not acknowledge (server error ${e.code()})."
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
                error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    SessionState.SESSION_EXPIRED_MESSAGE
                else "Could not dispatch barangay (server error ${e.code()})."
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
                error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    SessionState.SESSION_EXPIRED_MESSAGE
                else "Could not resolve (server error ${e.code()})."
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
        val created = parseServerTime(alert.createdAt)
        val resolved = alert.resolvedAt?.let(::parseServerTime)
        val minutes = if (created != null && resolved != null) Duration.between(created, resolved).toMinutes() else 0L
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
        return ResolvedSummary(
            alertShortId = "#SNR-" + alert.syncId.take(4).uppercase(),
            triggeredAt = created?.format(timeFmt) ?: "-",
            resolvedAt = resolved?.format(timeFmt) ?: "-",
            durationMinutes = minutes,
            resolvedBy = resolverName(alert)
        )
    }

    /**
     * Who closed the alert, read from the `resolved_family` entry the backend writes into
     * `escalation_steps`.
     *
     * The screen used to print a hardcoded "You", which was only ever right by luck: any of the
     * senior's other family contacts can resolve an alert, and that screen would still have
     * credited whoever happened to be reading it.
     *
     * Falls back to a dash rather than to "You". Alerts resolved before the backend started
     * recording the name genuinely have no answer, and guessing would put the wrong person's
     * name against someone else's action in an audit trail.
     */
    private fun resolverName(alert: AlertDto): String =
        alert.escalationSteps
            ?.lastOrNull { it["step"] == "resolved_family" }
            ?.get("by")
            ?.takeIf { it.isNotBlank() }
            ?: "—"

    companion object {
        private val OPEN_STATUSES = setOf("pending", "acknowledged", "escalated")

        /** Screens that only report the current situation, so a poll may replace them. The rest
         *  are steps the family member is part-way through and must not be pulled out from under
         *  them. */
        private val PASSIVE_SCREENS = setOf(
            AlertScreen.LOADING,
            AlertScreen.ALL_CLEAR,
            AlertScreen.DETAIL,
            AlertScreen.LOAD_FAILED
        )

        /** Matches the Home tab's cadence — the two tabs read the same endpoint and there is no
         *  reason for one to learn about an alert sooner than the other. */
        private const val POLL_INTERVAL_MS = 20_000L
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

/**
 * Parses a timestamp as the backend writes it and converts it to the device's own zone.
 *
 * The cloud columns are TIMESTAMP WITHOUT TIME ZONE holding UTC, so the strings arrive with no
 * offset. Reading them as local time made every alert look hours stale on any device outside
 * UTC — a brand-new alert would read "8 hr ago" on a phone in the Philippines. Every display of
 * a server timestamp must go through here.
 */
fun parseServerTime(iso: String): ZonedDateTime? {
    // The offset branch is defensive: today the backend always sends naive UTC, but if a column
    // ever becomes tz-aware, silently re-interpreting the offset would shift every timestamp.
    runCatching { OffsetDateTime.parse(iso) }.getOrNull()?.let {
        return it.atZoneSameInstant(ZoneId.systemDefault())
    }
    return runCatching {
        LocalDateTime.parse(iso).atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
    }.getOrNull()
}

fun relativeTimeAgo(iso: String): String {
    val then = parseServerTime(iso) ?: return ""
    val minutes = Duration.between(then, ZonedDateTime.now()).toMinutes()
    return when {
        // Negative means the server clock is marginally ahead of the device's; showing
        // "-1 min ago" would look broken, and the alert is new either way.
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} hr ago"
        else -> "${minutes / (60 * 24)} d ago"
    }
}

fun formatClockTime(iso: String): String {
    val then = parseServerTime(iso) ?: return ""
    return then.format(DateTimeFormatter.ofPattern("h:mm a"))
}

/** Compact form of [alertReasonText] for list rows, where the full sentence doesn't fit. */
fun triggerShortLabel(triggerType: String): String = when (triggerType) {
    "inactivity" -> "No movement"
    "movement" -> "Unusual movement"
    "screen_idle" -> "Phone idle"
    "charging" -> "Charging unusually long"
    "sos" -> "SOS pressed"
    "ml_flag" -> "Unusual daily pattern"
    "fall_pattern" -> "Possible fall"
    else -> "Unusual pattern"
}
