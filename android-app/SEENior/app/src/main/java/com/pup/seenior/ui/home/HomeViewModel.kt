package com.pup.seenior.ui.home

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.alerts.AlertEscalator
import com.pup.seenior.alerts.AlertResponder
import com.pup.seenior.alerts.EscalationScheduler
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.Contact
import com.pup.seenior.database.entities.Senior
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import com.pup.seenior.ui.onboarding.OnboardingOptions
import com.pup.seenior.ui.wellness.WellnessMessages
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * What Home says about an alert the senior has already raised. [Waiting] means it exists
 * only on this phone and nobody has been told; [Delivered] means the cloud accepted it.
 * They are kept apart so the app never claims help was summoned when it is still queued.
 */
sealed interface HelpDelivery {
    val alert: Alert

    /** Raised and recorded here, not yet accepted by the cloud. */
    data class Waiting(override val alert: Alert) : HelpDelivery

    /** The cloud row exists, so the family app can see it. */
    data class Delivered(override val alert: Alert) : HelpDelivery
}

/** Backs the senior's Home tab and decides when the wellness prompt takes over the screen. */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SeniorAppDatabase.getInstance(application)
    private val cloudSync = SeniorCloudSync(db)

    var senior by mutableStateOf<Senior?>(null)
        private set
    var language by mutableStateOf(WellnessMessages.ENGLISH)
        private set
    var batteryPercent by mutableStateOf(100)
        private set

    /** Who the SOS screen says it will alert. Read from the local Contacts table so SOS works
     *  offline. An empty list still shows the barangay tier. */
    var willAlertContacts by mutableStateOf<List<Contact>>(emptyList())
        private set

    /** Whether an empty [willAlertContacts] is an answer or just an absence. False until the
     *  family list has been read from the cloud at least once, so the SOS screen never tells
     *  a senior nobody will be called just because the phone couldn't reach the server. */
    var willAlertContactsKnown by mutableStateOf(false)
        private set

    private var openAlerts by mutableStateOf<List<Alert>>(emptyList())

    /**
     * Alerts the senior has already responded to on this screen. Answering "I need help"
     * leaves the alert `pending`, so without this the prompt would reappear immediately. Kept
     * in memory because it only applies to this session.
     */
    private var answeredThisSession by mutableStateOf(emptySet<Int>())

    /**
     * The alert the prompt is showing. Latched, not derived from [openAlerts]: "I'm safe"
     * removes the alert from that query, which would tear the prompt down before the senior
     * saw the acknowledgement. It is released through [onAlertAnswered].
     */
    private var handling by mutableStateOf<Alert?>(null)

    /** Guards against a second tap while the first is still writing and calling the server. */
    private var standingDownAlertId by mutableStateOf<Int?>(null)

    /** The alert currently owed a response, if any. */
    val activeAlert: Alert?
        get() = handling

    /**
     * What Home reports about help the senior has asked for, or null if none. Undelivered
     * outranks delivered. Neither retires on a timer (a timeout once showed "You're Safe" over
     * an open HIGH alert); it clears when the senior uses "I'm Fine Now" ([standDown]) or a
     * family contact or the barangay closes it ([syncClosedAlerts]).
     */
    val helpDelivery: HelpDelivery?
        get() {
            val escalated = openAlerts.filter { AlertEscalator.hasEscalatedToFamily(it) }
            escalated.filterNot { it.isSynced }.maxByOrNull { it.triggeredAt }
                ?.let { return HelpDelivery.Waiting(it) }
            return escalated
                .filter { AlertEscalator.deliveredAt(it) != null }
                .maxByOrNull { it.triggeredAt }
                ?.let { HelpDelivery.Delivered(it) }
        }

    val firstName: String
        get() = senior?.firstName ?: ""

    val fullName: String
        get() = senior?.let { "${it.firstName} ${it.lastName}".trim() } ?: ""

    val barangay: String
        get() = senior?.barangay.orEmpty()

    /**
     * Whether to hide the pairing tabs. Presentation only: the server decides escalation from
     * the contact rows, so a stale value can only misplace a tab, never misroute an alert.
     */
    val livesAlone: Boolean
        get() = senior?.livingArrangement == OnboardingOptions.LIVING_ALONE

    /** Monitoring degrades on a dying battery, so the status card says so. */
    val isMonitoringAtRisk: Boolean
        get() = batteryPercent <= LOW_BATTERY_PERCENT

    fun start() {
        viewModelScope.launch {
            val loaded = db.seniorDao().getOnboardedSenior() ?: return@launch
            senior = loaded
            db.seniorOnboardingDao().getBySeniorId(loaded.seniorId)?.let {
                language = it.languagePreference
            }
            // Collected, not read once, because Profile -> Language saves immediately and the
            // UI must change with it. Launched separately because the alert Flow collected at
            // the end of this method never returns.
            launch {
                db.seniorOnboardingDao().observeLanguagePreference(loaded.seniorId)
                    .collect { preference -> preference?.let { language = it } }
            }
            // Same reason as the language collector above: the alert Flow below never returns.
            launch {
                while (true) {
                    syncClosedAlerts()
                    delay(CLOSED_ALERTS_POLL_MS)
                }
            }
            refreshBattery()
            loadWillAlertContacts()
            restoreFamilyTabsIfPaired()
            db.alertDao().getUnacknowledgedAlerts(loaded.seniorId).collectLatest { alerts ->
                openAlerts = alerts
                if (handling == null) handling = nextUnanswered()
            }
        }
    }

    /**
     * Retires an alert on this phone once a family contact or the barangay has closed it in
     * the cloud. Only asks the server while an alert has actually reached it. Best effort:
     * offline or on error the card stays until the next pass. The local row is closed the way
     * a self-cancel closes it.
     */
    private suspend fun syncClosedAlerts() {
        val open = openAlerts.filter { it.isSynced }
        if (open.isEmpty()) return
        try {
            val seniorSyncId = cloudSync.withSyncIdOrNull() ?: return
            val closed = RetrofitClient.api.getClosedAlerts(seniorSyncId).associate { it.syncId to it.status }
            val now = System.currentTimeMillis()
            open.forEach { alert ->
                val status = closed[alert.syncId] ?: return@forEach
                db.alertDao().updateStatus(alert.alertId, status, now)
                EscalationScheduler.cancel(getApplication(), alert.alertId)
            }
        } catch (e: Exception) {
            // Offline or a server error: nothing to undo, try again on the next pass.
        }
    }

    /**
     * Fills the SOS screen's "Will Alert" list, cache first. The local Contacts table is read
     * before the network, and only a successful cloud answer replaces it. Silent on failure,
     * so a network problem never stops SOS or empties the list.
     */
    private suspend fun loadWillAlertContacts() {
        val seniorId = senior?.seniorId ?: return

        val cached = db.contactDao().getFamilyContactsOnce(seniorId)
        if (cached.isNotEmpty()) {
            willAlertContacts = cached
            // Rows can only be here because a previous fetch put them here.
            willAlertContactsKnown = true
        }

        try {
            val syncId = cloudSync.withSyncIdOrNull() ?: return
            val fetched = RetrofitClient.api.getFamilyContacts(syncId)
            val rows = fetched.map { contact ->
                Contact(
                    seniorId = seniorId,
                    name = contact.fullName.orEmpty(),
                    phoneNumber = contact.phone.orEmpty(),
                    contactType = contact.contactType,
                    relationshipLabel = contact.relationshipLabel
                )
            }
            db.contactDao().replaceFamilyContacts(seniorId, rows)
            // Re-read rather than using `rows`, so the list carries the ids Room assigned.
            willAlertContacts = db.contactDao().getFamilyContactsOnce(seniorId)
            willAlertContactsKnown = true
        } catch (e: Exception) {
            // The cache above, or the empty list, both already stand. Nothing to undo.
        }
    }

    /**
     * Puts the Invite and Contacts tabs back as soon as a family member pairs, so the senior
     * doesn't have to change a setting first. Only in this direction: removing the last
     * contact doesn't hide the tabs again.
     */
    private suspend fun restoreFamilyTabsIfPaired() {
        val current = senior ?: return
        if (current.livingArrangement != OnboardingOptions.LIVING_ALONE) return
        if (willAlertContacts.isEmpty()) return

        db.seniorDao().updateLivingArrangement(
            current.seniorId,
            OnboardingOptions.LIVING_WITH_FAMILY
        )
        // Also updates the in-memory copy, which isn't re-read until the next app start.
        senior = current.copy(livingArrangement = OnboardingOptions.LIVING_WITH_FAMILY)
    }

    fun refreshBattery() {
        val status: Intent? = getApplication<Application>().registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) batteryPercent = (level * 100) / scale
    }

    /** Called by the prompt when it is finished with the current alert. Moves on to the next open one. */
    fun onAlertAnswered() {
        val done = handling ?: return
        answeredThisSession = answeredThisSession + done.alertId
        handling = nextUnanswered()
    }

    private fun nextUnanswered(): Alert? =
        openAlerts.firstOrNull { it.alertId !in answeredThisSession }

    /**
     * SOS. Raised directly, since it is a conscious request for help and there is nothing to
     * score. Always high risk and works from day one (spec section 6). [AlertResponder.raise]
     * returns null when an SOS is already open, so a repeated swipe lands on that alert's prompt.
     */
    fun sendSos() {
        viewModelScope.launch {
            if (AlertResponder.raise(getApplication(), db, "sos", "high") != null) return@launch

            // Deduplicated, so nothing was raised. Show the alert already open instead of doing
            // nothing, which would look like a broken button.
            openAlerts.filter { it.triggerType == "sos" }
                .maxByOrNull { it.triggeredAt }
                ?.let { existing ->
                    // Clear any earlier answer so the prompt doesn't skip past it when it closes.
                    answeredThisSession = answeredThisSession - existing.alertId
                    handling = existing
                }
        }
    }

    /**
     * "I'm fine now" from Home: withdraws an alert that has already reached the family. This
     * card stays up for half an hour, longer than the prompt's few seconds. The status write
     * drops the alert from [openAlerts] and the card goes with it.
     */
    fun standDown(alert: Alert) {
        if (standingDownAlertId != null) return
        standingDownAlertId = alert.alertId

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.alertDao().updateEscalationSteps(
                alert.alertId,
                AlertEscalator.appendStep(alert.escalationSteps, "self_cancelled", now)
            )
            db.alertDao().updateStatus(alert.alertId, "self_cancelled", now)
            // Nothing is owed on it any more, including a queued delivery retry.
            EscalationScheduler.cancel(getApplication(), alert.alertId)
            AlertEscalator.cancelInCloud(db, alert.alertId)
            standingDownAlertId = null
        }
    }

    private companion object {
        const val LOW_BATTERY_PERCENT = 20

        /** How often Home asks whether an open alert has been closed elsewhere. */
        const val CLOSED_ALERTS_POLL_MS = 30_000L
    }
}
