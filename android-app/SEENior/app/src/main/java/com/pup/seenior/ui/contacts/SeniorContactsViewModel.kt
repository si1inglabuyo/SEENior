package com.pup.seenior.ui.contacts

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Contact
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.SeniorCloudSync
import com.pup.seenior.network.dto.FamilyContactDto
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class SeniorContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeniorAppDatabase.getInstance(application)
    private val cloudSync = SeniorCloudSync(db)

    var contacts by mutableStateOf<List<FamilyContactDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** True when the last refresh failed. Separate from `contacts.isEmpty()` so a failed load never looks like deleted contacts. */
    var loadFailed by mutableStateOf(false)
        private set

    private var cloudSyncId: String? = null

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            error = null
            loadFailed = false
            try {
                // Not registered with the cloud means no family can have paired, so skip creating a cloud record to read an empty list.
                val syncId = cloudSync.withSyncIdOrNull()
                cloudSyncId = syncId
                contacts = if (syncId != null) {
                    // A 404 means the cached id predates the backend's database; withSyncId re-registers.
                    cloudSync.withSyncId { id ->
                        cloudSyncId = id
                        RetrofitClient.api.getFamilyContacts(id)
                    }.also { cacheForSos(it) }
                } else emptyList()
            } catch (e: HttpException) {
                error = "Could not load contacts (server error ${e.code()})."
                loadFailed = true
            } catch (e: IOException) {
                error = "Could not reach the server. Check your internet connection."
                loadFailed = true
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Mirrors a successful fetch into the device's Contacts table. The SOS screen reads that
     * table, so a newly paired contact shows up there without an app restart. Failures are
     * swallowed so a cache write never turns a working list into an error.
     */
    private suspend fun cacheForSos(fetched: List<FamilyContactDto>) {
        try {
            val seniorId = db.seniorDao().getOnboardedSenior()?.seniorId ?: return
            db.contactDao().replaceFamilyContacts(
                seniorId,
                fetched.map {
                    Contact(
                        seniorId = seniorId,
                        name = it.fullName.orEmpty(),
                        phoneNumber = it.phone.orEmpty(),
                        contactType = it.contactType,
                        relationshipLabel = it.relationshipLabel
                    )
                }
            )
        } catch (e: Exception) {
            // Cache only. The screen already has what it needs.
        }
    }

    fun removeContact(contactId: Int) {
        val syncId = cloudSyncId ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.api.removeFamilyContact(syncId, contactId)
                contacts = contacts.filterNot { it.id == contactId }
            } catch (e: HttpException) {
                error = "Could not remove contact (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
            }
        }
    }
}
