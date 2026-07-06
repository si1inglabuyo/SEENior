package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "False_Positives",
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
    indices = [Index("alert_id"), Index("senior_id")]
)
data class FalsePositive(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "fp_id") val fpId: Int = 0,
    @ColumnInfo(name = "alert_id") val alertId: Int,
    @ColumnInfo(name = "senior_id") val seniorId: Int,
    /** Who reported the false positive: "senior" or "family" */
    @ColumnInfo(name = "reported_by") val reportedBy: String,
    @ColumnInfo(name = "reported_at") val reportedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "notes") val notes: String? = null
)
