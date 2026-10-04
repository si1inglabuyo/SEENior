package com.pup.seenior.network

import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.network.dto.CreateSeniorRequest
import retrofit2.HttpException
import java.io.IOException

/**
 * Owns the senior's cloud identity (`Seniors.cloud_sync_id`). Registration is lazy, on first
 * use of a cloud feature, so onboarding stays offline-capable.
 *
 * The cached id is not assumed valid forever: it can point at a senior the current backend
 * doesn't know (switching between the dev backend and Render, or a recreated database).
 * [withSyncId] re-registers and retries once instead of leaving invites broken until the
 * app data is wiped.
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
        created.deviceKey?.let { DeviceKeyStore.save(created.syncId, it) }
        return created.syncId
    }

    /**
     * Gets a device key for a senior who registered before keys existed, once. Offline it just
     * tries again on the next call. If the server says a key was already issued and this phone
     * doesn't have it, retrying can't help, so it stops asking.
     */
    private suspend fun ensureDeviceKey(syncId: String) {
        if (DeviceKeyStore.hasKeyFor(syncId) || syncId in refusedClaims) return
        try {
            DeviceKeyStore.save(syncId, RetrofitClient.api.claimDeviceKey(syncId).deviceKey)
        } catch (e: HttpException) {
            if (e.code() == 409) refusedClaims += syncId
        } catch (_: IOException) {
            // No connection; the next call will try again.
        }
    }

    private suspend fun cachedSyncId(): String? =
        db.seniorDao().getOnboardedSenior()?.cloudSyncId

    /**
     * Runs [block] with a cloud sync_id, registering first if there isn't one. On a 404 the
     * cached id is stale, so it re-registers and retries once.
     *
     * Only for calls addressed to the senior (register, heartbeat, invite, contacts), where a
     * 404 can only mean "Senior not found". Use [withCachedSyncId] for anything addressed to
     * an alert.
     */
    suspend fun <T> withSyncId(block: suspend (String) -> T): T {
        val existing = cachedSyncId()
        if (existing == null) return block(register())
        ensureDeviceKey(existing)
        return try {
            block(existing)
        } catch (e: HttpException) {
            if (e.code() == 404) block(register()) else throw e
        }
    }

    /**
     * Runs [block] with the cached sync_id and never re-registers. For calls addressed to an
     * alert, whose 404 means "Alert not found" (including when it belongs to someone else).
     * [withSyncId] would read that as a stale identity and register a replacement senior; on
     * 2026-09-01 that ran every 15 minutes for six hours and left 25 duplicate seniors in
     * production. A 404 here is about one alert, so the caller just retries later.
     */
    suspend fun <T> withCachedSyncId(block: suspend (String) -> T): T {
        val existing = cachedSyncId()
            ?: throw IllegalStateException("No cloud sync_id; this alert cannot have been synced.")
        ensureDeviceKey(existing)
        return block(existing)
    }

    /** For read paths that should stay silent when the senior never used a cloud feature: returns null instead of registering. */
    suspend fun withSyncIdOrNull(): String? = cachedSyncId()

    private companion object {
        /** Identities the server refused to issue a key to, so we don't ask on every call. */
        val refusedClaims: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    }
}
