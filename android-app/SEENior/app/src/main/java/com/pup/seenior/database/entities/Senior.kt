package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Seniors")
data class Senior(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "senior_id") val seniorId: Int = 0,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "age") val age: Int,
    @ColumnInfo(name = "gender") val gender: String,
    @ColumnInfo(name = "mobile_number") val mobileNumber: String,
    @ColumnInfo(name = "address") val address: String,
    /** Barangay name used for routing alerts to the correct barangay responder */
    @ColumnInfo(name = "barangay") val barangay: String,
    /** "alone", "with_family", or "with_caregiver" */
    @ColumnInfo(name = "living_arrangement") val livingArrangement: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_onboarding_complete") val isOnboardingComplete: Boolean = false,
    /**
     * UUID assigned by the cloud backend on POST /seniors. Null until the senior is first
     * registered with the cloud — registration is LAZY (done the first time an invite code
     * is generated), so onboarding still works fully offline.
     */
    @ColumnInfo(name = "cloud_sync_id") val cloudSyncId: String? = null
)
