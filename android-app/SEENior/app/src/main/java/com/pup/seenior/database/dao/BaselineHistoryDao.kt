package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pup.seenior.database.entities.BaselineHistory

@Dao
interface BaselineHistoryDao {

    /**
     * REPLACE on the (senior, date, feature, block) index: a copy the phone saved on a day
     * overwrites the estimate for the same day, which is the intent.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<BaselineHistory>)

    @Query("SELECT COUNT(*) FROM Baseline_History WHERE senior_id = :seniorId")
    suspend fun count(seniorId: Int): Int

    /** How many cells the phone itself saved on [date]; zero means only estimates (or nothing). */
    @Query("SELECT COUNT(*) FROM Baseline_History WHERE senior_id = :seniorId AND date = :date AND is_estimate = 0")
    suspend fun savedCountOn(seniorId: Int, date: String): Int

    /** Rows from [fromDate] on, oldest first: what the cloud keeps (30 days). */
    @Query("SELECT * FROM Baseline_History WHERE senior_id = :seniorId AND date >= :fromDate ORDER BY date ASC, time_block ASC, feature_name ASC")
    suspend fun since(seniorId: Int, fromDate: String): List<BaselineHistory>

    @Query("DELETE FROM Baseline_History WHERE senior_id = :seniorId AND date < :beforeDate")
    suspend fun pruneBefore(seniorId: Int, beforeDate: String)
}
