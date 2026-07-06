package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pup.seenior.database.entities.PendingAlert
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingAlertDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pendingAlert: PendingAlert): Long

    @Update
    suspend fun update(pendingAlert: PendingAlert)

    @Delete
    suspend fun delete(pendingAlert: PendingAlert)

    @Query("SELECT * FROM Pending_Alerts WHERE senior_id = :seniorId ORDER BY notification_sent_at DESC")
    fun getAllBySenior(seniorId: Int): Flow<List<PendingAlert>>

    @Query("SELECT * FROM Pending_Alerts WHERE pending_id = :pendingId")
    suspend fun getById(pendingId: Int): PendingAlert?

    @Query("SELECT * FROM Pending_Alerts WHERE alert_id = :alertId ORDER BY notification_sent_at ASC")
    suspend fun getByAlertId(alertId: Int): List<PendingAlert>

    /** Returns all open (not yet resolved) pending entries across all escalation tiers */
    @Query("SELECT * FROM Pending_Alerts WHERE senior_id = :seniorId AND is_resolved = 0 ORDER BY notification_sent_at ASC")
    fun getOpenPendingAlerts(seniorId: Int): Flow<List<PendingAlert>>

    /** Returns the open pending entry for a specific tier — drives the escalation state machine */
    @Query("""
        SELECT * FROM Pending_Alerts
        WHERE alert_id = :alertId
          AND escalation_tier = :tier
          AND is_resolved = 0
        LIMIT 1
    """)
    suspend fun getOpenByAlertAndTier(alertId: Int, tier: String): PendingAlert?

    /** Finds all pending entries whose response deadline has passed and are still unresolved */
    @Query("""
        SELECT * FROM Pending_Alerts
        WHERE is_resolved = 0
          AND response_type IS NULL
          AND response_deadline < :nowMillis
    """)
    suspend fun getExpiredPendingAlerts(nowMillis: Long): List<PendingAlert>

    @Query("UPDATE Pending_Alerts SET response_type = :responseType, responded_at = :respondedAt, is_resolved = 1 WHERE pending_id = :pendingId")
    suspend fun resolveEntry(pendingId: Int, responseType: String, respondedAt: Long)
}
