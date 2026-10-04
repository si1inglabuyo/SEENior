package com.pup.seenior.session

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject

/** Persists the family login. Only the token is stored; the linked seniors (up to
 *  MAX_LINKED_SENIORS) always come live from GET /contacts/me. Used only on the family side. */
object FamilySession {
    private const val PREFS = "family_session"
    private const val KEY_TOKEN = "token"

    fun saveToken(context: Context, token: String) {
        context.prefs().edit { putString(KEY_TOKEN, token) }
    }

    fun getToken(context: Context): String? = context.prefs().getString(KEY_TOKEN, null)

    /**
     * True when a stored token is still live. A token existing isn't proof of a usable session,
     * since the backend expires it (60 minutes by default) with no refresh flow. An expired
     * token is dropped here. One whose `exp` can't be read is treated as live and left for the
     * server to reject, so a parsing quirk never locks someone out.
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

    /** Reads the `exp` claim from the JWT payload. Null if the token is malformed or has no expiry. */
    private fun expiryEpochSeconds(token: String): Long? {
        val payload = token.split(".").getOrNull(1) ?: return null
        // JWT segments are base64url without padding; restore it instead of relying on the platform decoder.
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
