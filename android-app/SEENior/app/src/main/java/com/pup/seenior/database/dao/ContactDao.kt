package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT COUNT(*) FROM Contacts WHERE senior_id = :seniorId AND contact_type = 'family' AND is_active = 1")
    suspend fun getFamilyContactCount(seniorId: Int): Int

    @Query("SELECT * FROM Contacts WHERE invite_code = :code LIMIT 1")
    suspend fun getByInviteCode(code: String): Contact?

    @Query("UPDATE Contacts SET invite_code = NULL, invite_expires_at = NULL WHERE contact_id = :contactId")
    suspend fun clearInviteCode(contactId: Int)

    @Query("UPDATE Contacts SET fcm_token = :token WHERE contact_id = :contactId")
    suspend fun updateFcmToken(contactId: Int, token: String)
}
