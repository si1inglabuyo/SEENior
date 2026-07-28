package com.pup.seenior.session

import android.content.Context
import androidx.core.content.edit

/** Persists the family instance's login + which senior it's monitoring.
 *  The app is single-module and plays either role; this is only used on the family side. */
object FamilySession {
    private const val PREFS = "family_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_SENIOR_SYNC_ID = "senior_sync_id"
    private const val KEY_SENIOR_NAME = "senior_name"
    private const val KEY_RELATIONSHIP = "relationship"

    fun save(
        context: Context,
        token: String,
        seniorSyncId: String,
        seniorName: String,
        relationship: String
    ) {
        context.prefs().edit {
            putString(KEY_TOKEN, token)
            putString(KEY_SENIOR_SYNC_ID, seniorSyncId)
            putString(KEY_SENIOR_NAME, seniorName)
            putString(KEY_RELATIONSHIP, relationship)
        }
    }

    fun getToken(context: Context): String? = context.prefs().getString(KEY_TOKEN, null)
    fun getSeniorSyncId(context: Context): String? = context.prefs().getString(KEY_SENIOR_SYNC_ID, null)
    fun getSeniorName(context: Context): String? = context.prefs().getString(KEY_SENIOR_NAME, null)
    fun getRelationship(context: Context): String? = context.prefs().getString(KEY_RELATIONSHIP, null)
    fun isPaired(context: Context): Boolean = getToken(context) != null

    fun clear(context: Context) {
        context.prefs().edit { clear() }
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
