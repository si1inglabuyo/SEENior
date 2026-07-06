package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pup.seenior.database.entities.FalsePositive
import kotlinx.coroutines.flow.Flow

@Dao
interface FalsePositiveDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(falsePositive: FalsePositive): Long

    @Update
    suspend fun update(falsePositive: FalsePositive)

    @Delete
    suspend fun delete(falsePositive: FalsePositive)

    @Query("SELECT * FROM False_Positives WHERE senior_id = :seniorId ORDER BY reported_at DESC")
    fun getAllBySenior(seniorId: Int): Flow<List<FalsePositive>>

    @Query("SELECT * FROM False_Positives WHERE fp_id = :fpId")
    suspend fun getById(fpId: Int): FalsePositive?

    @Query("SELECT * FROM False_Positives WHERE alert_id = :alertId LIMIT 1")
    suspend fun getByAlertId(alertId: Int): FalsePositive?

    /** Total count — used for accuracy/false-positive rate reporting (target ≤ 15%) */
    @Query("SELECT COUNT(*) FROM False_Positives WHERE senior_id = :seniorId")
    suspend fun getCountBySenior(seniorId: Int): Int

    @Query("SELECT COUNT(*) FROM False_Positives WHERE senior_id = :seniorId AND reported_by = :reportedBy")
    suspend fun getCountByReporter(seniorId: Int, reportedBy: String): Int
}
