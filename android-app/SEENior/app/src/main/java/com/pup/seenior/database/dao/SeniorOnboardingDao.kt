package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pup.seenior.database.entities.SeniorOnboarding
import kotlinx.coroutines.flow.Flow

@Dao
interface SeniorOnboardingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(onboarding: SeniorOnboarding): Long

    @Update
    suspend fun update(onboarding: SeniorOnboarding)

    @Delete
    suspend fun delete(onboarding: SeniorOnboarding)

    @Query("SELECT * FROM Senior_Onboarding")
    fun getAll(): Flow<List<SeniorOnboarding>>

    @Query("SELECT * FROM Senior_Onboarding WHERE onboarding_id = :onboardingId")
    suspend fun getById(onboardingId: Int): SeniorOnboarding?

    @Query("SELECT * FROM Senior_Onboarding WHERE senior_id = :seniorId LIMIT 1")
    suspend fun getBySeniorId(seniorId: Int): SeniorOnboarding?

    /**
     * Sets seed_baseline_generated = true once the onboarding questionnaire answers
     * have been successfully converted into seed Baseline rows.
     */
    @Query("UPDATE Senior_Onboarding SET seed_baseline_generated = 1 WHERE senior_id = :seniorId")
    suspend fun markSeedBaselineGenerated(seniorId: Int)

    /**
     * Sets baseline_ready_at to signal that 14 days of real sensor data have fully replaced
     * all seed values — passive detection now operates at full accuracy.
     */
    @Query("UPDATE Senior_Onboarding SET baseline_ready_at = :readyAt WHERE senior_id = :seniorId")
    suspend fun markBaselineReady(seniorId: Int, readyAt: Long)

    @Query("UPDATE Senior_Onboarding SET language_preference = :language WHERE senior_id = :seniorId")
    suspend fun updateLanguagePreference(seniorId: Int, language: String)
}
