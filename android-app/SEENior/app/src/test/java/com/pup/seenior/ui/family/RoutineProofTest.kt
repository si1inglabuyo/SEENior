package com.pup.seenior.ui.family

import com.pup.seenior.network.dto.RoutineRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineProofTest {

    private fun row(feature: String, block: String, median: Double, mad: Double, days: Int = 14, seed: Boolean = false) =
        RoutineRowDto(feature, block, median, mad, days, seed)

    /** One block: still 34 min (MAD 12 min), the way the pasted Morning card reads. */
    private val morning = listOf(
        row("inactivity_duration", "morning", 2040.0, 720.0),
        row("movement_score", "morning", 0.07, 0.01),
        row("step_count", "morning", 900.0, 100.0),
        row("screen_unlock_count", "morning", 4.0, 1.0)
    )

    @Test
    fun theAlertPointIsTheMedianPlusTwoPointFiveOverScaleMads() {
        // 2040 + (2.5 / 0.6745) * 720 = 4708.6 s, about 1 hr 18 min.
        assertEquals(4708.6, RoutineProof.alertAfterSeconds(2040.0, 720.0), 0.1)
    }

    @Test
    fun aTinyMadIsFlooredTheWayTheDetectorFloorsIt() {
        // The detector never divides by less than 300 s, so a 60 s MAD behaves as 300 s.
        assertEquals(2040.0 + 3.7064 * 300.0, RoutineProof.alertAfterSeconds(2040.0, 60.0), 0.1)
    }

    @Test
    fun theWorkingNamesTheNumbersTheCardShows() {
        val b = RoutineProof.build(morning).single()
        assertEquals(100, b.activityPercent)
        assertTrue(RoutineProof.stillLine(b, false)!!.contains("34 min"))
        assertTrue(RoutineProof.stillLine(b, false)!!.contains("14 days"))
        assertTrue(RoutineProof.alertLine(b, false)!!.contains("1 hr 18 min"))
        assertTrue(RoutineProof.alertLine(b, false)!!.contains("0.6745"))
        assertTrue(RoutineProof.phoneLine(b, false)!!.contains("4 unlocks"))
        assertTrue(RoutineProof.phoneLine(b, false)!!.contains("sometimes"))
    }

    @Test
    fun filipinoHasTheSameLines() {
        val b = RoutineProof.build(morning).single()
        assertTrue(RoutineProof.stillLine(b, true)!!.contains("34 min"))
        assertTrue(RoutineProof.alertLine(b, true)!!.contains("1 oras 18 min"))
        assertTrue(RoutineProof.phoneLine(b, true)!!.contains("paminsan-minsan"))
    }

    @Test
    fun aSeedFigureIsSaidToBeASetupAnswer() {
        val seeded = morning.map { it.copy(isSeed = true) }
        val b = RoutineProof.build(seeded).single()
        assertTrue(b.hasSeed)
        assertTrue(RoutineProof.seedLine(b, false)!!.contains("setup"))
        assertNull(RoutineProof.seedLine(RoutineProof.build(morning).single(), false))
    }

    @Test
    fun aMissingStillnessRowLeavesOutTheLinesThatNeedIt() {
        val b = RoutineProof.build(morning.filter { it.featureName != "inactivity_duration" }).single()
        assertNull(RoutineProof.stillLine(b, false))
        assertNull(RoutineProof.alertLine(b, false))
    }
}
