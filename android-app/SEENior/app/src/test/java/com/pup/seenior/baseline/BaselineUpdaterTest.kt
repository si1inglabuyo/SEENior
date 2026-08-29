package com.pup.seenior.baseline

import com.pup.seenior.database.dao.BaselineDao
import com.pup.seenior.database.dao.DailyAggregateDao
import com.pup.seenior.database.entities.Baseline
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.SeniorOnboarding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the seed-to-real hand-over, which is otherwise invisible for two weeks: the bug this
 * replaced wrote `median = 0, MAD = 0` from three partial days and no test could see it, because
 * nothing fails until a senior is being asked if she is safe every few minutes.
 */
class BaselineUpdaterTest {

    private val onboarding = SeniorOnboarding(
        seniorId = 1,
        wakeTime = "06:00",
        sleepTime = "22:00",
        hasNap = false,
        activityLevel = "light",
        languagePreference = "en",
    )

    /** The seed this senior's evening starts from: 30 minutes idle, MAD `max(30*0.4, 5) = 12`, in seconds. */
    private val seedEveningScreenIdle = 30.0 * 60
    private val seedEveningMad = seedEveningScreenIdle * 0.4

    private fun evening(day: Int, screenIdleSeconds: Long) = DailyAggregate(
        seniorId = 1,
        date = "2026-08-%02d".format(day),
        timeBlock = "evening",
        avgMovementScore = 0.30,
        totalInactivityDuration = 1800,
        avgScreenIdleDuration = screenIdleSeconds,
        totalScreenUnlocks = 5,
        totalSteps = 400,
        isChargingMajority = false,
    )

    private fun run(days: List<DailyAggregate>): FakeBaselineDao {
        val baselineDao = FakeBaselineDao()
        runBlocking {
            BaselineUpdater.updateForSenior(1, onboarding, baselineDao, FakeDailyAggregateDao(days))
        }
        return baselineDao
    }

    private fun stored(dao: FakeBaselineDao, featureName: String): Baseline? =
        dao.rows["$featureName|evening"]

    @Test
    fun `three flat days cannot flatten the baseline`() {
        // The exact shape that broke Agnes's phone: a handful of days recorded while she happened
        // to be holding it, so every evening reads "screen never idle, and never varies".
        val dao = run((1..3).map { evening(it, 0) })
        val row = requireNotNull(stored(dao, "screen_idle_duration"))

        assertTrue("median collapsed to ${row.medianValue}", row.medianValue > seedEveningScreenIdle / 2)
        assertTrue("MAD collapsed to ${row.madValue}", row.madValue > seedEveningMad / 2)
        assertTrue("still mostly the questionnaire's answer", row.isSeed)
        assertEquals(3, row.sampleCount)
    }

    @Test
    fun `no baseline may ever be stored with a MAD below its floor`() {
        // Fourteen identical days: real MAD is genuinely 0, and the blend cannot rescue it.
        val dao = run((1..14).map { evening(it, 4000) })
        val row = requireNotNull(stored(dao, "screen_idle_duration"))

        assertEquals(SeedBaselineGenerator.MIN_MAD_FLOOR.getValue("screen_idle_duration"), row.madValue, 0.001)
    }

    @Test
    fun `fourteen days hands over fully to the real data`() {
        val dao = run((1..14).map { evening(it, 4000) })
        val row = requireNotNull(stored(dao, "screen_idle_duration"))

        assertEquals(4000.0, row.medianValue, 0.001)
        assertFalse("hand-over is complete at day 14", row.isSeed)
    }

    @Test
    fun `the hand-over is gradual, not a cliff`() {
        val atSeven = requireNotNull(stored(run((1..7).map { evening(it, 4000) }), "screen_idle_duration"))
        val atTwelve = requireNotNull(stored(run((1..12).map { evening(it, 4000) }), "screen_idle_duration"))

        // Seed 1800 -> real 4000, so each extra day should move the median further along.
        assertTrue(atSeven.medianValue > seedEveningScreenIdle)
        assertTrue(atTwelve.medianValue > atSeven.medianValue)
        assertTrue(atTwelve.medianValue < 4000.0)
    }

    @Test
    fun `two days is too few to write anything`() {
        val dao = run((1..2).map { evening(it, 0) })
        assertNull(stored(dao, "screen_idle_duration"))
    }

    private class FakeBaselineDao : BaselineDao {
        val rows = mutableMapOf<String, Baseline>()

        private fun key(featureName: String, timeBlock: String) = "$featureName|$timeBlock"

        override suspend fun insert(baseline: Baseline): Long {
            rows[key(baseline.featureName, baseline.timeBlock)] = baseline
            return rows.size.toLong()
        }

        override suspend fun insertAll(baselines: List<Baseline>): List<Long> = baselines.map { insert(it) }

        override suspend fun update(baseline: Baseline) { insert(baseline) }

        override suspend fun delete(baseline: Baseline) {
            rows.remove(key(baseline.featureName, baseline.timeBlock))
        }

        override fun getAllBySenior(seniorId: Int): Flow<List<Baseline>> = flowOf(rows.values.toList())

        override suspend fun getById(baselineId: Int): Baseline? = null

        override suspend fun getBaselineByFeatureAndTimeBlock(
            seniorId: Int,
            featureName: String,
            timeBlock: String
        ): Baseline? = rows[key(featureName, timeBlock)]

        override suspend fun getSeedBaselines(seniorId: Int): List<Baseline> = rows.values.filter { it.isSeed }

        override suspend fun getRealBaselineCount(seniorId: Int): Int = rows.values.count { !it.isSeed }

        override suspend fun deleteAllForSenior(seniorId: Int) { rows.clear() }

        override suspend fun deleteByFeatureAndTimeBlock(seniorId: Int, featureName: String, timeBlock: String) {
            rows.remove(key(featureName, timeBlock))
        }
    }

    private class FakeDailyAggregateDao(private val days: List<DailyAggregate>) : DailyAggregateDao {
        override suspend fun getRecentByTimeBlock(seniorId: Int, timeBlock: String, days: Int): List<DailyAggregate> =
            this.days.filter { it.timeBlock == timeBlock }.takeLast(days)

        override suspend fun insert(dailyAggregate: DailyAggregate): Long = 0
        override suspend fun update(dailyAggregate: DailyAggregate) = Unit
        override suspend fun delete(dailyAggregate: DailyAggregate) = Unit
        override fun getAllBySenior(seniorId: Int): Flow<List<DailyAggregate>> = flowOf(days)
        override suspend fun getById(aggregateId: Int): DailyAggregate? = null
        override suspend fun getByDate(seniorId: Int, date: String): List<DailyAggregate> =
            days.filter { it.date == date }
        override suspend fun deleteByDateAndTimeBlock(seniorId: Int, date: String, timeBlock: String) = Unit
        override suspend fun getRecentDays(seniorId: Int, days: Int): List<DailyAggregate> = this.days
        override suspend fun getWithoutIsolationForestScore(seniorId: Int): List<DailyAggregate> = emptyList()
        override suspend fun updateIsolationForestScore(aggregateId: Int, score: Double) = Unit
    }
}
