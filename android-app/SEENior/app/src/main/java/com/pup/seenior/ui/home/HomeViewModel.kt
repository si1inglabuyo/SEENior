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
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.Senior
import com.pup.seenior.detection.AnomalySimulator
import com.pup.seenior.detection.FallDetector
import com.pup.seenior.detection.FallSimulator
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import com.pup.seenior.network.dto.FamilyContactDto
import com.pup.seenior.ui.wellness.WellnessMessages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * What Home says about an alert the senior has already raised, once the prompt has closed.
 *
 * The two states are not cosmetic variants of each other. [Waiting] means the alert exists only
 * on this phone; nobody has been told. [Delivered] means the cloud accepted it and the family
 * app can see it. Collapsing them would make the app claim help was summoned when it was sitting
 * in a queue — the exact false reassurance the offline path used to give.
 */
sealed interface HelpDelivery {
    val alert: Alert

    /** Raised and recorded here, not yet accepted by the cloud. */
    data class Waiting(override val alert: Alert) : HelpDelivery

    /** The cloud row exists, so the family app can see it. */
    data class Delivered(override val alert: Alert) : HelpDelivery
}

/**
 * Backs the senior's Home tab (`designs/senior/home_screen/`) and decides when the wellness
 * prompt takes over the screen.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SeniorAppDatabase.getInstance(application)
    private val cloudSync = SeniorCloudSync(db)

    var senior by mutableStateOf<Senior?>(null)
        private set
    var language by mutableStateOf(WellnessMessages.ENGLISH)
        private set
    var batteryPercent by mutableStateOf(100)
        private set
    var simulationMessage by mutableStateOf<String?>(null)
        private set

    /** Who the SOS screen says it will alert. Best-effort: an empty list still shows the
     *  barangay tier, which is available regardless of whether any family is linked. */
    var willAlertContacts by mutableStateOf<List<FamilyContactDto>>(emptyList())
        private set

    private var openAlerts by mutableStateOf<List<Alert>>(emptyList())

    /**
     * Alerts the senior has already responded to on this screen.
     *
     * Answering "I need help" escalates but deliberately leaves the alert `pending` (that IS the
     * awaiting-family state), so it never leaves [com.pup.seenior.database.dao.AlertDao
     * .getUnacknowledgedAlerts] and the prompt would otherwise reappear the instant it closed.
     * Kept in memory rather than the DB because it is a property of this screen session, not of
     * the alert — after a restart the alert is genuinely still open and worth showing again.
     */
    private var answeredThisSession by mutableStateOf(emptySet<Int>())

    /**
     * Ticks so [helpDelivery] can retire a delivery confirmation on time.
     *
     * Everything else on this screen changes only when the database does, and Room re-emits for
     * free. "Delivered twenty minutes ago" becoming "delivered thirty-one minutes ago" is the one
     * transition no write accompanies, so it needs a clock of its own.
     */
    private var now by mutableStateOf(System.currentTimeMillis())

    /**
     * The alert the prompt is currently showing.
     *
     * Latched rather than recomputed from [openAlerts] on every emission. "I'm safe" sets the
     * status to self_cancelled, which immediately removes the alert from the query feeding
     * [openAlerts] — so a derived value would tear the prompt down the instant it was answered
     * and the senior would never see the acknowledgement. The prompt keeps its alert until it
     * reports back through [onAlertAnswered].
     */
    private var handling by mutableStateOf<Alert?>(null)

    /** The alert currently owed a response, if any. */
    val activeAlert: Alert?
        get() = handling

    /**
     * What Home reports about help the senior has already asked for, or null when there is
     * nothing to report.
     *
     * Undelivered outranks delivered, always: if anything at all is still stuck on this phone,
     * that is the fact the senior needs, even when a later alert did get through.
     *
     * [HelpDelivery.Waiting] is never retired on age. It is an unkept promise, and it stays on
     * screen until it becomes true. [HelpDelivery.Delivered] is retired after
     * [DELIVERED_DISPLAY_MILLIS] — long enough to be read and believed, short enough that Home
     * does not permanently advertise an old incident. (It would otherwise never clear at all:
     * the family resolving in the cloud is not synced back to this device yet.)
     */
    val helpDelivery: HelpDelivery?
        get() {
            val escalated = openAlerts.filter { AlertEscalator.hasEscalatedToFamily(it) }
            escalated.filterNot { it.isSynced }.maxByOrNull { it.triggeredAt }
                ?.let { return HelpDelivery.Waiting(it) }
            return escalated
                .filter { alert ->
                    AlertEscalator.deliveredAt(alert)
                        ?.let { now - it <= DELIVERED_DISPLAY_MILLIS } == true
                }
                .maxByOrNull { it.triggeredAt }
                ?.let { HelpDelivery.Delivered(it) }
        }

    val firstName: String
        get() = senior?.firstName ?: ""

    val fullName: String
        get() = senior?.let { "${it.firstName} ${it.lastName}".trim() } ?: ""

    val barangay: String
        get() = senior?.barangay.orEmpty()

    /** Monitoring degrades on a dying battery, so the status card says so rather than claiming
     *  everything is fine (`Senior Dashboard - With Family-3.png`). */
    val isMonitoringAtRisk: Boolean
        get() = batteryPercent <= LOW_BATTERY_PERCENT

    fun start() {
        viewModelScope.launch {
            val loaded = db.seniorDao().getOnboardedSenior() ?: return@launch
            senior = loaded
            db.seniorOnboardingDao().getBySeniorId(loaded.seniorId)?.let {
                language = it.languagePreference
            }
            refreshBattery()
            loadWillAlertContacts()
            launch {
                while (true) {
                    delay(DELIVERY_TICK_MILLIS)
                    now = System.currentTimeMillis()
                }
            }
            db.alertDao().getUnacknowledgedAlerts(loaded.seniorId).collectLatest { alerts ->
                openAlerts = alerts
                if (handling == null) handling = nextUnanswered()
            }
        }
    }

    /** Deliberately silent on failure: this only populates a reassurance list on the SOS screen,
     *  and a network hiccup must never stop SOS itself from working. */
    private suspend fun loadWillAlertContacts() {
        try {
            val syncId = cloudSync.withSyncIdOrNull() ?: return
            willAlertContacts = RetrofitClient.api.getFamilyContacts(syncId)
        } catch (e: Exception) {
            willAlertContacts = emptyList()
        }
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

    /** Called by the prompt once it is finished with the current alert, whichever way it ended.
     *  Moves straight on to the next open alert if there is one. */
    fun onAlertAnswered() {
        val done = handling ?: return
        answeredThisSession = answeredThisSession + done.alertId
        handling = nextUnanswered()
    }

    private fun nextUnanswered(): Alert? =
        openAlerts.firstOrNull { it.alertId !in answeredThisSession }

    /**
     * Demo trigger. Feeds one synthetic reading through the real Median-MAD detector — see
     * [AnomalySimulator] for why this is injection rather than a fabricated alert.
     */
    fun simulateAnomaly() {
        viewModelScope.launch {
            simulationMessage = when (val result = AnomalySimulator.simulateProlongedInactivity(db)) {
                is AnomalySimulator.Result.Triggered -> {
                    result.alert?.let { AlertResponder.onAlertCreated(getApplication(), db, it) }
                    "Detector flagged an anomaly (z = %.1f).".format(result.zScore)
                }
                AnomalySimulator.Result.AlreadyActive ->
                    "An alert is already open — answer it first."
                AnomalySimulator.Result.NoBaseline ->
                    "No baseline for this time block yet, so there is nothing to compare against."
                AnomalySimulator.Result.NoSenior ->
                    "No senior profile found on this phone."
            }
        }
    }

    /**
     * Demo trigger for Layer 0. Replays a synthetic fall through the real [FallDetector] with
     * production thresholds — nothing here can produce an alert the live sensor stream would not
     * have produced from the same motion.
     */
    fun simulateFall() {
        viewModelScope.launch {
            simulationMessage = when (FallSimulator.simulate(getApplication(), db)) {
                is FallSimulator.Result.Raised ->
                    "Fall signature confirmed: free fall, impact, then no movement."
                FallSimulator.Result.NotConfirmed ->
                    "The detector did not confirm a fall from that motion."
                FallSimulator.Result.AlreadyActive ->
                    "A fall alert is already open — answer it first."
                FallSimulator.Result.NoSenior ->
                    "No senior profile found on this phone."
            }
        }
    }

    fun clearSimulationMessage() {
        simulationMessage = null
    }

    /**
     * SOS. Raised directly instead of going through a detector: this is a conscious request for
     * help, not a statistical deviation, so there is nothing to score. Always high risk, and
     * works from day one regardless of baseline status (CLAUDE.md §6).
     *
     * [AlertResponder.raise] returns null when an SOS is already open, so a repeated swipe joins
     * the alert already in flight rather than stacking duplicates — and lands on that alert's
     * prompt, so the swipe always leads somewhere.
     */
    fun sendSos() {
        viewModelScope.launch {
            if (AlertResponder.raise(getApplication(), db, "sos", "high") != null) return@launch

            // Inside the dedupe window, so nothing was raised. Doing nothing at all is the worst
            // available answer to a senior asking for help: from their side a deduplicated swipe
            // and a broken button look identical. Show them the alert that is already open — it
            // is the screen where they can answer, self-cancel, or read that help is on its way.
            openAlerts.filter { it.triggerType == "sos" }
                .maxByOrNull { it.triggeredAt }
                ?.let { existing ->
                    // It may have been answered earlier this session, which is what let them back
                    // to Home to swipe again; clear that so the prompt does not skip straight
                    // past it when it closes.
                    answeredThisSession = answeredThisSession - existing.alertId
                    handling = existing
                }
        }
    }

    private companion object {
        const val LOW_BATTERY_PERCENT = 20

        /**
         * How long the delivery confirmation stays on Home.
         *
         * Half an hour: long enough that a senior who put the phone down after pressing SOS
         * still finds the answer when they pick it up, short enough that Home is not permanently
         * reporting an incident that is over. There is no better signal to end on yet -- the
         * family resolving the alert in the cloud is never synced back to this device.
         */
        const val DELIVERED_DISPLAY_MILLIS = 30L * 60 * 1000

        /** Coarse on purpose. It only has to retire a half-hour banner, and this wakes the
         *  main thread for as long as the Home tab is open. */
        const val DELIVERY_TICK_MILLIS = 30_000L
    }
}
