package com.pup.seenior.alerts

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide signal that the user arrived by tapping an alert notification, so the family
 * dashboard opens that alert. Mirrors [com.pup.seenior.session.SessionState]: a small
 * observable object, because the Intent can arrive at cold start or, with singleTop, at
 * onNewIntent on an already composed Activity, which nav arguments can't express. Single use:
 * [consume] hands the id over once, so the Alerts tab doesn't reopen a handled alert.
 */
object PendingAlertNavigation {

    /** Set when a notification tap is pending, cleared by [consume]. */
    var alertSyncId by mutableStateOf<String?>(null)
        private set

    /** Records the tap if [intent] came from an alert notification. Safe on any intent. */
    fun captureFrom(intent: Intent?) {
        val syncId = intent?.getStringExtra(FamilyAlertNotifier.EXTRA_ALERT_SYNC_ID)
        if (!syncId.isNullOrBlank()) {
            alertSyncId = syncId
            // Cleared off the Intent too, or a later recreation (rotation, restart) would replay the extra.
            intent.removeExtra(FamilyAlertNotifier.EXTRA_ALERT_SYNC_ID)
        }
    }

    /** Returns the pending alert id once, then forgets it. */
    fun consume(): String? {
        val pending = alertSyncId
        alertSyncId = null
        return pending
    }
}
