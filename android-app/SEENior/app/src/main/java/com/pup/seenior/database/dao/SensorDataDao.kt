package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pup.seenior.database.entities.SensorData
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDataDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sensorData: SensorData): Long

    @Update
    suspend fun update(sensorData: SensorData)

    @Delete
    suspend fun delete(sensorData: SensorData)

    @Query("SELECT * FROM Sensor_Data WHERE senior_id = :seniorId ORDER BY timestamp DESC")
    fun getAllBySenior(seniorId: Int): Flow<List<SensorData>>

    @Query("SELECT * FROM Sensor_Data WHERE data_id = :dataId")
    suspend fun getById(dataId: Int): SensorData?

    /** Returns all rows not yet rolled into Daily_Aggregates — used by the nightly aggregation job */
    @Query("SELECT * FROM Sensor_Data WHERE senior_id = :seniorId AND is_aggregated = 0 ORDER BY timestamp ASC")
    suspend fun getUnaggregatedSensorData(seniorId: Int): List<SensorData>

    @Query("SELECT * FROM Sensor_Data WHERE senior_id = :seniorId AND time_block = :timeBlock ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentByTimeBlock(seniorId: Int, timeBlock: String, limit: Int): List<SensorData>

    /** Marks a batch of rows as aggregated so they can be purged by the nightly cleanup */
    @Query("UPDATE Sensor_Data SET is_aggregated = 1 WHERE data_id IN (:dataIds)")
    suspend fun markAsAggregated(dataIds: List<Int>)

    /** Purges all aggregated raw records — called nightly after rollup to Daily_Aggregates */
    @Query("DELETE FROM Sensor_Data WHERE is_aggregated = 1")
    suspend fun deleteAggregated(): Int
}
