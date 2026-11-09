package com.pup.seenior.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pup.seenior.network.dto.ContactDto
import com.pup.seenior.network.dto.FamilyContactDto

/**
 * Last contact lists the server sent, so the Contacts screens still show something with no
 * signal or a slow connection. Written only after a successful fetch and read only when a fetch
 * fails to reach the server; it never replaces a fresh answer, and a server "no" (401, 5xx)
 * does not use it.
 *
 * Senior side: the family contacts the senior already sees. Family side: the seniors that
 * account is linked to. Neither holds anything the same screen did not already show. Each
 * copy is dropped when its account ends ([clearFamily] on sign-out, [clearSenior] on delete).
 */
object ContactsCache {
    private const val PREFS = "contacts_cache"
    private const val KEY_SENIOR = "senior_family_contacts"
    private const val KEY_FAMILY = "family_linked_seniors"

    private val gson = Gson()

    fun saveSenior(context: Context, contacts: List<FamilyContactDto>) =
        save(context, KEY_SENIOR, contacts)

    fun loadSenior(context: Context): List<FamilyContactDto>? =
        load(context, KEY_SENIOR, object : TypeToken<List<FamilyContactDto>>() {})

    fun clearSenior(context: Context) = remove(context, KEY_SENIOR)

    fun saveFamily(context: Context, contacts: List<ContactDto>) =
        save(context, KEY_FAMILY, contacts)

    fun loadFamily(context: Context): List<ContactDto>? =
        load(context, KEY_FAMILY, object : TypeToken<List<ContactDto>>() {})

    fun clearFamily(context: Context) = remove(context, KEY_FAMILY)

    private fun save(context: Context, key: String, value: Any) {
        // A cache write must never turn a working list into an error.
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(key, gson.toJson(value)).apply()
        }
    }

    private fun <T> load(context: Context, key: String, type: TypeToken<List<T>>): List<T>? {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
            ?: return null
        // Gson can leave a non-null Kotlin field null on a stale shape; treat that as no cache.
        return runCatching { gson.fromJson<List<T>>(json, type.type) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun remove(context: Context, key: String) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key).apply()
        }
    }
}
