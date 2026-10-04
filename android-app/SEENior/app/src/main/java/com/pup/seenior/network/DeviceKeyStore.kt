package com.pup.seenior.network

import android.content.Context
import android.content.SharedPreferences

/**
 * The key that proves requests come from this senior's own phone.
 *
 * A senior's `sync_id` is also visible to linked family members and barangay responders, so the
 * server wants this key (sent as `X-Device-Key`) for anything done in the senior's name. It is
 * issued once by the backend and kept here, in app-private storage that isn't backed up. A phone
 * has one senior, so one key is stored together with the sync_id it belongs to.
 */
object DeviceKeyStore {
    private const val PREFS = "device_key_store"
    private const val KEY = "device_key"
    private const val SYNC_ID = "sync_id"

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Call once from the Application, before any request is made. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** The stored key, whichever senior it was issued for. Used to stamp outgoing requests. */
    fun current(): String? = prefs?.getString(KEY, null)

    /** True if a key is stored for exactly this cloud identity. */
    fun hasKeyFor(syncId: String): Boolean =
        prefs?.let { it.getString(SYNC_ID, null) == syncId && it.getString(KEY, null) != null } == true

    fun save(syncId: String, key: String) {
        prefs?.edit()?.putString(SYNC_ID, syncId)?.putString(KEY, key)?.apply()
    }

    /** Forgets the key, for account deletion. */
    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
