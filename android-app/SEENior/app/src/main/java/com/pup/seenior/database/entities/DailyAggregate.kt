package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Daily_Aggregates",
    foreignKeys = [
        ForeignKey(
            entity = Senior::class,
            parentColumns = ["senior_id"],
            childColumns = ["senior_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("senior_id"), Index("date")]
)
data class DailyAggregate(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "aggregate_id") val aggregateId: Int = 0,
    @ColumnInfo(name = "senior_id") val seniorId: Int,
    /** ISO date string "YYYY-MM-DD" */
    @ColumnInfo(name = "date") val date: String,
    /** "morning", "afternoon", "evening", or "night" */
    @ColumnInfo(name = "time_block") val timeBlock: String,
    @ColumnInfo(name = "avg_movement_score") val avgMovementScore: Double,
    @ColumnInfo(name = "total_inactivity_duration") val totalInactivityDuration: Long,
    /** Longest screen-idle stretch in the block, in seconds. Named `avg_` for historical
     *  reasons (it once held an average); renaming needs a table rebuild on API 26. */
    @ColumnInfo(name = "avg_screen_idle_duration") val avgScreenIdleDuration: Long,
    @ColumnInfo(name = "total_screen_unlocks") val totalScreenUnlocks: Int,
    @ColumnInfo(name = "total_steps") val totalSteps: Int,
    /** True if the device was charging for a majority of readings in this block */
    @ColumnInfo(name = "is_charging_majority") val isChargingMajority: Boolean,
    /**
     * How many `Sensor_Data` rows this block was summarised from. The raw rows are purged the
     * same night, so without this a summary of four readings looks like one of fifty-two.
     *
     * It matters for Layer 2: Isolation Forest learns "normal" from what it is given, so
     * near-empty blocks would teach it that near-empty is ordinary. Used to exclude thin blocks
     * from training, never as a feature. Expected at 5-minute sampling for wake 10:00 / sleep
     * 23:00: ~52 per waking block, ~132 for the night. Null means unknown (older rows) and must
     * not be trusted.
     */
    @ColumnInfo(name = "sample_count") val sampleCount: Int? = null,
    /** Isolation Forest path-length anomaly score — set after the daily IF run, null until then */
    @ColumnInfo(name = "isolation_forest_score") val isolationForestScore: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
