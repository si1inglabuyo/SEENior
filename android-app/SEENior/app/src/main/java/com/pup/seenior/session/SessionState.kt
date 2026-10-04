package com.pup.seenior.session

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import retrofit2.HttpException

/**
 * App-wide signal that the family login has died, so a 401 becomes a route back to Log In.
 * The access token expires (60 minutes server-side) with no refresh flow, and before this
 * every family ViewModel showed "server error 401" and left the user stuck. A 401 always
 * means the credential is finished. Observed by FamilyDashboard, which owns the navigation.
 */
object SessionState {

    /** Raised on any 401, lowered by consume() once the dashboard has navigated away. */
    var expired by mutableStateOf(false)
        private set

    /**
     * Clears the stored token and flags the session as expired when [e] is a 401. Returns true
     * when it handled the exception, so callers can pick the message:
     *
     *     error = if (SessionState.handleIfUnauthorized(getApplication(), e))
     *         FamilyError.SessionExpired
     *     else FamilyError.Server(FamilyError.Action.LoadAlerts, e.code())
     */
    fun handleIfUnauthorized(context: Context, e: HttpException): Boolean {
        if (e.code() != HTTP_UNAUTHORIZED) return false
        FamilySession.clear(context.applicationContext)
        expired = true
        return true
    }

    /** Called once the log-out navigation has been performed, so the next login starts clean. */
    fun consume() {
        expired = false
    }

    private const val HTTP_UNAUTHORIZED = 401
}
