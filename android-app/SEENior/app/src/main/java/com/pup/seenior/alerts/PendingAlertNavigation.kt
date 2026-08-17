package com.pup.seenior.alerts

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide signal that the user arrived by tapping an alert notification, so the family
 * dashboard can open that specific alert instead of whatever it would otherwise show.
 *
 * Deliberately mirrors [com.pup.seenior.session.SessionState]: a small observable object
 * rather than navigation arguments threaded through SeniorNavGraph, because the trigger
 * comes from an Activity Intent — which may arrive either at cold start or, thanks to
 * `launchMode="singleTop"`, at onNewIntent on an Activity already composed. Nav arguments
 * cannot express the second case without rebuilding the graph mid-emergency.
 *
 * Single-use by design: [consume] hands the id over exactly once, so returning to the
 * Alerts tab later does not silently re-open an alert the user already dealt with.
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
            // Cleared off the Intent itself as well: without this, any later recreation
            // (a rotation, a process restart) would replay the same extra and yank the
            // user back to this alert long after they moved on.
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
