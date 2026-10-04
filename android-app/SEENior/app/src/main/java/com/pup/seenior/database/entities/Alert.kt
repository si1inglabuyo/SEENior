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
    /** UUID for cloud sync. Anonymous, not the sequential alert_id, to avoid ID collisions and leaking alert volume. */
    @ColumnInfo(name = "sync_id") val syncId: String,
    /** What triggered this alert: "inactivity", "movement", "screen_idle", "charging", "sos", "ml_flag", "fall_pattern" */
    @ColumnInfo(name = "trigger_type") val triggerType: String,
    /** "low", "medium", or "high" — assigned by the Fuzzy Logic layer */
    @ColumnInfo(name = "risk_level") val riskLevel: String,
    /** "morning", "afternoon", "evening" or "night"; used for the wellness prompt's context message. */
    @ColumnInfo(name = "time_block") val timeBlock: String,
    /** Modified Z-Score from Median-MAD (Layer 1). Null for "ml_flag", which uses a path-length score instead. */
    @ColumnInfo(name = "deviation_score") val deviationScore: Double? = null,
    /**
     * Where the phone was when this alert fired, as a geohash cell. Captured only at alert
     * time, never continuously, and never stored as raw coordinates.
     *
     * Not anonymous: a precision-9 cell is ~5 m, finer than GPS error, and identifies a place.
     * It is held under RA 10173 section 12(c) (vital interests). The column keeps its
     * `location_cluster_id` name only because renaming it would need a migration on both
     * databases. Null if GPS was unavailable or not yet captured.
     */
    @ColumnInfo(name = "location_cluster_id") val locationClusterId: String? = null,
    /**
     * "pending", "logged", "self_cancelled", "acknowledged_family", "escalated_barangay",
     * "resolved" or "false_positive".
     *
     * "logged" is the low-risk tier: an anomaly Layer 3 judged not worth telling anyone about.
     * It sits outside every status set the response chain queries, so it raises no prompt,
     * arms no alarm and can't dedupe a real alert away.
     */
    @ColumnInfo(name = "status") val status: String = "pending",
    /** JSON array string logging the full escalation timeline for audit purposes */
    @ColumnInfo(name = "escalation_steps") val escalationSteps: String = "[]",
    @ColumnInfo(name = "triggered_at") val triggeredAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null,
    /** Whether this alert's metadata has been synced to the cloud PostgreSQL backend */
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
)
