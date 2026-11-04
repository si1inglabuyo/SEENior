package com.pup.seenior.afterhours

import android.content.Context
import android.util.Log
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.pup.seenior.network.RetrofitClient

/**
 * Keeps the last after-hours settings the server sent, so the notice works offline. [current]
 * never touches the network: it returns the cached copy if there is a usable one and the bundled
 * [AfterHoursConfig.DEFAULT] otherwise. [refreshIfStale] fetches a new copy, at most every
 * [MAX_AGE_MS], and never throws; a failed fetch just leaves the old copy in place.
 *
 * Nothing personal is stored or sent: it is the same public list for every user.
 */
object AfterHoursStore {

    private const val TAG = "AfterHours"
    private const val PREFS = "after_hours"
    private const val KEY_JSON = "config_json"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

    private val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    fun current(context: Context): AfterHoursConfig {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null)
        return parse(json) ?: AfterHoursConfig.DEFAULT
    }

    /** Null for missing or unusable JSON, so a bad cache falls back to the defaults. */
    internal fun parse(json: String?): AfterHoursConfig? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, AfterHoursConfig::class.java) }
            .getOrNull()
            ?.takeIf { it.isUsable() }
    }

    suspend fun refreshIfStale(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (nowMs - prefs.getLong(KEY_FETCHED_AT, 0L) < MAX_AGE_MS) return
        try {
            val fresh = RetrofitClient.api.afterHours()
            if (!fresh.isUsable()) {
                Log.w(TAG, "server sent an unusable after-hours config; keeping the cached one")
                return
            }
            prefs.edit()
                .putString(KEY_JSON, gson.toJson(fresh))
                .putLong(KEY_FETCHED_AT, nowMs)
                .apply()
        } catch (e: Exception) {
            // Offline or the server is waking up. The cached copy (or the defaults) stays.
            Log.i(TAG, "could not refresh after-hours config: ${e.javaClass.simpleName}")
        }
    }
}
