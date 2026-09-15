package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pup.seenior.database.entities.Baseline
import kotlinx.coroutines.flow.Flow

@Dao
interface BaselineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(baseline: Baseline): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(baselines: List<Baseline>): List<Long>

    @Update
    suspend fun update(baseline: Baseline)

    @Delete
    suspend fun delete(baseline: Baseline)

    @Query("SELECT * FROM Baseline WHERE senior_id = :seniorId")
    fun getAllBySenior(seniorId: Int): Flow<List<Baseline>>

    /**
     * One-shot read of the whole Routine Fingerprint — all 5 features across all 4 blocks.
     *
     * Separate from [getAllBySenior] because that returns a Flow for screens to observe.
     * [com.pup.seenior.detection.IsolationForestDetector] needs the 20 rows exactly once per
     * nightly run, to score every block against, and a subscription would be the wrong shape.
     */
    @Query("SELECT * FROM Baseline WHERE senior_id = :seniorId")
    suspend fun getAllBySeniorOnce(seniorId: Int): List<Baseline>

    @Query("SELECT * FROM Baseline WHERE baseline_id = :baselineId")
    suspend fun getById(baselineId: Int): Baseline?

    /**
     * Primary lookup for Layer 1 (Median-MAD) detection — fetches the median and MAD
     * for a specific feature within a specific time block.
     */
    @Query("""
        SELECT * FROM Baseline
        WHERE senior_id = :seniorId
          AND feature_name = :featureName
          AND time_block = :timeBlock
        LIMIT 1
    """)
    suspend fun getBaselineByFeatureAndTimeBlock(
        seniorId: Int,
        featureName: String,
        timeBlock: String
    ): Baseline?

    /** Returns all baseline entries that are still seed values (not yet replaced by real data) */
    @Query("SELECT * FROM Baseline WHERE senior_id = :seniorId AND is_seed = 1")
    suspend fun getSeedBaselines(seniorId: Int): List<Baseline>

    /** Returns count of real-data baseline entries — used to gauge how close to Day 14 the senior is */
    @Query("SELECT COUNT(*) FROM Baseline WHERE senior_id = :seniorId AND is_seed = 0")
    suspend fun getRealBaselineCount(seniorId: Int): Int

    @Query("DELETE FROM Baseline WHERE senior_id = :seniorId")
    suspend fun deleteAllForSenior(seniorId: Int)

    @Query("DELETE FROM Baseline WHERE  senior_id = :seniorId AND feature_name = :featureName AND time_block = :timeBlock")
    suspend fun deleteByFeatureAndTimeBlock(seniorId: Int, featureName: String, timeBlock: String)

    @Transaction
    suspend fun replaceFeatureBaseline(baseline: Baseline) {
        deleteByFeatureAndTimeBlock(baseline.seniorId, baseline.featureName, baseline.timeBlock)
        insert(baseline)
    }
}
