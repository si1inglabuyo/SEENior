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
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.Senior
import com.pup.seenior.detection.AnomalySimulator
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import com.pup.seenior.network.dto.FamilyContactDto
import com.pup.seenior.ui.wellness.WellnessMessages
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

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
                is AnomalySimulator.Result.Triggered ->
                    "Detector flagged an anomaly (z = %.1f).".format(result.zScore)
                AnomalySimulator.Result.AlreadyActive ->
                    "An alert is already open — answer it first."
                AnomalySimulator.Result.NoBaseline ->
                    "No baseline for this time block yet, so there is nothing to compare against."
                AnomalySimulator.Result.NoSenior ->
                    "No senior profile found on this phone."
            }
        }
    }

    fun clearSimulationMessage() {
        simulationMessage = null
    }

    /**
     * SOS. Writes the alert directly instead of going through the detector: this is a conscious
     * request for help, not a statistical deviation, so there is nothing to score. Always high
     * risk, and works from day one regardless of baseline status (CLAUDE.md §6).
     */
    fun sendSos() {
        val current = senior ?: return
        viewModelScope.launch {
            val onboarding = db.seniorOnboardingDao().getBySeniorId(current.seniorId) ?: return@launch
            val now = System.currentTimeMillis()
            val timeBlock = SeedBaselineGenerator
                .resolveTimeBlock(now, onboarding.wakeTime, onboarding.sleepTime)
                .name.lowercase()

            // Reuse an already-open SOS rather than stacking duplicates if the swipe is repeated.
            if (db.alertDao().getActiveAlert(current.seniorId, "sos") != null) return@launch

            db.alertDao().insert(
                Alert(
                    seniorId = current.seniorId,
                    syncId = UUID.randomUUID().toString(),
                    triggerType = "sos",
                    riskLevel = "high",
                    timeBlock = timeBlock,
                    // No z-score exists for a button press; the column is Median-MAD's.
                    deviationScore = null
                )
            )
        }
    }

    private companion object {
        const val LOW_BATTERY_PERCENT = 20
    }
}
