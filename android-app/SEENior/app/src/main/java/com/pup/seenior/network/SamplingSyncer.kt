package com.pup.seenior.network

import android.content.Context
import android.util.Log
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.network.dto.SamplingDayDto
import com.pup.seenior.network.dto.SamplingSyncRequest
import java.time.LocalDate

/**
 * Reports how many 5-minute readings the phone collected on each finished day, so the team can
 * show the sampling cycle ran and see which phones lose hours to the operating system.
 *
 * Counts only, per time block: no reading, and nothing about what the senior did, only whether the
 * phone was collecting (about 288 a day when it runs all day). Built from the `sample_count` the
 * nightly job already stores in `Daily_Aggregates`; `Sensor_Data` and the aggregates themselves
 * never leave. Rides the heartbeat like [RoutineSyncer], and a signature of what was last sent
 * keeps it to about one upload a day. Never throws: a missed upload waits for the next heartbeat.
 */
object SamplingSyncer {

    private const val TAG = "SamplingSyncer"
    private const val PREFS = "sampling_sync"

    /** What the cloud keeps (migration 0028). */
    const val DAYS = 30L

    suspend fun syncIfChanged(context: Context, db: SeniorAppDatabase) {
        try {
            val senior = db.seniorDao().getOnboardedSenior() ?: return
            val days = build(db.dailyAggregateDao().getAllOnce(senior.seniorId), LocalDate.now())
            if (days.isEmpty()) return

            val signature = signatureOf(days)
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            SeniorCloudSync(db).withCachedSyncId { syncId ->
                val key = "sent_$syncId"
                if (prefs.getString(key, null) != signature) {
                    RetrofitClient.api.syncSampling(syncId, SamplingSyncRequest(days))
                    prefs.edit().putString(key, signature).apply()
                    Log.i(TAG, "Sampling counts sent (${days.size} days)")
                }
            }
        } catch (e: Exception) {
            // INFO, not WARN: being offline, or having no cloud id yet, is ordinary.
            Log.i(TAG, "Sampling counts not delivered (${e.javaClass.simpleName})")
        }
    }

    /**
     * One entry per finished day in the last [DAYS] days, with the readings counted in each block.
     * Today is left out (its blocks are still filling). A block with no row collected nothing, so
     * it counts as 0. A day with any row whose count is unknown (older rows) is skipped rather
     * than guessed.
     */
    internal fun build(aggregates: List<DailyAggregate>, today: LocalDate): List<SamplingDayDto> {
        val earliest = today.minusDays(DAYS).toString()
        return aggregates
            .filter { it.date >= earliest && it.date < today.toString() }
            .groupBy { it.date }
            .mapNotNull { (day, rows) ->
                if (rows.any { it.sampleCount == null }) return@mapNotNull null
                fun total(block: String) = rows.filter { it.timeBlock == block }.sumOf { it.sampleCount ?: 0 }
                SamplingDayDto(
                    day = day,
                    morning = total("morning"),
                    afternoon = total("afternoon"),
                    evening = total("evening"),
                    night = total("night")
                )
            }
            .sortedBy { it.day }
    }

    internal fun signatureOf(days: List<SamplingDayDto>): String =
        days.joinToString("|") { "${it.day}:${it.morning}:${it.afternoon}:${it.evening}:${it.night}" }
            .hashCode()
            .toString()
}
