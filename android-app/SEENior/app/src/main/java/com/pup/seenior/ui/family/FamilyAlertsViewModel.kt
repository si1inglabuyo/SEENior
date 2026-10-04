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
import com.pup.seenior.network.dto.SeniorDto
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

enum class AlertScreen {
    LOADING, ALL_CLEAR, LOAD_FAILED, DETAIL, ACKNOWLEDGED, CALL_SENIOR, LOCATION, DISPATCH,
    RESOLVED, HISTORY, HISTORY_DETAIL
}

/** One row in the history list: an alert plus the senior it belongs to. */
data class AlertHistoryItem(val alert: AlertDto, val senior: SeniorDto)

/** A closed alert's summary, kept only for the Resolved screen right after resolving it. */
data class ResolvedSummary(
    val alertShortId: String,
    val triggeredAt: String,
    val resolvedAt: String,
    val durationMinutes: Long,
    /** Who closed it, from the audit timeline — see [resolverName]. */
    val resolvedBy: String
)

/**
 * Drives the family Alerts tab. Watches every linked senior's alerts and shows the most
 * urgent open one (pending, acknowledged or escalated), or "All Clear" when nothing is open.
 */
class FamilyAlertsViewModel(application: Application) : AndroidViewModel(application) {
    // Starts at LOADING, never ALL_CLEAR, so the app doesn't claim the senior is safe before hearing from the server.
    var screen by mutableStateOf(AlertScreen.LOADING)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<FamilyError?>(null)
        private set

    var activeAlert by mutableStateOf<AlertDto?>(null)
        private set
    var activeSenior by mutableStateOf<ContactDto?>(null)
        private set
    var resolvedSummary by mutableStateOf<ResolvedSummary?>(null)
        private set

    /** Every fetched alert across all linked seniors, newest first. Refreshed on every [load],
     *  including background polls. */
    var history by mutableStateOf<List<AlertHistoryItem>>(emptyList())
        private set
    var selectedHistoryItem by mutableStateOf<AlertHistoryItem?>(null)
        private set

    private var actionInFlight = false

    private var pollJob: Job? = null

    // Set when the family member taps through from the Home popup, so the next refresh opens that alert.
    private var requestedSyncId: String? = null

    /** Asks the next [refresh] to open one specific alert, so tapping "View" on a popup doesn't land on a different, newer one. */
    fun focusAlert(syncId: String) {
        requestedSyncId = syncId
    }

    /** One-shot fetch that may claim the screen. Used on entry and by the retry button. */
    fun refresh(contacts: List<ContactDto>) {
        viewModelScope.launch { load(contacts, background = false) }
    }

    /** Re-fetches every [POLL_INTERVAL_MS] while the Alerts tab is resumed, so the screen doesn't sit on "All clear" while an alert is already on the server. */
    fun startPolling(contacts: List<ContactDto>) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var first = true
            while (isActive) {
                // Only the first fetch may take over the screen, and only when it is empty. Later
                // polls are silent so they don't reset a step the family member is on.
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
                val fetchedHistory = mutableListOf<AlertHistoryItem>()
                for (contact in contacts) {
                    val alerts = RetrofitClient.api.getAlerts(contact.senior.syncId, "Bearer $token")
                    fetchedHistory += alerts.map { AlertHistoryItem(it, contact.senior) }
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
                // Refreshed unconditionally so History is current when opened.
                history = fetchedHistory.sortedByDescending { it.alert.createdAt }
                // A requested alert outranks "newest open". Falls back if it has vanished.
                if (requestedAlert != null) {
                    bestAlert = requestedAlert
                    bestContact = requestedContact
                }

                if (mayClaimScreen(background)) {
                    // Consumed only when acted on, so a silent poll can't swallow it.
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
                    FamilyError.SessionExpired
                else FamilyError.Server(FamilyError.Action.LoadAlerts, e.code())
                if (mayClaimScreen(background)) screen = AlertScreen.LOAD_FAILED
            } catch (e: IOException) {
                error = FamilyError.Network(FamilyError.NetworkVariant.CheckConnection)
                if (mayClaimScreen(background)) screen = AlertScreen.LOAD_FAILED
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Whether this fetch may change what is on screen. A foreground fetch always may. A
     * background poll may only while the screen is just reporting; once the family member has
     * acknowledged or opened the dispatch form, the screen is theirs. RESOLVED counts as
     * reporting, so a finished summary can't block a live alert.
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
                    FamilyError.SessionExpired
                else FamilyError.Server(FamilyError.Action.Acknowledge, e.code())
            } catch (e: IOException) {
                error = FamilyError.Network()
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
                    FamilyError.SessionExpired
                else FamilyError.Server(FamilyError.Action.DispatchBarangay, e.code())
            } catch (e: IOException) {
                error = FamilyError.Network()
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
                    FamilyError.SessionExpired
                else FamilyError.Server(FamilyError.Action.Resolve, e.code())
            } catch (e: IOException) {
                error = FamilyError.Network()
            } finally {
                actionInFlight = false
            }
        }
    }

