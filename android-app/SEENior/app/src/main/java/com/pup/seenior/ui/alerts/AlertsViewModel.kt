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
 * Backs the senior's Alerts tab: every alert this device has raised, newest first. Unfiltered
 * here; narrowing to 14 days and excluding low-risk `logged` rows is the screen's job.
 */
class AlertsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeniorAppDatabase.getInstance(application)

    var alerts by mutableStateOf<List<Alert>>(emptyList())
        private set

    /**
     * `ml_flag` alerts' Isolation Forest score by alert id, recovered from
     * [com.pup.seenior.database.entities.DailyAggregate] since `Alert.deviationScore` is null for
     * that trigger type (see [com.pup.seenior.database.dao.DailyAggregateDao.getMostRecentBefore]).
     * Layer 1 alerts show `Alert.deviationScore` directly.
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

    /** Fetched once per alert and cached: the aggregate row is never rewritten after the night it was scored. */
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
