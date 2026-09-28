package com.pup.seenior.session

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import retrofit2.HttpException

/**
 * App-wide signal that the family login has died, so a 401 becomes a route back to Log In
 * instead of a dead end.
 *
 * Why this exists: the access token expires (60 minutes by default server-side) and there is
 * no refresh flow. Before this, every family ViewModel caught the resulting HttpException and
 * rendered "…(server error 401)", leaving the user stuck on a dashboard whose only escape was
 * the Profile tab's Log Out button. A 401 has no recoverable meaning here — expired token,
 * rotated SECRET_KEY, or a re-created database all mean the same thing to the app: this
 * credential is finished, get a new one.
 *
 * Observed by FamilyDashboard, which owns the navigation callback.
 */
object SessionState {

    /** Raised on any 401, lowered by consume() once the dashboard has navigated away. */
    var expired by mutableStateOf(false)
        private set

    /**
     * Clears the stored token and flags the session as expired when [e] is a 401.
     * Returns true when it handled the exception, so callers can pick the right message:
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
