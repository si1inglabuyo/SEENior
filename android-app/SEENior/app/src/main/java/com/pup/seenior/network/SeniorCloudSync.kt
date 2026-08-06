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
     * Both senior-side endpoints that take a sync_id return 404 only for "Senior not found", so a
     * 404 here is never ambiguous.
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

    /** For read paths that should stay silent when the senior has never used a cloud feature:
     *  returns null rather than registering, since no family can have paired yet either way. */
    suspend fun withSyncIdOrNull(): String? = cachedSyncId()
}
