package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Senior_Onboarding",
    foreignKeys = [
        ForeignKey(
            entity = Senior::class,
            parentColumns = ["senior_id"],
            childColumns = ["senior_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("senior_id", unique = true)]
)
data class SeniorOnboarding(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "onboarding_id") val onboardingId: Int = 0,
    @ColumnInfo(name = "senior_id") val seniorId: Int,
    /** "HH:mm" — used to define the start of "morning" monitoring */
    @ColumnInfo(name = "wake_time") val wakeTime: String,
    /** "HH:mm" — used to define the start of the "night" suppression window */
    @ColumnInfo(name = "sleep_time") val sleepTime: String,
    @ColumnInfo(name = "has_nap") val hasNap: Boolean,
    /** "HH:mm" — nap suppression window start; null if has_nap is false */
    @ColumnInfo(name = "nap_time") val napTime: String? = null,
    /** Duration in minutes; null if has_nap is false */
    @ColumnInfo(name = "nap_duration_minutes") val napDurationMinutes: Int? = null,
    /** Self-reported activity level: "resting", "light", "moderate", or "active" */
    @ColumnInfo(name = "activity_level") val activityLevel: String,
    /** "en" or "fil" — drives language of all senior-facing prompts */
    @ColumnInfo(name = "language_preference") val languagePreference: String,
    /**
     * False while seed values pre-populate the Baseline table during onboarding.
     * Set to true once real sensor data fully replaces seed values (target: Day 14).
     */
    @ColumnInfo(name = "seed_baseline_generated") val seedBaselineGenerated: Boolean = false,
    @ColumnInfo(name = "onboarding_completed_at") val onboardingCompletedAt: Long = System.currentTimeMillis(),
    /** Null until 14 days of real sensor data have replaced all seed baseline values */
    @ColumnInfo(name = "baseline_ready_at") val baselineReadyAt: Long? = null
)
