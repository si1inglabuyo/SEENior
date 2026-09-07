package com.pup.seenior.ui.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.alerts.EscalationScheduler
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Senior
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import com.pup.seenior.network.dto.AccountDeletionRequest
import com.pup.seenior.network.dto.UpdateSeniorRequest
import com.pup.seenior.sensors.SensorCollectionService
import com.pup.seenior.ui.onboarding.OnboardingOptions
import com.pup.seenior.validation.PhilippinePhone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import com.pup.seenior.ui.wellness.WellnessMessages

/** Backs the senior's Profile tab and its Edit Profile screen. */
class SeniorProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SeniorAppDatabase.getInstance(application)
    private val cloudSync = SeniorCloudSync(db)

    var senior by mutableStateOf<Senior?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Set when the local save succeeded but the cloud copy could not be updated. Deliberately
     *  separate from [error]: the senior's own record IS saved, so this is a warning ("your
     *  family may see your old details"), never a failure. */
    var syncWarning by mutableStateOf<String?>(null)
        private set

    // ---- Edit-profile form state ----
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var age by mutableStateOf("")
    var gender by mutableStateOf<String?>(null)
    var mobileNumber by mutableStateOf("")
    var livingArrangementLabel by mutableStateOf<String?>(null)

    /** The senior's chosen language, mirroring `Senior_Onboarding.language_preference`. Held
     *  here rather than read from the device locale on purpose: a handset set up in English by a
     *  relative must not decide what an emergency prompt says to the senior. */
    var language by mutableStateOf(WellnessMessages.ENGLISH)
        private set
    var address by mutableStateOf("")
    var isSaving by mutableStateOf(false)
        private set

    val fullName: String
        get() = senior?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Senior"

    /**
     * Whether Profile hosts the "Family contacts" row.
     *
     * Read from the saved record, not from [livingArrangementLabel]: that one tracks the Edit
     * Profile form and changes on every keystroke of a dropdown the senior may still abandon.
     * The row appearing and disappearing under a half-made edit would be its own small bug.
     */
    val livesAlone: Boolean
        get() = senior?.livingArrangement == OnboardingOptions.LIVING_ALONE

    val isEditValid: Boolean
        get() = firstName.isNotBlank() &&
            lastName.isNotBlank() &&
            age.toIntOrNull() != null &&
            gender != null &&
            PhilippinePhone.isValid(mobileNumber) &&
            livingArrangementLabel != null &&
            address.isNotBlank()

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val loaded = db.seniorDao().getOnboardedSenior()
                if (loaded == null) {
                    error = "No profile found on this device."
                } else {
                    senior = loaded
                    fillFormFrom(loaded)
                    db.seniorOnboardingDao().getBySeniorId(loaded.seniorId)?.let {
                        language = it.languagePreference
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Persists a new language choice immediately — there is no Save button on that screen.
     *
     * Written straight through to the database rather than held as a draft because this is the
     * one setting whose only visible effect is on screens the senior may not reach again for
     * days. A half-applied language is worse than either language.
     */
    fun chooseLanguage(code: String) {
        val id = senior?.seniorId ?: return
        if (code == language) return
        language = code
        viewModelScope.launch {
            db.seniorOnboardingDao().updateLanguagePreference(id, code)
        }
    }

    /** Discards unsaved edits — called when leaving Edit Profile without saving, so half-typed
     *  values don't survive into the next visit. */
    fun discardEdits() {
        senior?.let { fillFormFrom(it) }
        error = null
        syncWarning = null
    }

    private fun fillFormFrom(source: Senior) {
        firstName = source.firstName
        lastName = source.lastName
        age = source.age.toString()
        gender = source.gender
        mobileNumber = source.mobileNumber
        address = source.address
        // Stored as "alone"/"with_family"; the dropdown shows the human-readable label.
        livingArrangementLabel = OnboardingOptions.livingArrangements
            .firstOrNull { it.second == source.livingArrangement }?.first
    }

    fun saveProfile(onSaved: () -> Unit) {
        val current = senior ?: return
        if (!isEditValid || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            syncWarning = null

            val updated = current.copy(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                age = age.trim().toInt(),
                gender = gender!!,
                mobileNumber = PhilippinePhone.normalize(mobileNumber)!!,
                address = address.trim(),
                livingArrangement = OnboardingOptions.livingArrangements
                    .first { it.first == livingArrangementLabel }.second
            )

            // Local first: this device is the source of truth and must save even offline.
            db.seniorDao().update(updated)
            senior = updated

            // Then a best-effort cloud push, so the family app stops showing stale details.
            // Skipped entirely when this senior has never registered with the cloud — no reason
            // to create a cloud record just because a name was edited.
            //
            // Deliberately uses the cached id directly instead of SeniorCloudSync.withSyncId:
            // that helper re-registers via POST /seniors on ANY 404, and a 404 here is ambiguous
            // — it means "senior row missing" OR "this backend predates PATCH /seniors/{id}".
            // In the second case the self-heal would mint a fresh cloud senior on every save and
            // rotate cloud_sync_id, silently orphaning family contacts already paired to the old
            // id. A profile edit is not worth that risk; the invite flow still self-heals a
            // genuinely stale id the next time a code is generated.
            try {
                val syncId = cloudSync.withSyncIdOrNull()
                if (syncId != null) {
                    RetrofitClient.api.updateSenior(
                        syncId,
                        UpdateSeniorRequest(
                            firstName = updated.firstName,
                            lastName = updated.lastName,
                            age = updated.age,
                            gender = updated.gender,
                            barangay = updated.barangay,
                            address = updated.address,
                            mobileNumber = updated.mobileNumber
                        )
                    )
                }
            } catch (e: IOException) {
                syncWarning = "Saved on this phone. Your family may see your old details until you reconnect."
            } catch (e: Exception) {
                syncWarning = "Saved on this phone, but we could not update your family's copy."
            } finally {
                isSaving = false
            }

            onSaved()
        }
    }

    fun clearSyncWarning() {
        syncWarning = null
    }

    // ---- Delete account ----

    var isDeleting by mutableStateOf(false)
        private set

    /**
     * Deletes this senior's account.
     *
     * The cloud call is best-effort: erasing this phone is what actually protects the
     * senior's data (CLAUDE.md §11), so a failed or offline server call must not block
     * the wipe. Known limitation — there is no retry after the wipe, so if the phone is
     * offline the cloud record (name + address only) lingers until it is pruned by hand;
     * a hardened build would need a server-side TTL or an unauthenticated retry token.
     *
     * Order matters: monitoring is stopped and every armed escalation alarm is cancelled
     * *before* the rows are wiped, then the whole local database goes.
     *
     * [reason] is a stable code ("switching_phone", …), not the on-screen label.
     */
    fun deleteAccount(reason: String, note: String?, onDeleted: () -> Unit) {
        if (isDeleting) return
        isDeleting = true
        viewModelScope.launch {
            val app = getApplication<Application>()

            // 1. Best-effort cloud soft-delete + contact unlink. Capped so a hung network
            //    cannot leave the senior staring at "Deleting…" for the full OkHttp timeout —
            //    the wipe below is the part that matters and must not wait on this.
            runCatching {
                withTimeoutOrNull(8_000) {
                    cloudSync.withSyncIdOrNull()?.let { syncId ->
                        RetrofitClient.api.deleteSenior(syncId, AccountDeletionRequest(reason, note))
                    }
                }
            }

            // 2. Stop passive monitoring and cancel any armed escalation deadlines
            //    while the alert rows still exist to be found.
            SensorCollectionService.stop(app)
            runCatching {
                db.alertDao().getAllAlertIds().forEach { EscalationScheduler.cancel(app, it) }
            }

            // 3. Erase the local database — all ten tables, the real personal data.
            //    clearAllTables() is a blocking call and asserts off the main thread.
            withContext(Dispatchers.IO) { db.clearAllTables() }

            isDeleting = false
            onDeleted()
        }
    }
}
