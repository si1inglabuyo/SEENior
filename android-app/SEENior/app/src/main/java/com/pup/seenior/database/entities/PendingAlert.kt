package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Pending_Alerts",
    foreignKeys = [
        ForeignKey(
            entity = Alert::class,
            parentColumns = ["alert_id"],
            childColumns = ["alert_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Senior::class,
            parentColumns = ["senior_id"],
            childColumns = ["senior_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("alert_id"), Index("senior_id"), Index("is_resolved")]
)
data class PendingAlert(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "pending_id") val pendingId: Int = 0,
    @ColumnInfo(name = "alert_id") val alertId: Int,
    @ColumnInfo(name = "senior_id") val seniorId: Int,
    /** Which escalation tier this pending entry represents: "senior", "family", or "barangay" */
    @ColumnInfo(name = "escalation_tier") val escalationTier: String,
    @ColumnInfo(name = "notification_sent_at") val notificationSentAt: Long,
    /** Epoch millis deadline by which a response is required before auto-escalating */
    @ColumnInfo(name = "response_deadline") val responseDeadline: Long,
    @ColumnInfo(name = "responded_at") val respondedAt: Long? = null,
    /**
     * "safe", "need_help", "acknowledged", "escalated", or "no_response"
     * Null while still awaiting a response.
     */
    @ColumnInfo(name = "response_type") val responseType: String? = null,
    @ColumnInfo(name = "is_resolved") val isResolved: Boolean = false
)
