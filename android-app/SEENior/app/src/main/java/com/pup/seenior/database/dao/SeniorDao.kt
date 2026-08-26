package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pup.seenior.database.entities.Senior
import kotlinx.coroutines.flow.Flow

@Dao
interface SeniorDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(senior: Senior): Long

    @Update
    suspend fun update(senior: Senior)

    @Delete
    suspend fun delete(senior: Senior)

    @Query("SELECT * FROM Seniors ORDER BY created_at DESC")
    fun getAll(): Flow<List<Senior>>

    @Query("SELECT * FROM Seniors WHERE senior_id = :seniorId")
    suspend fun getById(seniorId: Int): Senior?

    @Query("SELECT * FROM Seniors WHERE is_onboarding_complete = 1 ORDER BY created_at DESC LIMIT 1")
    suspend fun getOnboardedSenior(): Senior?

    @Query("UPDATE Seniors SET is_onboarding_complete = 1 WHERE senior_id = :seniorId")
    suspend fun markOnboardingComplete(seniorId: Int)

    @Query("UPDATE Seniors SET cloud_sync_id = :cloudSyncId WHERE senior_id = :seniorId")
    suspend fun updateCloudSyncId(seniorId: Int, cloudSyncId: String)

    /**
     * Moves a senior off "alone" once a family contact actually pairs with them.
     *
     * A column write rather than [update] with a whole entity: the caller holds a Senior that
     * may be seconds stale, and writing all of it back would silently revert an edit the
     * senior made on the Profile screen in the meantime.
     */
    @Query("UPDATE Seniors SET living_arrangement = :livingArrangement WHERE senior_id = :seniorId")
    suspend fun updateLivingArrangement(seniorId: Int, livingArrangement: String)
}
