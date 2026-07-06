package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Alerts",
    foreignKeys = [
        ForeignKey(
            entity = Senior::class,
            parentColumns = ["senior_id"],
            childColumns = ["senior_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("senior_id"), Index("status"), Index("triggered_at"), Index("sync_id")]
)
data class Alert(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "alert_id") val alertId: Int = 0,
    @ColumnInfo(name = "senior_id") val seniorId: Int,
    /**
     * UUID used for cloud sync — anonymous, not the sequential alert_id.
     * Prevents ID collisions across devices and avoids leaking alert volume.
     */
    @ColumnInfo(name = "sync_id") val syncId: String,
    /**
     * What triggered this alert:
     * "inactivity", "movement", "screen_idle", "charging", "sos", "ml_flag", "fall_pattern"
     */
    @ColumnInfo(name = "trigger_type") val triggerType: String,
    /** "low", "medium", or "high" — assigned by the Fuzzy Logic layer */
    @ColumnInfo(name = "risk_level") val riskLevel: String,
    /** "morning", "afternoon", "evening", or "night" — used to build the wellness prompt context message */
    @ColumnInfo(name = "time_block") val timeBlock: String,
    /**
     * Modified Z-Score from Median-MAD (Layer 1). Null when trigger_type is "ml_flag"
     * because Isolation Forest uses path-length score, not z-score.
     */
    @ColumnInfo(name = "deviation_score") val deviationScore: Double? = null,
    /**
     * Anonymous location cluster ID — captured only at alert-trigger time, never raw coordinates.
     * Null if GPS was unavailable or not yet captured.
     */
    @ColumnInfo(name = "location_cluster_id") val locationClusterId: String? = null,
    /**
     * "pending", "self_cancelled", "acknowledged_family", "escalated_barangay",
     * "resolved", or "false_positive"
     */
    @ColumnInfo(name = "status") val status: String = "pending",
    /** JSON array string logging the full escalation timeline for audit purposes */
    @ColumnInfo(name = "escalation_steps") val escalationSteps: String = "[]",
    @ColumnInfo(name = "triggered_at") val triggeredAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null,
    /** Whether this alert's metadata has been synced to the cloud PostgreSQL backend */
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
)
