package com.pup.seenior.session

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject

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

    /**
     * True when a stored token is still live. A token merely *existing* is not proof of a
     * usable session — the backend expires it (ACCESS_TOKEN_EXPIRE_MINUTES, 60 by default)
     * and there is no refresh flow, so routing on "a token string is present" lands the user
     * in the dashboard with a dead credential and every call failing 401.
     *
     * An expired token is dropped here so the app stops re-entering the dashboard with it.
     * A token whose `exp` cannot be read is treated as live and left for the server to reject —
     * a parsing quirk must never lock someone out of their own account.
     */
    fun hasLiveSession(context: Context): Boolean {
        val token = getToken(context) ?: return false
        val expiry = expiryEpochSeconds(token)
        if (expiry != null && expiry <= System.currentTimeMillis() / 1000) {
            clear(context)
            return false
        }
        return true
    }

    fun clear(context: Context) {
        context.prefs().edit { clear() }
    }

    /** Reads the `exp` claim out of the JWT payload (segment 2 of header.payload.signature).
     *  Null when the token is malformed or carries no expiry. */
    private fun expiryEpochSeconds(token: String): Long? {
        val payload = token.split(".").getOrNull(1) ?: return null
        // JWT segments are base64url with the padding stripped; restore it rather than depend
        // on how lenient the platform decoder happens to be.
        val padded = payload.padEnd((payload.length + 3) / 4 * 4, '=')
        return runCatching {
            val json = String(
                Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP),
                Charsets.UTF_8
            )
            JSONObject(json).optLong("exp").takeIf { it > 0L }
        }.getOrNull()
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
