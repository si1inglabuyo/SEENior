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

    /** Alerts still "pending" or "escalated_barangay" that haven't been acknowledged or resolved. Drives the escalation chain. */
    @Query("""
        SELECT * FROM Alerts
        WHERE senior_id = :seniorId
          AND status IN ('pending', 'escalated_barangay')
        ORDER BY triggered_at DESC
    """)
    fun getUnacknowledgedAlerts(seniorId: Int): Flow<List<Alert>>

    @Query("SELECT EXISTS(SELECT 1 FROM Alerts WHERE senior_id = :seniorId AND trigger_type = :triggerType AND status = 'pending')")
    suspend fun hasPendingAlert(seniorId: Int, triggerType: String): Boolean

    /**
     * An "active" alert is one still in the escalation chain (not self-cancelled, resolved or
     * false-positive). Used to fold a fresh anomaly of the same trigger_type into the existing
     * alert instead of creating a duplicate.
     */
    @Query("""
        SELECT * FROM Alerts
        WHERE senior_id = :seniorId AND trigger_type = :triggerType
          AND status IN ('pending', 'acknowledged_family', 'escalated_barangay')
        LIMIT 1
    """)
    suspend fun getActiveAlert(seniorId: Int, triggerType: String): Alert?

    /**
     * The same dedup check, but only against alerts raised at or after [notBefore].
     *
     * [getActiveAlert] suits signals that describe a state (inactivity), where one alert should
     * absorb every repeat. It is wrong for events like a fall: nothing on the device moves an
     * escalated alert out of the open set, so an unbounded check would block every fall after
     * the first.
     */
    @Query("""
        SELECT * FROM Alerts
        WHERE senior_id = :seniorId AND trigger_type = :triggerType
          AND status IN ('pending', 'acknowledged_family', 'escalated_barangay')
          AND triggered_at >= :notBefore
        LIMIT 1
    """)
    suspend fun getRecentActiveAlert(seniorId: Int, triggerType: String, notBefore: Long): Alert?

    /**
     * The most recent low-risk note for this signal, for collapsing repeats. Separate from
     * [getRecentActiveAlert], which looks at alerts somebody is told about; this looks only
     * among rows nobody was told about.
     */
    @Query("""
        SELECT * FROM Alerts
        WHERE senior_id = :seniorId AND trigger_type = :triggerType
          AND status = 'logged'
          AND triggered_at >= :notBefore
        ORDER BY triggered_at DESC
        LIMIT 1
    """)
    suspend fun getRecentLoggedAlert(seniorId: Int, triggerType: String, notBefore: Long): Alert?

    /**
     * Alerts the senior closed on this phone that also exist in the cloud, so a self-cancel
     * that couldn't reach the server can be retried. The caller decides from the audit
     * timeline whether each still needs sending (not expressible in SQL on a JSON column).
     */
    @Query("""
        SELECT * FROM Alerts
        WHERE senior_id = :seniorId AND status = 'self_cancelled' AND is_synced = 1
        ORDER BY triggered_at DESC
    """)
    suspend fun getSelfCancelledSyncedAlerts(seniorId: Int): List<Alert>

    /**
     * Open alerts that already have a cloud row, for repairing metadata the server wasn't told
     * about (like a severity upgrade made offline). Same three statuses as [getActiveAlert].
     */
    @Query("""
        SELECT * FROM Alerts
        WHERE senior_id = :seniorId
          AND is_synced = 1
          AND status IN ('pending', 'acknowledged_family', 'escalated_barangay')
        ORDER BY triggered_at DESC
    """)
    suspend fun getOpenSyncedAlerts(seniorId: Int): List<Alert>

    /**
     * The z-scores of this signal's recent alerts in one time block that were closed as false
     * alarms: the senior answered "Ligtas po ako" / "I'm fine now" (`self_cancelled`), or
     * family or the barangay marked it `false_positive` (carried back by the closed-alerts poll).
     *
     * Read by [com.pup.seenior.detection.FalseAlarmTolerance]. Derived from Alerts, not
     * False_Positives, to keep one source of truth. `logged` rows are excluded, or the
     * loosening could feed on itself.
     */
    @Query("""
        SELECT deviation_score FROM Alerts
        WHERE senior_id = :seniorId AND trigger_type = :triggerType AND time_block = :timeBlock
          AND status IN ('self_cancelled', 'false_positive')
          AND deviation_score IS NOT NULL
          AND triggered_at >= :since
    """)
    suspend fun getToleratedDeviationScores(
        seniorId: Int,
        triggerType: String,
        timeBlock: String,
        since: Long
    ): List<Double>

    @Query("UPDATE Alerts SET risk_level = :riskLevel, deviation_score = :deviationScore WHERE alert_id = :alertId")
    suspend fun updateSeverity(alertId: Int, riskLevel: String, deviationScore: Double)


    /** Returns all alerts that haven't been synced to the cloud backend yet */
    @Query("SELECT * FROM Alerts WHERE is_synced = 0 ORDER BY triggered_at ASC")
    suspend fun getUnsyncedAlerts(): List<Alert>

    /** Alerts still awaiting an answer, oldest first. Used to re-arm deadlines after a reboot, since alarms don't survive one. */
    @Query("SELECT * FROM Alerts WHERE status = 'pending' ORDER BY triggered_at ASC")
    suspend fun getPendingAlerts(): List<Alert>

    @Query("UPDATE Alerts SET status = :status, resolved_at = :resolvedAt WHERE alert_id = :alertId")
    suspend fun updateStatus(alertId: Int, status: String, resolvedAt: Long? = null)

    @Query("UPDATE Alerts SET escalation_steps = :steps WHERE alert_id = :alertId")
    suspend fun updateEscalationSteps(alertId: Int, steps: String)

    @Query("UPDATE Alerts SET is_synced = 1 WHERE alert_id = :alertId")
    suspend fun markSynced(alertId: Int)

    /**
     * Adopts the sync_id the backend minted for this alert. POST /alerts generates its own UUID
     * and ignores the device's, so this is needed to match the two copies later.
     */
    @Query("UPDATE Alerts SET sync_id = :syncId WHERE alert_id = :alertId")
    suspend fun updateSyncId(alertId: Int, syncId: String)

    @Query("UPDATE Alerts SET location_cluster_id = :clusterId WHERE alert_id = :alertId")
    suspend fun updateLocationCluster(alertId: Int, clusterId: String)

    @Query("SELECT * FROM Alerts WHERE senior_id = :seniorId AND risk_level = :riskLevel ORDER BY triggered_at DESC")
    fun getByRiskLevel(seniorId: Int, riskLevel: String): Flow<List<Alert>>

    /** Every alert id on this device, used to cancel armed alarms before the account is wiped. */
    @Query("SELECT alert_id FROM Alerts")
    suspend fun getAllAlertIds(): List<Int>
}
