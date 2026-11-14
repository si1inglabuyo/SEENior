package com.pup.seenior.network

import android.content.Context
import android.util.Log
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.baseline.BaselineHistoryRecorder
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.BaselineHistory
import java.time.LocalDate
import com.pup.seenior.network.dto.RoutineHistoryRowDto
import com.pup.seenior.network.dto.RoutineHistorySyncRequest
import com.pup.seenior.network.dto.RoutineRowDto
import com.pup.seenior.network.dto.RoutineSyncRequest

/**
 * Sends the senior's Routine Fingerprint to the cloud so the family app and the barangay
 * dashboard can show it (spec section 11: a 14-day median and MAD per feature per time block,
 * 20 rows at most; never `Sensor_Data`, never `Daily_Aggregates`).
 *
 * Called from the heartbeat, which already runs every 15 minutes and handles the senior's cloud
 * identity. The fingerprint only moves when the nightly job rewrites it, so a signature of what
 * was last sent keeps this to about one upload a day. Remembered per sync_id, so a re-registered
 * senior uploads again.
 */
object RoutineSyncer {

    private const val TAG = "RoutineSyncer"
    private const val PREFS = "routine_sync"

    /** What the cloud keeps (migration 0024). Sending more would only be trimmed there. */
    private const val HISTORY_DAYS = 30L

    /** Never throws: a missed upload just waits for the next heartbeat. */
    suspend fun syncIfChanged(context: Context, db: SeniorAppDatabase) {
        try {
            val senior = db.seniorDao().getOnboardedSenior() ?: return
            keepHistory(db, senior.seniorId)
            val rows = db.baselineDao().getAllBySeniorOnce(senior.seniorId).map { it.toDto() }
            if (rows.isEmpty()) return

            val signature = signatureOf(rows)
            val history = db.baselineHistoryDao()
                .since(senior.seniorId, LocalDate.now().minusDays(HISTORY_DAYS).toString())
                .map { it.toDto() }
            val historySignature = historySignatureOf(history)
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            SeniorCloudSync(db).withSyncId { syncId ->
                val key = "sent_$syncId"
                if (prefs.getString(key, null) != signature) {
                    RetrofitClient.api.syncRoutine(syncId, RoutineSyncRequest(rows))
                    prefs.edit().putString(key, signature).apply()
                    Log.i(TAG, "Routine fingerprint sent (${rows.size} rows)")
                }
                val historyKey = "sent_history_$syncId"
                if (history.isNotEmpty() && prefs.getString(historyKey, null) != historySignature) {
                    RetrofitClient.api.syncRoutineHistory(syncId, RoutineHistorySyncRequest(history))
                    prefs.edit().putString(historyKey, historySignature).apply()
                    Log.i(TAG, "Routine history sent (${history.size} rows)")
                }
            }
        } catch (e: Exception) {
            // INFO, not WARN: being offline is ordinary.
            Log.i(TAG, "Routine not delivered (${e.javaClass.simpleName})")
        }
    }

    /**
     * Makes sure the phone has a daily copy to send: fills the earlier days once from its stored
     * aggregates, then saves today's if the nightly job has not yet. Never throws; a failure just
     * means the card has less to show.
     */
    private suspend fun keepHistory(db: SeniorAppDatabase, seniorId: Int) {
        try {
            val onboarding = db.seniorOnboardingDao().getBySeniorId(seniorId) ?: return
            BaselineHistoryRecorder.backfillIfEmpty(
                seniorId, onboarding, db.dailyAggregateDao(), db.baselineHistoryDao()
            )
            BaselineHistoryRecorder.ensureToday(
                seniorId, LocalDate.now(), db.baselineDao(), db.baselineHistoryDao()
            )
        } catch (e: Exception) {
            Log.i(TAG, "Routine history not kept (${e.javaClass.simpleName})")
        }
    }

    private fun BaselineHistory.toDto() = RoutineHistoryRowDto(
        day = date,
        featureName = featureName,
        timeBlock = timeBlock,
        medianValue = medianValue,
        madValue = madValue,
        sampleCount = sampleCount,
        isSeed = isSeed,
        isEstimate = isEstimate
    )

    private fun Baseline.toDto() = RoutineRowDto(
        featureName = featureName,
        timeBlock = timeBlock,
        medianValue = medianValue,
        madValue = madValue,
        sampleCount = sampleCount,
        isSeed = isSeed
    )

    /** Order-independent and ignores `last_updated`, which changes without the numbers changing. */
    internal fun signatureOf(rows: List<RoutineRowDto>): String =
        rows.sortedWith(compareBy({ it.timeBlock }, { it.featureName }))
            .joinToString("|") {
                "${it.timeBlock}:${it.featureName}:${it.medianValue}:${it.madValue}:${it.sampleCount}:${it.isSeed}"
            }
            .hashCode()
            .toString()

    /** Same idea as [signatureOf]: a changed day, or an estimate replaced by a saved copy, re-sends. */
    internal fun historySignatureOf(rows: List<RoutineHistoryRowDto>): String =
        rows.sortedWith(compareBy({ it.day }, { it.timeBlock }, { it.featureName }))
            .joinToString("|") {
                "${it.day}:${it.timeBlock}:${it.featureName}:${it.medianValue}:${it.madValue}:${it.sampleCount}:${it.isSeed}:${it.isEstimate}"
            }
            .hashCode()
            .toString()
}
