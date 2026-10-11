package com.pup.seenior.detection

import com.pup.seenior.database.dao.AlertDao

/**
 * Reads the evidence [FallTolerance] needs out of the `Alerts` table. Kept apart from
 * [FallTolerance] so that object stays free of Room and a JUnit test can drive it with plain
 * numbers. Same source of truth as the Layer 1 tolerance: derived from Alerts, nothing stored.
 */
object FallToleranceLoader {

    suspend fun load(
        dao: AlertDao,
        seniorId: Int,
        base: FallDetector.Config,
        now: Long = System.currentTimeMillis()
    ): FallTolerance.Result {
        val since = FallTolerance.windowStart(now)
        return FallTolerance.resolve(
            base = base,
            falseAlarms = dao.getFallFalseAlarmCount(seniorId, since),
            realAlerts = dao.getFallConfirmedCount(seniorId, since)
        )
    }
}
