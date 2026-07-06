package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Sensor_Data",
    foreignKeys = [
        ForeignKey(
            entity = Senior::class,
            parentColumns = ["senior_id"],
            childColumns = ["senior_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("senior_id"), Index("timestamp"), Index("is_aggregated")]
)
data class SensorData(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "data_id") val dataId: Int = 0,
    @ColumnInfo(name = "senior_id") val seniorId: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    /** "morning", "afternoon", "evening", or "night" */
    @ColumnInfo(name = "time_block") val timeBlock: String,
    /** Accelerometer-derived movement intensity 0.0–1.0 */
    @ColumnInfo(name = "movement_score") val movementScore: Double,
    /** Continuous inactivity duration in seconds */
    @ColumnInfo(name = "inactivity_duration") val inactivityDuration: Long,
    /** Screen idle duration in seconds */
    @ColumnInfo(name = "screen_idle_duration") val screenIdleDuration: Long,
    @ColumnInfo(name = "screen_unlock_count") val screenUnlockCount: Int,
    @ColumnInfo(name = "is_charging") val isCharging: Boolean,
    /** Step counter reading at the time of collection */
    @ColumnInfo(name = "step_count") val stepCount: Int,
    /** Set to true once rolled into Daily_Aggregates; purged nightly after aggregation */
    @ColumnInfo(name = "is_aggregated") val isAggregated: Boolean = false
)
