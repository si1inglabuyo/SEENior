package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Baseline",
    foreignKeys = [
        ForeignKey(
            entity = Senior::class,
            parentColumns = ["senior_id"],
            childColumns = ["senior_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("senior_id"), Index("feature_name"), Index("time_block")]
)
data class Baseline(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "baseline_id") val baselineId: Int = 0,
    @ColumnInfo(name = "senior_id") val seniorId: Int,
    /**
     * One of: "inactivity_duration", "movement_score", "screen_idle_duration",
     * "screen_unlock_count", "step_count"
     */
    @ColumnInfo(name = "feature_name") val featureName: String,
    /** "morning", "afternoon", "evening", or "night" */
    @ColumnInfo(name = "time_block") val timeBlock: String,
    /** Median of the feature within the rolling 14-day window for this time block */
    @ColumnInfo(name = "median_value") val medianValue: Double,
    /** Median Absolute Deviation — used for the Modified Z-Score (not std dev) */
    @ColumnInfo(name = "mad_value") val madValue: Double,
    /** Number of real sensor samples that contributed to this baseline entry */
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    /** True = warm-start seed value from onboarding questionnaire; false = built from real data */
    @ColumnInfo(name = "is_seed") val isSeed: Boolean = false,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis()
)
