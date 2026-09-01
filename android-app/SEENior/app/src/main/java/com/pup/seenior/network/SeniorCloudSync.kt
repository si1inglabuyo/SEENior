package com.pup.seenior.network

import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.network.dto.CreateSeniorRequest
import retrofit2.HttpException

/**
 * Owns the senior's cloud identity (`Seniors.cloud_sync_id`).
 *
 * Registration is lazy — it happens the first time a cloud feature is used, so onboarding stays
 * fully offline-capable per CLAUDE.md §3.
 *
 * The cached id is deliberately NOT treated as permanently valid. It can point at a senior the
 * current backend has never heard of whenever the cloud database it was issued by is no longer
 * the one being talked to — switching between the local dev backend and Render, or Render's free
 * Postgres expiring after 30 days and being recreated. Before this class, that left the senior
 * permanently unable to generate an invite code (every call 404'd) with no recovery short of
 * wiping app data. [withSyncId] re-registers and retries once instead.
 */
class SeniorCloudSync(private val db: SeniorAppDatabase) {

    /** Registers this senior with whatever backend is currently configured and caches the new id. */
    private suspend fun register(): String {
        val senior = db.seniorDao().getOnboardedSenior()
            ?: throw IllegalStateException("No onboarded senior found on this device.")
        val created = RetrofitClient.api.createSenior(
            CreateSeniorRequest(
                firstName = senior.firstName,
                lastName = senior.lastName,
                age = senior.age,
                gender = senior.gender,
                barangay = senior.barangay,
                address = senior.address,
                mobileNumber = senior.mobileNumber
            )
        )
        db.seniorDao().updateCloudSyncId(senior.seniorId, created.syncId)
        return created.syncId
    }

    private suspend fun cachedSyncId(): String? =
        db.seniorDao().getOnboardedSenior()?.cloudSyncId

    /**
     * Runs [block] with a cloud sync_id, registering first if there isn't one yet. If the backend
     * reports 404 the cached id is stale (see class docs), so this re-registers and retries once.
     *
     * **Only for calls addressed to the senior.** For those — register, heartbeat, invite,
     * contacts — a 404 can only mean "Senior not found", which is the condition this recovery
     * exists for. It is not true of every endpoint that happens to carry a sync_id, and reading a
     * 404 as a stale identity where it meant something else is what
     * [withCachedSyncId] exists to prevent. Use that one for anything addressed to an alert.
     */
    suspend fun <T> withSyncId(block: suspend (String) -> T): T {
        val existing = cachedSyncId()
        if (existing == null) return block(register())
        return try {
            block(existing)
        } catch (e: HttpException) {
            if (e.code() == 404) block(register()) else throw e
        }
    }

    /**
     * Runs [block] with the cached sync_id and never re-registers, whatever comes back.
     *
     * For calls addressed to an *alert* rather than to the senior. Those answer 404 for "Alert
     * not found" — including the deliberate one the backend returns when the alert exists but
     * belongs to someone else, since it will not confirm an alert it is not going to show you.
     *
     * [withSyncId] reads a 404 as a stale identity and registers a replacement senior. Against an
     * alert-scoped 404 that is exactly backwards, and on 2026-09-01 it ran every fifteen minutes
     * for six hours: asked about an alert it could not prove it owned, the phone replaced the
     * identity that was the proof, and each new one orphaned it further. It left 25 duplicate
     * seniors in production and made the severity it was trying to repair permanently unrepairable.
     *
     * A 404 here is information about one alert, never about the senior. There is nothing to
     * recover from, so the caller simply retries later.
     */
    suspend fun <T> withCachedSyncId(block: suspend (String) -> T): T {
        val existing = cachedSyncId()
            ?: throw IllegalStateException("No cloud sync_id; this alert cannot have been synced.")
        return block(existing)
    }

    /** For read paths that should stay silent when the senior has never used a cloud feature:
     *  returns null rather than registering, since no family can have paired yet either way. */
    suspend fun withSyncIdOrNull(): String? = cachedSyncId()
}
