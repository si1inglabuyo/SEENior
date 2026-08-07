package com.pup.seenior.ui.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Senior
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import com.pup.seenior.network.dto.UpdateSeniorRequest
import com.pup.seenior.ui.onboarding.OnboardingOptions
import com.pup.seenior.validation.PhilippinePhone
import kotlinx.coroutines.launch
import java.io.IOException

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
    var address by mutableStateOf("")
    var isSaving by mutableStateOf(false)
        private set

    val fullName: String
        get() = senior?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Senior"

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
                }
            } finally {
                isLoading = false
            }
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
}
