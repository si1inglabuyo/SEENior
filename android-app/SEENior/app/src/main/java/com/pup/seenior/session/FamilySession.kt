package com.pup.seenior.session

import android.content.Context
import androidx.core.content.edit

/** Persists the family instance's login. A family account can monitor up to 3 seniors
 *  (MAX_LINKED_SENIORS in FamilySeniorsViewModel) — the list itself always comes live
 *  from GET /contacts/me, not cached locally, so only the token is stored here.
 *  The app is single-module and plays either role; this is only used on the family side. */
object FamilySession {
    private const val PREFS = "family_session"
    private const val KEY_TOKEN = "token"

    fun saveToken(context: Context, token: String) {
        context.prefs().edit { putString(KEY_TOKEN, token) }
    }

    fun getToken(context: Context): String? = context.prefs().getString(KEY_TOKEN, null)
    fun isPaired(context: Context): Boolean = getToken(context) != null

    fun clear(context: Context) {
        context.prefs().edit { clear() }
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
