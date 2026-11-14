package com.pup.seenior.ui.demo

import com.pup.seenior.detection.FallCheckMonitor
import com.pup.seenior.detection.FallCheckMonitor.Gate
import com.pup.seenior.detection.FallCheckMonitor.Outcome
import com.pup.seenior.detection.FallCheckMonitor.Status
import com.pup.seenior.detection.FallDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FallAttemptCsvTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun series(vararg pairs: Pair<Float, Float>) =
        Series(pairs.map { it.first }.toFloatArray(), pairs.map { it.second }.toFloatArray())

    private fun snapshot(outcome: Outcome) = FallCheckMonitor.Snapshot(
        statuses = mapOf(Gate.FREE_FALL to Status.PASSED, Gate.IMPACT to Status.PASSED),
        freeFallMs = 360, impactPeak = 31f, rotationPeak = 4f, stillMs = 0, outcome = outcome, message = null,
        config = FallDetector.Config(),
        freeFallStartAtMs = 2_000, freeFallEndAtMs = 2_360, impactAtMs = 2_380, endedAtMs = 12_000, sinceImpactMs = 9_600
    )

    @Test
    fun `header has one column more than the pre-fall recording's sensor columns and ends with Event`() {
        val columns = RecorderCsv.FALL_HEADER.split(",")
        assertEquals(13, columns.size)
        assertEquals("\"Time (s)\"", columns.first())
        assertEquals("\"Event\"", columns.last())
    }

    @Test
    fun `every row has as many fields as the header`() {
        val a = series(0f to 0.1f, 0.02f to 0.2f, 0.04f to 0.3f)
        val rows = RecorderCsv.fallAttemptRows(
            a, a, a, Series.EMPTY, Series.EMPTY, Series.EMPTY, Series.EMPTY, Series.EMPTY, Series.EMPTY, emptyList()
        )
        assertEquals(3, rows.size)
        rows.forEach { assertEquals(13, it.split(",").size) }
    }

    @Test
    fun `a sensor the phone lacks leaves its columns blank, not zero`() {
        val a = series(0f to 1f)
        val row = RecorderCsv.fallAttemptRows(
            a, a, a, Series.EMPTY, Series.EMPTY, Series.EMPTY, Series.EMPTY, Series.EMPTY, Series.EMPTY, emptyList()
        ).single().split(",")
        // time, x, y, z, absolute, then 3 linear + 4 gyroscope + event, all blank
        assertEquals(List(8) { "" }, row.drop(5))
    }

    @Test
    fun `gyroscope and linear columns carry the latest reading at or before each moment`() {
        val a = series(0f to 0f, 0.5f to 0f, 1f to 0f)
        val gx = series(0.4f to 1f)
        val gy = series(0.4f to 2f)
        val gz = series(0.4f to 2f)
        val rows = RecorderCsv.fallAttemptRows(
            a, a, a, Series.EMPTY, Series.EMPTY, Series.EMPTY, gx, gy, gz, emptyList()
        ).map { it.split(",") }
        assertEquals("not yet reported at 0.0 s", "", rows[0][8])
        assertEquals("1.000000", rows[1][8])
        assertEquals("still the 0.4 s reading at 1.0 s", "1.000000", rows[2][8])
        assertEquals("absolute rotation sqrt(1+4+4) = 3", "3.000000", rows[1][11])
    }

    @Test
    fun `events land on the first row at or after their time`() {
        val a = series(0f to 0f, 1f to 0f, 2f to 0f, 3f to 0f)
        val rows = RecorderCsv.fallAttemptRows(
            a, a, a, Series.EMPTY, Series.EMPTY, Series.EMPTY, Series.EMPTY, Series.EMPTY, Series.EMPTY,
            listOf(1.5f to "IMPACT", 1.8f to "SETTLED", 9f to "LATER")
        ).map { it.split(",").last() }
        assertEquals(listOf("", "", "IMPACT;SETTLED", ""), rows)
    }

    @Test
    fun `fall events come out in time order with the confirmation only when it was confirmed`() {
        val confirmed = RecorderCsv.fallEvents(snapshot(Outcome.CONFIRMED), originMs = 0)
        assertEquals(
            listOf("FREE_FALL_START", "FREE_FALL_END", "IMPACT", "SETTLED", "STILL_CONFIRMED"),
            confirmed.map { it.second }
        )
        assertEquals(confirmed.map { it.first }.sorted(), confirmed.map { it.first })

        val notFall = RecorderCsv.fallEvents(snapshot(Outcome.NOT_CONFIRMED), originMs = 0).map { it.second }
        assertFalse("STILL_CONFIRMED" in notFall)
        assertTrue("STOPPED_NOT_A_FALL" in notFall)
    }

    @Test
    fun `event times are relative to the graphs' zero`() {
        val events = RecorderCsv.fallEvents(snapshot(Outcome.CONFIRMED), originMs = 1_000)
        assertEquals(1.0f, events.first { it.second == "FREE_FALL_START" }.first, 0.0001f)
    }

    @Test
    fun `file name says whether the detector called it a fall`() {
        assertEquals("FALLCHECK_20261115_031530_CONFIRMED.csv", RecorderCsv.fallAttemptName("20261115_031530", Outcome.CONFIRMED))
        assertEquals("FALLCHECK_20261115_031530_NOTFALL.csv", RecorderCsv.fallAttemptName("20261115_031530", Outcome.NOT_CONFIRMED))
        assertEquals("FALLCHECK_20261115_031530_LIVE.csv", RecorderCsv.fallAttemptName("20261115_031530", Outcome.NONE))
    }

    private fun touch(dir: java.io.File, vararg names: String) = names.forEach { java.io.File(dir, it).writeText("x") }

    @Test
    fun `each screen sees only its own recordings`() {
        val dir = tmp.newFolder("recordings")
        touch(dir, "P01_WALKHAND_R1.csv", "P01_WALKHAND_R2.csv", "FALLCHECK_20261115_031530_CONFIRMED.csv", "notes.txt")

        assertEquals(
            setOf("P01_WALKHAND_R1.csv", "P01_WALKHAND_R2.csv"),
            RecorderCsv.recordings(dir, fallAttempts = false).map { it.name }.toSet()
        )
        assertEquals(
            setOf("FALLCHECK_20261115_031530_CONFIRMED.csv"),
            RecorderCsv.recordings(dir, fallAttempts = true).map { it.name }.toSet()
        )
    }

    @Test
    fun `deleting the pre-fall recordings leaves the fall attempts and everything else`() {
        val dir = tmp.newFolder("recordings")
        touch(dir, "P01_WALKHAND_R1.csv", "P01_WALKPOCKET_R1.csv", "FALLCHECK_20261115_031530_CONFIRMED.csv", "notes.txt")
        java.io.File(dir, "sub").mkdir()

        assertEquals(2, RecorderCsv.deleteAll(dir, fallAttempts = false))
        assertEquals(setOf("FALLCHECK_20261115_031530_CONFIRMED.csv", "notes.txt", "sub"), dir.list()!!.toSet())
    }

    @Test
    fun `deleting the fall attempts leaves the pre-fall recordings`() {
        val dir = tmp.newFolder("recordings")
        touch(dir, "P01_WALKHAND_R1.csv", "FALLCHECK_20261115_031530_CONFIRMED.csv", "FALLCHECK_20261115_031600_NOTFALL.csv")

        assertEquals(2, RecorderCsv.deleteAll(dir, fallAttempts = true))
        assertEquals(setOf("P01_WALKHAND_R1.csv"), dir.list()!!.toSet())
    }

    @Test
    fun `a participant cannot be mistaken for a fall attempt`() {
        // A participant code is cut to 8 characters, so even a participant named FALLCHECK is saved as
        // FALLCHEC_..., which can never match the nine-character FALLCHECK_ prefix of a fall attempt.
        assertTrue(RecorderCsv.isFallAttempt(RecorderCsv.fallAttemptName("20261115_031530", Outcome.NONE)))
        assertFalse(RecorderCsv.isFallAttempt(RecorderCsv.fileName("FALLCHECK", "WALKHAND", 1)))
    }

    @Test
    fun `delete all on an empty or missing folder removes nothing and does not fail`() {
        assertEquals(0, RecorderCsv.deleteAll(tmp.newFolder("empty"), fallAttempts = false))
        assertEquals(0, RecorderCsv.deleteAll(java.io.File(tmp.root, "does-not-exist"), fallAttempts = true))
    }
}
