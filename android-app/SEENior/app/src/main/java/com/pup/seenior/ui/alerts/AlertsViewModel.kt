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

    /**
     * The scored block behind each `ml_flag` alert, so the details can show which signals leaned
     * the worrying way. Kept on the phone for 14 days only, so older alerts have none.
     */
    var mlFlagDays by mutableStateOf<Map<Int, com.pup.seenior.database.entities.DailyAggregate>>(emptyMap())
        private set

    /**
     * The Routine Fingerprint, read so the details screen can say whether an alert is one of
     * those currently loosening the trigger (see [com.pup.seenior.ui.routine.SeniorRoutineModel.care]).
     */
    var baseline by mutableStateOf<List<com.pup.seenior.database.entities.Baseline>>(emptyList())
        private set

    fun start() {
        viewModelScope.launch {
            val seniorId = db.seniorDao().getOnboardedSenior()?.seniorId ?: return@launch
            db.alertDao().getAllBySenior(seniorId).collectLatest { list ->
                alerts = list
                baseline = db.baselineDao().getAllBySeniorOnce(seniorId)
                loadMlFlagScores(seniorId, list)
            }
        }
    }

    /** Fetched once per alert and cached: the aggregate row is never rewritten after the night it was scored. */
    private suspend fun loadMlFlagScores(seniorId: Int, list: List<Alert>) {
        val unfetched = list.filter { it.triggerType == "ml_flag" && it.alertId !in mlFlagScores }
        if (unfetched.isEmpty()) return
        val rows = unfetched.mapNotNull { alert ->
            db.dailyAggregateDao()
                .getMostRecentBefore(seniorId, alert.timeBlock, alert.triggeredAt)
                ?.let { alert.alertId to it }
        }
        mlFlagDays = mlFlagDays + rows
        mlFlagScores = mlFlagScores + rows.mapNotNull { (id, row) -> row.isolationForestScore?.let { id to it } }
    }
}
