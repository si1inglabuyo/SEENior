package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pup.seenior.database.entities.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contact: Contact): Long

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM Contacts WHERE senior_id = :seniorId ORDER BY created_at ASC")
    fun getAllBySenior(seniorId: Int): Flow<List<Contact>>

    @Query("SELECT * FROM Contacts WHERE contact_id = :contactId")
    suspend fun getById(contactId: Int): Contact?

    @Query("SELECT * FROM Contacts WHERE senior_id = :seniorId AND contact_type = :type AND is_active = 1")
    fun getActiveByType(seniorId: Int, type: String): Flow<List<Contact>>

    /**
     * The paired family, read once rather than observed.
     *
     * The SOS screen needs an answer the moment the button is swiped, from whatever is on the
     * device -- there is no time to wait for a Flow to emit, and no network to wait for either.
     */
    @Query(
        "SELECT * FROM Contacts WHERE senior_id = :seniorId AND contact_type = 'family' " +
            "AND is_active = 1 ORDER BY created_at ASC"
    )
    suspend fun getFamilyContactsOnce(seniorId: Int): List<Contact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<Contact>)

    @Query("DELETE FROM Contacts WHERE senior_id = :seniorId AND contact_type = 'family'")
    suspend fun deleteFamilyContacts(seniorId: Int)

    /**
     * Makes the cached family list match what the cloud just returned, in one transaction.
     *
     * Replace rather than merge: the cloud is authoritative about who is paired, so a contact
     * the senior unlinked must disappear here too. Doing it in a transaction matters because
     * the half-state -- deleted, not yet reinserted -- is exactly the state the SOS screen
     * would read as "nobody will be called".
     */
    @Transaction
    suspend fun replaceFamilyContacts(seniorId: Int, contacts: List<Contact>) {
        deleteFamilyContacts(seniorId)
        if (contacts.isNotEmpty()) insertAll(contacts)
    }

    @Query("SELECT COUNT(*) FROM Contacts WHERE senior_id = :seniorId AND contact_type = 'family' AND is_active = 1")
    suspend fun getFamilyContactCount(seniorId: Int): Int

    @Query("SELECT * FROM Contacts WHERE invite_code = :code LIMIT 1")
    suspend fun getByInviteCode(code: String): Contact?

    @Query("UPDATE Contacts SET invite_code = NULL, invite_expires_at = NULL WHERE contact_id = :contactId")
    suspend fun clearInviteCode(contactId: Int)

    @Query("UPDATE Contacts SET fcm_token = :token WHERE contact_id = :contactId")
    suspend fun updateFcmToken(contactId: Int, token: String)
}
