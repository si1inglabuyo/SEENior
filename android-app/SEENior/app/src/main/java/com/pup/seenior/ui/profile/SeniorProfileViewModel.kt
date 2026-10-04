package com.pup.seenior.ui.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.address.AddressForm
import com.pup.seenior.alerts.EscalationScheduler
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Senior
import com.pup.seenior.network.DeviceKeyStore
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

    /** Set when the local save succeeded but the cloud copy couldn't be updated. A warning ("your
     *  family may see your old details"), separate from [error] because the record is saved. */
    var syncWarning by mutableStateOf<String?>(null)
        private set

    // ---- Edit-profile form state ----
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var age by mutableStateOf("")
    var gender by mutableStateOf<String?>(null)
    var mobileNumber by mutableStateOf("")
    var livingArrangementLabel by mutableStateOf<String?>(null)

    /** The senior's chosen language (`Senior_Onboarding.language_preference`). Held here, not read
     *  from the device locale, so a handset set up in English by a relative doesn't decide what an emergency prompt says. */
    var language by mutableStateOf(WellnessMessages.ENGLISH)
        private set
    /** The structured address being edited, the same holder sign-up uses. */
    val addressForm = AddressForm()
    var isSaving by mutableStateOf(false)
        private set

    val fullName: String
        get() = senior?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Senior"

    /**
     * Whether Profile shows the "Family contacts" row. Read from the saved record, not
     * [livingArrangementLabel], which changes while the senior is still editing.
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
            // An older free-text address that hasn't been touched is left alone; once edited it must be a complete PSGC address.
            (!addressForm.dirty || addressForm.isComplete)

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
                    addressForm.load(getApplication())
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
     * Saves a new language choice immediately (there is no Save button), since its effect is
     * on screens the senior may not reach for days.
     */
    fun chooseLanguage(code: String) {
        val id = senior?.seniorId ?: return
        if (code == language) return
        language = code
        viewModelScope.launch {
            db.seniorOnboardingDao().updateLanguagePreference(id, code)
        }
    }

    /** Discards unsaved edits when leaving Edit Profile without saving. */
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
        addressForm.fillFrom(source.address, source.barangay)
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
                // Untouched -> keep what is stored, including a pre-structured free-text address.
                address = if (addressForm.dirty) addressForm.joined() else current.address,
                barangay = if (addressForm.dirty) addressForm.barangay!! else current.barangay,
                livingArrangement = OnboardingOptions.livingArrangements
                    .first { it.first == livingArrangementLabel }.second
            )

            // Local first: this device is the source of truth and must save even offline.
            db.seniorDao().update(updated)
            senior = updated

            // Then a best-effort cloud push so the family app doesn't show stale details.
            // Skipped if this senior never registered with the cloud.
            //
            // Uses the cached id directly instead of SeniorCloudSync.withSyncId, which
            // re-registers on any 404. Here a 404 can also mean the backend predates
            // PATCH /seniors/{id}, and re-registering would mint a new cloud senior on every
            // save and orphan paired family contacts.
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
     * Deletes this senior's account. The cloud call is best effort, since erasing the phone is
     * what protects the data, so an offline server must not block the wipe. Known limitation:
     * there is no retry after the wipe, so an offline delete leaves the cloud record (name and
     * address only) until it is pruned by hand. Monitoring is stopped and alarms cancelled
     * before the rows are wiped. [reason] is a stable code ("switching_phone"), not the label.
     */
    fun deleteAccount(reason: String, note: String?, onDeleted: () -> Unit) {
        if (isDeleting) return
        isDeleting = true
        viewModelScope.launch {
            val app = getApplication<Application>()

            // 1. Best-effort cloud soft-delete and contact unlink, with a time cap so a hung
            //    network doesn't hold up the wipe.
            runCatching {
                withTimeoutOrNull(8_000) {
                    cloudSync.withSyncIdOrNull()?.let { syncId ->
                        RetrofitClient.api.deleteSenior(syncId, AccountDeletionRequest(reason, note))
                    }
                }
            }

            // 2. Stop monitoring and cancel armed escalation deadlines while the alert rows still exist.
            SensorCollectionService.stop(app)
            runCatching {
                db.alertDao().getAllAlertIds().forEach { EscalationScheduler.cancel(app, it) }
            }

            // 3. Erase the local database (all ten tables). clearAllTables() blocks and must run off the main thread.
            withContext(Dispatchers.IO) { db.clearAllTables() }
            DeviceKeyStore.clear()

            isDeleting = false
            onDeleted()
        }
    }
}
