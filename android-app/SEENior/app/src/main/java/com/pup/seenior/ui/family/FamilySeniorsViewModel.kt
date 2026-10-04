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
import com.pup.seenior.session.SessionState
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** A family account may monitor at most this many seniors. */
const val MAX_LINKED_SENIORS = 3

/**
 * Loads and holds every senior the family member is linked to. Shared by the Home, Link and
 * Contacts tabs (one instance per FamilyDashboard) so a change on one shows on the others.
 */
class FamilySeniorsViewModel(application: Application) : AndroidViewModel(application) {
    var contacts by mutableStateOf<List<ContactDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<FamilyError?>(null)
        private set

    /** True when the last refresh failed. Screens must check this before `contacts.isEmpty()`, so a failed fetch never looks like "no seniors linked". */
    var loadFailed by mutableStateOf(false)
        private set

    // Only meaningful after a successful load; after a failure `contacts` is empty for the wrong reason.
    val canLinkMore: Boolean get() = !loadFailed && contacts.size < MAX_LINKED_SENIORS

    fun refresh() {
        val token = FamilySession.getToken(getApplication()) ?: return
        viewModelScope.launch {
            isLoading = true
            error = null
            loadFailed = false
            try {
                contacts = RetrofitClient.api.getMyContacts("Bearer $token")
            } catch (e: HttpException) {
                error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    FamilyError.SessionExpired
                else FamilyError.Server(FamilyError.Action.LoadSeniors, e.code())
                loadFailed = true
            } catch (e: IOException) {
                error = FamilyError.Network(FamilyError.NetworkVariant.CheckConnection)
                loadFailed = true
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
                error = if (SessionState.handleIfUnauthorized(getApplication(), e))
                    FamilyError.SessionExpired
                else FamilyError.Server(FamilyError.Action.Unlink, e.code())
            } catch (e: IOException) {
                error = FamilyError.Network()
            }
        }
    }
}
