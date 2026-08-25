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

    @Query("SELECT EXISTS(SELECT 1 FROM Alerts WHERE senior_id = :seniorId AND trigger_type = :triggerType AND status = 'pending')")
    suspend fun hasPendingAlert(seniorId: Int, triggerType: String): Boolean

    /**
     * An "active" alert is one still being worked through the escalation chain —
     * not yet self-cancelled/resolved/marked false-positive. Used to dedup a fresh
     * anomaly of the same trigger_type into the existing alert instead of spawning
     * a duplicate once the alert has already progressed past "pending".
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
     * [getActiveAlert] is right for the signals that describe a *state* — while a senior is still
     * inactive, every five-minute poll re-detects the same inactivity, and one alert should absorb
     * them all however long it stays open. It is wrong for the ones that describe an *event*. A
     * fall is over the moment it happens, and nothing on this device ever moves an escalated alert
     * out of the open set (the only local status write is "self_cancelled"; the family resolving
     * it in the cloud is never synced back), so an unbounded check meant the first fall a phone
     * ever escalated silently blocked every fall after it, for the life of the install.
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
     * The most recent low-risk note for this signal, for collapsing repeats.
     *
     * Separate from [getRecentActiveAlert] because it asks the opposite question. That one looks
     * for an alert somebody is being told about, so that a second detection joins it instead of
     * spawning a duplicate; this one looks only among rows nobody was told about, so an hour of
     * unremarkable stillness leaves one line in the record rather than twelve.
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

    @Query("UPDATE Alerts SET risk_level = :riskLevel, deviation_score = :deviationScore WHERE alert_id = :alertId")
    suspend fun updateSeverity(alertId: Int, riskLevel: String, deviationScore: Double)


    /** Returns all alerts that haven't been synced to the cloud backend yet */
    @Query("SELECT * FROM Alerts WHERE is_synced = 0 ORDER BY triggered_at ASC")
    suspend fun getUnsyncedAlerts(): List<Alert>

    /**
     * Alerts still awaiting an answer, oldest first.
     *
     * Exists for re-arming the escalation deadline after a reboot: AlarmManager alarms do NOT
     * survive one, so without this every alert open at the moment the phone restarted would
     * wait forever for a wake-up that is never coming.
     */
    @Query("SELECT * FROM Alerts WHERE status = 'pending' ORDER BY triggered_at ASC")
    suspend fun getPendingAlerts(): List<Alert>

    @Query("UPDATE Alerts SET status = :status, resolved_at = :resolvedAt WHERE alert_id = :alertId")
    suspend fun updateStatus(alertId: Int, status: String, resolvedAt: Long? = null)

    @Query("UPDATE Alerts SET escalation_steps = :steps WHERE alert_id = :alertId")
    suspend fun updateEscalationSteps(alertId: Int, steps: String)

    @Query("UPDATE Alerts SET is_synced = 1 WHERE alert_id = :alertId")
    suspend fun markSynced(alertId: Int)

    /**
     * Adopts the sync_id the backend minted for this alert. POST /alerts generates its own
     * UUID and ignores the device's, so without this the same alert carries two different
     * ids and the two sides can never be matched up again — needed the moment the senior's
     * phone wants to read back what the family did with an alert.
     */
    @Query("UPDATE Alerts SET sync_id = :syncId WHERE alert_id = :alertId")
    suspend fun updateSyncId(alertId: Int, syncId: String)

    @Query("UPDATE Alerts SET location_cluster_id = :clusterId WHERE alert_id = :alertId")
    suspend fun updateLocationCluster(alertId: Int, clusterId: String)

    @Query("SELECT * FROM Alerts WHERE senior_id = :seniorId AND risk_level = :riskLevel ORDER BY triggered_at DESC")
    fun getByRiskLevel(seniorId: Int, riskLevel: String): Flow<List<Alert>>
}
