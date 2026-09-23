package com.pup.seenior.ui.alerts

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Backs the senior's Alerts tab: every alert this device has ever raised, newest first.
 *
 * Deliberately unfiltered here — narrowing to "last 14 days" and excluding low-risk `logged`
 * rows (CLAUDE.md §5: an anomaly nobody was ever told about) is the screen's job, not the
 * query's, so the same list can also answer "has anything ever happened" if that's ever needed.
 */
class AlertsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeniorAppDatabase.getInstance(application)

    var alerts by mutableStateOf<List<Alert>>(emptyList())
        private set

    /**
     * `ml_flag` alerts' Isolation Forest path-length score, keyed by alert id — recovered from
     * [com.pup.seenior.database.entities.DailyAggregate] since `Alert.deviationScore` is null for
     * this trigger type (CLAUDE.md §8; see [com.pup.seenior.database.dao.DailyAggregateDao
     * .getMostRecentBefore]'s KDoc for why). Absent from Layer 1 alerts entirely — they show
     * `Alert.deviationScore` directly, no lookup needed.
     */
    var mlFlagScores by mutableStateOf<Map<Int, Double>>(emptyMap())
        private set

    fun start() {
        viewModelScope.launch {
            val seniorId = db.seniorDao().getOnboardedSenior()?.seniorId ?: return@launch
            db.alertDao().getAllBySenior(seniorId).collectLatest { list ->
                alerts = list
                loadMlFlagScores(seniorId, list)
            }
        }
    }

    /** Fetched once per alert and cached forever: the aggregate row it reads is never rewritten
     *  after the night it was scored, so there is nothing to refresh. */
    private suspend fun loadMlFlagScores(seniorId: Int, list: List<Alert>) {
        val unfetched = list.filter { it.triggerType == "ml_flag" && it.alertId !in mlFlagScores }
        if (unfetched.isEmpty()) return
        val found = unfetched.mapNotNull { alert ->
            db.dailyAggregateDao()
                .getMostRecentBefore(seniorId, alert.timeBlock, alert.triggeredAt)
                ?.isolationForestScore
                ?.let { alert.alertId to it }
        }
        mlFlagScores = mlFlagScores + found
    }
}
