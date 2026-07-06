package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pup.seenior.database.entities.Alert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(alert: Alert): Long

    @Update
    suspend fun update(alert: Alert)

    @Delete
    suspend fun delete(alert: Alert)

    @Query("SELECT * FROM Alerts WHERE senior_id = :seniorId ORDER BY triggered_at DESC")
    fun getAllBySenior(seniorId: Int): Flow<List<Alert>>

    @Query("SELECT * FROM Alerts WHERE alert_id = :alertId")
    suspend fun getById(alertId: Int): Alert?

    @Query("SELECT * FROM Alerts WHERE sync_id = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Alert?

    /**
     * Returns alerts that are still "pending" or "escalated_barangay" and haven't been
     * acknowledged or resolved — used by the escalation engine to drive the response chain.
     */
    @Query("""
        SELECT * FROM Alerts
        WHERE senior_id = :seniorId
          AND status IN ('pending', 'escalated_barangay')
        ORDER BY triggered_at DESC
    """)
    fun getUnacknowledgedAlerts(seniorId: Int): Flow<List<Alert>>

    /** Returns all alerts that haven't been synced to the cloud backend yet */
    @Query("SELECT * FROM Alerts WHERE is_synced = 0 ORDER BY triggered_at ASC")
    suspend fun getUnsyncedAlerts(): List<Alert>

    @Query("UPDATE Alerts SET status = :status, resolved_at = :resolvedAt WHERE alert_id = :alertId")
    suspend fun updateStatus(alertId: Int, status: String, resolvedAt: Long? = null)

    @Query("UPDATE Alerts SET escalation_steps = :steps WHERE alert_id = :alertId")
    suspend fun updateEscalationSteps(alertId: Int, steps: String)

    @Query("UPDATE Alerts SET is_synced = 1 WHERE alert_id = :alertId")
    suspend fun markSynced(alertId: Int)

    @Query("UPDATE Alerts SET location_cluster_id = :clusterId WHERE alert_id = :alertId")
    suspend fun updateLocationCluster(alertId: Int, clusterId: String)

    @Query("SELECT * FROM Alerts WHERE senior_id = :seniorId AND risk_level = :riskLevel ORDER BY triggered_at DESC")
    fun getByRiskLevel(seniorId: Int, riskLevel: String): Flow<List<Alert>>
}
