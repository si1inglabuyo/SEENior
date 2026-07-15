package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pup.seenior.database.entities.DailyAggregate
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyAggregateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dailyAggregate: DailyAggregate): Long

    @Update
    suspend fun update(dailyAggregate: DailyAggregate)

    @Delete
    suspend fun delete(dailyAggregate: DailyAggregate)

    @Query("SELECT * FROM Daily_Aggregates WHERE senior_id = :seniorId ORDER BY date DESC, time_block ASC")
    fun getAllBySenior(seniorId: Int): Flow<List<DailyAggregate>>

    @Query("SELECT * FROM Daily_Aggregates WHERE aggregate_id = :aggregateId")
    suspend fun getById(aggregateId: Int): DailyAggregate?

    @Query("SELECT * FROM Daily_Aggregates WHERE senior_id = :seniorId AND date = :date")
    suspend fun getByDate(seniorId: Int, date: String): List<DailyAggregate>

    @Query("DELETE FROM Daily_Aggregates WHERE senior_id = :seniorId AND date = :date AND time_block = :timeBlock")
    suspend fun deleteByDateAndTimeBlock(seniorId: Int, date: String, timeBlock: String)


    /** Returns the most recent N days of aggregates — used as input for Isolation Forest inference */
    @Query("""
        SELECT * FROM Daily_Aggregates
        WHERE senior_id = :seniorId
        ORDER BY date DESC, time_block ASC
        LIMIT :days * 4
    """)
    suspend fun getRecentDays(seniorId: Int, days: Int): List<DailyAggregate>

    /** Returns rows where the Isolation Forest score hasn't been computed yet */
    @Query("SELECT * FROM Daily_Aggregates WHERE senior_id = :seniorId AND isolation_forest_score IS NULL ORDER BY date ASC")
    suspend fun getWithoutIsolationForestScore(seniorId: Int): List<DailyAggregate>

    @Query("UPDATE Daily_Aggregates SET isolation_forest_score = :score WHERE aggregate_id = :aggregateId")
    suspend fun updateIsolationForestScore(aggregateId: Int, score: Double)
}
