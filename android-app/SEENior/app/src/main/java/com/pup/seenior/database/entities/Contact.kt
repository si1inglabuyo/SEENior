package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Contacts",
    foreignKeys = [
        ForeignKey(
            entity = Senior::class,
            parentColumns = ["senior_id"],
            childColumns = ["senior_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("senior_id")]
)
data class Contact(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "contact_id") val contactId: Int = 0,
    @ColumnInfo(name = "senior_id") val seniorId: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    /** "family" or "barangay_responder" */
    @ColumnInfo(name = "contact_type") val contactType: String,
    /** "daughter", "son", ... as the family contact described themselves when pairing. Shown
     *  under their name on the SOS screen, so the senior recognises who is being called rather
     *  than reading a bare name. Nullable because the cloud field is. */
    @ColumnInfo(name = "relationship_label") val relationshipLabel: String? = null,
    @ColumnInfo(name = "fcm_token") val fcmToken: String? = null,
    /** Short-lived OTP code used to pair a family contact with a senior — expires in 5 minutes */
    @ColumnInfo(name = "invite_code") val inviteCode: String? = null,
    @ColumnInfo(name = "invite_expires_at") val inviteExpiresAt: Long? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
