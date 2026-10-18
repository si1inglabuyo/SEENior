package com.pup.seenior.network

import android.content.Context
import android.util.Log
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Baseline
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

    /** Never throws: a missed upload just waits for the next heartbeat. */
    suspend fun syncIfChanged(context: Context, db: SeniorAppDatabase) {
        try {
            val senior = db.seniorDao().getOnboardedSenior() ?: return
            val rows = db.baselineDao().getAllBySeniorOnce(senior.seniorId).map { it.toDto() }
            if (rows.isEmpty()) return

            val signature = signatureOf(rows)
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            SeniorCloudSync(db).withSyncId { syncId ->
                val key = "sent_$syncId"
                if (prefs.getString(key, null) == signature) return@withSyncId
                RetrofitClient.api.syncRoutine(syncId, RoutineSyncRequest(rows))
                prefs.edit().putString(key, signature).apply()
                Log.i(TAG, "Routine fingerprint sent (${rows.size} rows)")
            }
        } catch (e: Exception) {
            // INFO, not WARN: being offline is ordinary.
            Log.i(TAG, "Routine not delivered (${e.javaClass.simpleName})")
        }
    }

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
}
