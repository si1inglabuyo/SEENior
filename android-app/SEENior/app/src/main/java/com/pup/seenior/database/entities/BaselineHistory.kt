package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One day's copy of a [Baseline] cell, so the app can show how a routine was learned instead of
 * only where it stands now (migration 9 -> 10). Same figures as the fingerprint itself, a median
 * and a MAD, never a sensor reading.
 *
 * [isEstimate] separates two kinds of row. `false` is a copy the phone saved on that day, the
 * fingerprint it actually held and used. `true` is a day worked out afterwards from the stored
 * `Daily_Aggregates` with today's rules, because the older fingerprints were overwritten and
 * never saved. The apps say so in plain words; the numbers of an estimate are close but are not
 * what the detector used that day (the thin-block filter arrived 2026-09-17 and the 0.6745 seed
 * scale 2026-11-14).
 */
@Entity(
    tableName = "Baseline_History",
    foreignKeys = [
        ForeignKey(
            entity = Senior::class,
            parentColumns = ["senior_id"],
            childColumns = ["senior_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("senior_id"),
        Index(value = ["senior_id", "date", "feature_name", "time_block"], unique = true)
    ]
)
data class BaselineHistory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "history_id") val historyId: Int = 0,
    @ColumnInfo(name = "senior_id") val seniorId: Int,
    /** ISO date string "YYYY-MM-DD", the phone's local calendar day. */
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "feature_name") val featureName: String,
    @ColumnInfo(name = "time_block") val timeBlock: String,
    @ColumnInfo(name = "median_value") val medianValue: Double,
    @ColumnInfo(name = "mad_value") val madValue: Double,
    @ColumnInfo(name = "sample_count") val sampleCount: Int,
    /** Still partly the questionnaire's answer on that day. */
    @ColumnInfo(name = "is_seed") val isSeed: Boolean,
    /** True = worked out afterwards; false = saved by the phone on that day. */
    @ColumnInfo(name = "is_estimate") val isEstimate: Boolean
)
