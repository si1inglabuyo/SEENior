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

    fun start() {
        viewModelScope.launch {
            val seniorId = db.seniorDao().getOnboardedSenior()?.seniorId ?: return@launch
            db.alertDao().getAllBySenior(seniorId).collectLatest { alerts = it }
        }
    }
}