    /** The senior was fine and the detection was wrong. Closes the alert like [markResolved] but as a false alarm. */
    fun markFalseAlarm() {
        val alert = activeAlert ?: return
        val token = FamilySession.getToken(getApplication()) ?: return
        if (actionInFlight) return
        viewModelScope.launch {
            actionInFlight = true
            try {
                val closed = RetrofitClient.api.markAlertFalsePositive(alert.syncId, "Bearer $token")
                resolvedSummary = buildSummary(closed)
                activeAlert = closed
                screen = AlertScreen.RESOLVED
            } catch (e: HttpException) {
                error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    FamilyError.SessionExpired
                else FamilyError.Server(FamilyError.Action.FalseAlarm, e.code())
            } catch (e: IOException) {
                error = FamilyError.Network()
            } finally {
                actionInFlight = false
            }
        }
    }

    fun goTo(target: AlertScreen) {
        screen = target
    }

    /** Opens the read-only detail view for a tapped history tile. */
    fun openHistoryDetail(item: AlertHistoryItem) {
        selectedHistoryItem = item
        screen = AlertScreen.HISTORY_DETAIL
    }

    /** The incident-summary [markResolved] builds, for the history detail screen to use on any past alert. */
    fun summaryFor(alert: AlertDto): ResolvedSummary = buildSummary(alert)

    /** Called after leaving the Resolved screen. Goes back to All Clear without re-fetching. */
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
     * Who closed the alert, from the closing entry in `escalation_steps`. Either a family
     * member resolved it or the senior answered on their own phone; both are named. Falls back
     * to a dash for older alerts with no recorded name, rather than guessing.
     */
    private fun resolverName(alert: AlertDto): String =
        alert.escalationSteps
            ?.lastOrNull { it["step"] in CLOSING_STEPS }
            ?.get("by")
            ?.takeIf { it.isNotBlank() }
            ?: "—"

    companion object {
        private val OPEN_STATUSES = setOf("pending", "acknowledged", "escalated")

        /** Audit steps that close an incident, newest of which names who closed it. */
        private val CLOSING_STEPS = setOf("resolved_family", "false_positive_family", "self_cancelled_senior")

        /** Screens that only report the situation, so a poll may replace them. The rest are steps the family member is partway through. */
        private val PASSIVE_SCREENS = setOf(
            AlertScreen.LOADING,
            AlertScreen.ALL_CLEAR,
            AlertScreen.DETAIL,
            AlertScreen.LOAD_FAILED,
            AlertScreen.RESOLVED
        )

        /** Matches the Home tab's cadence, since both read the same endpoint. */
        private const val POLL_INTERVAL_MS = 20_000L
    }
}

/**
 * Parses a server timestamp and converts it to the device's time zone. The cloud stores UTC
 * with no offset, so reading it as local time made new alerts look hours old. Every display
 * of a server timestamp must go through here.
 */
fun parseServerTime(iso: String): ZonedDateTime? {
    // Defensive: the backend sends naive UTC today, but a tz-aware column shouldn't shift timestamps.
    runCatching { OffsetDateTime.parse(iso) }.getOrNull()?.let {
        return it.atZoneSameInstant(ZoneId.systemDefault())
    }
    return runCatching {
        LocalDateTime.parse(iso).atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
    }.getOrNull()
}

/**
 * Plain-language "why we're asking" text from Alert.triggerType, so families get the same
 * context as the senior. English only, because [FamilyAlertNotifier] builds notifications
 * outside any Composable and can't use [LocalFamilyCopy]. The in-app tab uses
 * [FamilyStrings.Copy.alertReasonText], which adds Filipino.
 */
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

/** Minutes between a server timestamp and now, clamped to 0. Feeds [FamilyStrings.Copy.relativeTimeAgo]. */
fun minutesAgo(iso: String): Long {
    val then = parseServerTime(iso) ?: return 0L
    return Duration.between(then, ZonedDateTime.now()).toMinutes().coerceAtLeast(0)
}

fun formatClockTime(iso: String): String {
    val then = parseServerTime(iso) ?: return ""
    return then.format(DateTimeFormatter.ofPattern("h:mm a"))
}
