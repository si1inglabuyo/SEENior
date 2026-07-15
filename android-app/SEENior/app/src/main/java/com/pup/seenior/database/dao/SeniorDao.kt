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
}
