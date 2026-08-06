package com.pup.seenior.ui.contacts

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.database.SeniorAppDatabase
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

    /** True when the last refresh failed. Kept separate from `contacts.isEmpty()` so the screen
     *  never shows the "no family connected yet" empty state for what is really a failed load —
     *  that read as though the senior's paired contacts had been deleted. */
    var loadFailed by mutableStateOf(false)
        private set

    private var cloudSyncId: String? = null

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            error = null
            loadFailed = false
            try {
                // Not registered with the cloud yet ⇒ no family can have paired ⇒ empty list,
                // and no reason to create a cloud record just to read an empty one.
                val syncId = cloudSync.withSyncIdOrNull()
                cloudSyncId = syncId
                contacts = if (syncId != null) {
                    // A 404 here means the cached id predates the current backend's database;
                    // withSyncId re-registers so the senior recovers instead of staying broken.
                    cloudSync.withSyncId { id ->
                        cloudSyncId = id
                        RetrofitClient.api.getFamilyContacts(id)
                    }
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
