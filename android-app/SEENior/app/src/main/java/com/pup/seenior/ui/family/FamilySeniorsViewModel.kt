package com.pup.seenior.ui.family

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pup.seenior.network.RetrofitClient
import com.pup.seenior.network.dto.ContactDto
import com.pup.seenior.session.FamilySession
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** A family account may monitor at most this many seniors. */
const val MAX_LINKED_SENIORS = 3

/**
 * Loads and holds every senior the logged-in family member is linked to. Shared by the
 * Home, Link, and Contacts tabs (one instance per FamilyDashboard) so a link or unlink
 * on one tab is reflected immediately on the others.
 */
class FamilySeniorsViewModel(application: Application) : AndroidViewModel(application) {
    var contacts by mutableStateOf<List<ContactDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val canLinkMore: Boolean get() = contacts.size < MAX_LINKED_SENIORS

    fun refresh() {
        val token = FamilySession.getToken(getApplication()) ?: return
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                contacts = RetrofitClient.api.getMyContacts("Bearer $token")
            } catch (e: HttpException) {
                error = "Could not load your linked seniors (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
            } finally {
                isLoading = false
            }
        }
    }

    fun unlink(contactId: Int, onDone: () -> Unit = {}) {
        val token = FamilySession.getToken(getApplication()) ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.api.unlinkContact("Bearer $token", contactId)
                contacts = contacts.filterNot { it.id == contactId }
                onDone()
            } catch (e: HttpException) {
                error = "Could not unlink (server error ${e.code()})."
            } catch (e: IOException) {
                error = "Could not reach the server."
            }
        }
    }
}
