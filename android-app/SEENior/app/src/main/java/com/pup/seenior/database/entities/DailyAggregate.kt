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
     *  reasons -- it held a per-poll average before screen idle became a running counter, and
     *  renaming the column needs a table rebuild on API 26. */
    @ColumnInfo(name = "avg_screen_idle_duration") val avgScreenIdleDuration: Long,
    @ColumnInfo(name = "total_screen_unlocks") val totalScreenUnlocks: Int,
    @ColumnInfo(name = "total_steps") val totalSteps: Int,
    /** True if the device was charging for a majority of readings in this block */
    @ColumnInfo(name = "is_charging_majority") val isChargingMajority: Boolean,
    /**
     * How many `Sensor_Data` rows this block was summarised from.
     *
     * Every other field here is a summary, and a summary of four readings is indistinguishable
     * from a summary of fifty-two once the raw rows are gone -- which they are, because
     * `deleteAggregated()` purges them the same night. Without this count there is no way to
     * tell a real quiet morning from a morning the phone spent switched off.
     *
     * That distinction matters to Layer 2 specifically. Isolation Forest is unsupervised: it
     * learns "normal" from whatever it is given, so a handful of near-empty blocks teach it that
     * near-empty is ordinary -- and a senior lying motionless on the floor produces a block that
     * looks very like one. The model would learn to shrug at the thing it exists to catch.
     *
     * Used to EXCLUDE thin blocks at training time, never as a feature to train on. Fed in as a
     * feature it would have the model learning about handset uptime rather than about the senior.
     *
     * Expected counts at the 5-minute sampling of CLAUDE.md §4, for wake 10:00 / sleep 23:00:
     * ~52 for each 260-minute waking block, ~132 for the 660-minute night.
     *
     * Nullable because rows written before this column existed cannot be counted retroactively;
     * null means "unknown", which is not the same claim as a number and must not be trusted.
     */
    @ColumnInfo(name = "sample_count") val sampleCount: Int? = null,
    /** Isolation Forest path-length anomaly score — set after the daily IF run, null until then */
    @ColumnInfo(name = "isolation_forest_score") val isolationForestScore: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
