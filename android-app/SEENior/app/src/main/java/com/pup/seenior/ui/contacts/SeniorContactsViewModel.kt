package com.pup.seenior.ui.contacts

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.FamilyContactDto
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class SeniorContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = SeniorAppDatabase.getInstance(application)

    var contacts by mutableStateOf<List<FamilyContactDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var cloudSyncId: String? = null

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val senior = db.seniorDao().getOnboardedSenior()
                cloudSyncId = senior?.cloudSyncId
                // Not registered with the cloud yet ⇒ no family can have paired ⇒ empty list.
                val syncId = cloudSyncId
                contacts = if (syncId != null) RetrofitClient.api.getFamilyContacts(syncId) else emptyList()
            } catch (e: HttpException) {
                error = "Could not load contacts (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
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
