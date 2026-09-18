package com.pup.seenior.detection

import com.pup.seenior.baseline.SeedBaselineGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Validates Layer 3 by driving it with known inputs, per CLAUDE.md §10.
 *
 * The claim this layer makes is that the *same* deviation deserves a different answer depending on
 * when it arrives, so the tests that matter are the pairs: one z-score, two hours, two verdicts.
 * A classifier that merely re-derived the old `z >= 3.5` cutoff would pass a single-input test and
 * fail every one of those.
 */
class FuzzyRiskClassifierTest {

    private fun classify(z: Double, rest: Double) =
        FuzzyRiskClassifier.classify(FuzzyRiskClassifier.Inputs(z, rest))

    // ---------------------------------------------------------------- the pairs

    @Test
    fun `the same extreme deviation is high awake and medium asleep`() {
        val z = 5.0
        assertEquals(FuzzyRiskClassifier.Risk.HIGH, classify(z, rest = 0.0))
        assertEquals(FuzzyRiskClassifier.Risk.MEDIUM, classify(z, rest = 1.0))
    }

    @Test
    fun `the same moderate deviation is high awake and low asleep`() {
        val z = 3.6
        assertEquals(FuzzyRiskClassifier.Risk.HIGH, classify(z, rest = 0.0))
        assertEquals(FuzzyRiskClassifier.Risk.LOW, classify(z, rest = 1.0))
    }

    @Test
    fun `a mild deviation still asks the senior during waking hours`() {
        // CLAUDE.md §5: 2.5 <= z < 3.5 is a moderate anomaly and triggers the wellness prompt.
        // Context may quieten it at night, but it must not be silent in the middle of the day.
        assertEquals(FuzzyRiskClassifier.Risk.MEDIUM, classify(2.6, rest = 0.0))
    }

    @Test
    fun `a mild deviation at rest is logged and nobody is woken`() {
        assertEquals(FuzzyRiskClassifier.Risk.LOW, classify(2.6, rest = 1.0))
    }

    // ------------------------------------------------------- properties of the surface

    @Test
    fun `nothing at full rest is ever classified high`() {
        // The deliberate ceiling: deep sleep never escalates on its own. If something is genuinely
        // wrong the deviation keeps growing and the waking hours that follow escalate it.
        var z = 2.5
        while (z <= 12.0) {
            assertFalse(
                "z=$z at full rest should not be HIGH",
                classify(z, rest = 1.0) == FuzzyRiskClassifier.Risk.HIGH
            )
            z += 0.1
        }
    }

    @Test
    fun `risk never decreases as the deviation grows`() {
        // Monotonic in the deviation, at every hour. A bigger departure from normal must never
        // produce a calmer answer than a smaller one.
        val order = listOf(
            FuzzyRiskClassifier.Risk.LOW,
            FuzzyRiskClassifier.Risk.MEDIUM,
            FuzzyRiskClassifier.Risk.HIGH
        )
        for (restStep in 0..10) {
            val rest = restStep / 10.0
            var previous = -1
            var z = 2.5
            while (z <= 10.0) {
                val rank = order.indexOf(classify(z, rest))
                assertTrue("risk fell at z=$z rest=$rest", rank >= previous)
                previous = rank
                z += 0.1
            }
        }
    }

    @Test
    fun `risk never increases as more rest is expected`() {
        // Monotonic in the other input too: a quieter hour must never produce a louder answer.
        val order = listOf(
            FuzzyRiskClassifier.Risk.LOW,
            FuzzyRiskClassifier.Risk.MEDIUM,
            FuzzyRiskClassifier.Risk.HIGH
        )
        var z = 2.5
        while (z <= 10.0) {
            var previous = order.size
            for (restStep in 0..20) {
                val rank = order.indexOf(classify(z, restStep / 20.0))
                assertTrue("risk rose at z=$z rest=${restStep / 20.0}", rank <= previous)
                previous = rank
            }
            z += 0.25
        }
    }

    // ------------------------------------------------------------ rest expectation

    @Test
    fun `midday is fully awake and the small hours are fully at rest`() {
        val wake = "06:00"
        val sleep = "22:00"
        assertEquals(0.0, FuzzyRiskClassifier.restExpectation(13 * 60, wake, sleep), 0.001)
        assertEquals(1.0, FuzzyRiskClassifier.restExpectation(3 * 60, wake, sleep), 0.001)
    }

    @Test
    fun `waking and bedtime ramp rather than step`() {
        val wake = "06:00"
        val sleep = "22:00"
        // Half an hour after waking: half way down the ramp, not already fully awake.
        assertEquals(0.5, FuzzyRiskClassifier.restExpectation(6 * 60 + 30, wake, sleep), 0.01)
        // Half an hour before bed: half way back up.
        assertEquals(0.5, FuzzyRiskClassifier.restExpectation(21 * 60 + 30, wake, sleep), 0.01)
    }

    @Test
    fun `a sleep window crossing midnight is handled`() {
        // Wakes at 05:00, sleeps at 01:00 the next day.
        val rest = FuzzyRiskClassifier.restExpectation(2 * 60, "05:00", "01:00")
        assertEquals(1.0, rest, 0.001)
        assertEquals(0.0, FuzzyRiskClassifier.restExpectation(12 * 60, "05:00", "01:00"), 0.001)
    }

    @Test
    fun `identical wake and sleep times keep detection on rather than off`() {
        // A degenerate profile must fail towards monitoring, never towards permanent silence.
        assertEquals(0.0, FuzzyRiskClassifier.restExpectation(3 * 60, "07:00", "07:00"), 0.001)
    }

    // ------------------------------------------------------------------ nap window

    @Test
    fun `the declared nap window suppresses and its edges are half-open`() {
        assertTrue(FuzzyRiskClassifier.isWithinNapWindow(14 * 60, "14:00", 60))
        assertTrue(FuzzyRiskClassifier.isWithinNapWindow(14 * 60 + 59, "14:00", 60))
        // The minute the nap ends is outside it — otherwise the window silently runs a minute long.
        assertFalse(FuzzyRiskClassifier.isWithinNapWindow(15 * 60, "14:00", 60))
        assertFalse(FuzzyRiskClassifier.isWithinNapWindow(13 * 60 + 59, "14:00", 60))
    }

    @Test
    fun `a nap crossing midnight is handled`() {
        assertTrue(FuzzyRiskClassifier.isWithinNapWindow(15, "23:30", 60))
    }

    @Test
    fun `no declared nap suppresses nothing`() {
        assertFalse(FuzzyRiskClassifier.isWithinNapWindow(14 * 60, null, 60))
        assertFalse(FuzzyRiskClassifier.isWithinNapWindow(14 * 60, "14:00", null))
        assertFalse(FuzzyRiskClassifier.isWithinNapWindow(14 * 60, "14:00", 0))
    }

    // ------------------------------------- the device under test, for demo rehearsal

    // Agnes Rayos as actually onboarded on the Infinix: wake 08:30, sleep 21:00, and a declared
    // nap at 14:00 lasting 120 minutes. AnomalySimulator injects its reading at z = 4.0, so these
    // pin down exactly what the demo button does at each hour of her day.
    private val WAKE = "08:30"
    private val SLEEP = "21:00"

    private fun demoRiskAt(hour: Int, minute: Int = 0) =
        classify(4.0, FuzzyRiskClassifier.restExpectation(hour * 60 + minute, WAKE, SLEEP))

    @Test
    fun `demo reading is high through Agnes's waking hours`() {
        assertEquals(FuzzyRiskClassifier.Risk.HIGH, demoRiskAt(10))
        assertEquals(FuzzyRiskClassifier.Risk.HIGH, demoRiskAt(13))
        assertEquals(FuzzyRiskClassifier.Risk.HIGH, demoRiskAt(16))
        assertEquals(FuzzyRiskClassifier.Risk.HIGH, demoRiskAt(19))
    }

    @Test
    fun `demo reading goes quiet once Agnes is expected asleep`() {
        assertEquals(FuzzyRiskClassifier.Risk.LOW, demoRiskAt(22, 45))
        assertEquals(FuzzyRiskClassifier.Risk.LOW, demoRiskAt(3))
    }

    @Test
    fun `Agnes's declared nap suppresses two whole afternoon hours`() {
        assertTrue(FuzzyRiskClassifier.isWithinNapWindow(14 * 60, "14:00", 120))
        assertTrue(FuzzyRiskClassifier.isWithinNapWindow(15 * 60 + 59, "14:00", 120))
        assertFalse(FuzzyRiskClassifier.isWithinNapWindow(16 * 60, "14:00", 120))
        assertFalse(FuzzyRiskClassifier.isWithinNapWindow(13 * 60 + 59, "14:00", 120))
    }

    // ------------------------------------------------------------- the nap tail

    /** A local-time instant today, on the same clock the production code reads. */
    private fun at(hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun secondsSinceNapEnd(hour: Int, minute: Int, second: Int = 0) =
        FuzzyRiskClassifier.secondsSinceNapEnd(at(hour, minute, second), "14:00", 60)

    @Test
    fun `seconds since nap end starts at zero the moment the window closes`() {
        // The minute isWithinNapWindow stops suppressing is the minute this starts counting from,
        // so no stretch of the day is excused by both and no stretch by neither.
        assertFalse(FuzzyRiskClassifier.isWithinNapWindow(15 * 60, "14:00", 60))
        assertEquals(0L, secondsSinceNapEnd(15, 0))
        assertEquals(30L, secondsSinceNapEnd(15, 0, 30))
        assertEquals(1680L, secondsSinceNapEnd(15, 28))
    }

    @Test
    fun `seconds since nap end is null when no nap was declared`() {
        val noon = at(12, 0)
        assertNull(FuzzyRiskClassifier.secondsSinceNapEnd(noon, null, 60))
        assertNull(FuzzyRiskClassifier.secondsSinceNapEnd(noon, "14:00", null))
        assertNull(FuzzyRiskClassifier.secondsSinceNapEnd(noon, "14:00", 0))
    }

    @Test
    fun `a nap crossing midnight counts forward from the far side`() {
        // 23:30 + 60 min ends at 00:30. Half an hour later is half an hour, not twenty-three and a
        // half — the modular arithmetic has to survive the wrap or the clip would excuse a whole
        // day of stillness every morning.
        assertEquals(1800L, FuzzyRiskClassifier.secondsSinceNapEnd(at(1, 0), "23:30", 60))
    }

    @Test
    fun `away from the nap the tail clip yields to the block clip`() {
        // Ten in the morning is nowhere near her nap, so this number is large and a caller taking
        // `min` of it and the block elapsed time is left with the block elapsed time. The new clip
        // must change nothing on the readings it was not written for.
        val blockElapsed = 3600L
        val sinceNapEnd = secondsSinceNapEnd(10, 0)!!
        assertTrue(sinceNapEnd > blockElapsed)
        assertEquals(blockElapsed, minOf(blockElapsed, sinceNapEnd))
    }

    @Test
    fun `the pilot's two false alerts of 2026-09-16 no longer clear the threshold`() {
        // Her real fingerprint for the afternoon block as the 2026-09-18 baseline pass wrote it.
        val median = 1007.1
        val mad = 315.4
        val madFloor = SeedBaselineGenerator.MIN_MAD_FLOOR.getValue("inactivity_duration")

        // Alert 101, 15:28. Her afternoon block starts 14:20, so the block clip alone allowed
        // 4,080 s and the counter's own 3,593 s stood — thirty-two minutes of which were the nap
        // she had declared.
        val counterReading = 3593.0
        val blockElapsed = 4080L
        assertEquals(8.2, MedianMad.deviationsScore(
            minOf(counterReading, blockElapsed.toDouble()), median, mad, madFloor), 0.05)

        // With the nap tail clipped off, the same reading is twenty-eight minutes of stillness.
        val napClipped = minOf(counterReading, secondsSinceNapEnd(15, 28)!!.toDouble())
        assertEquals(1680.0, napClipped, 0.001)
        assertEquals(2.13, MedianMad.deviationsScore(napClipped, median, mad, madFloor), 0.01)
        assertTrue(MedianMad.deviationsScore(napClipped, median, mad, madFloor) < 2.5)
    }

    // ------------------------------------------- Layer 2 as the third antecedent (Phase 6)

    private fun classify(z: Double, rest: Double, ml: Double) =
        FuzzyRiskClassifier.classify(FuzzyRiskClassifier.Inputs(z, rest, ml))

    @Test
    fun `a Layer 1 alert is unchanged by the widened table`() {
        // The nine original rules are the ml_flag-absent slice, and every Layer 1 caller still
        // uses the two-argument form. Stating it as an equality means a future edit to the
        // twenty-seven cannot quietly move Layer 1's answers.
        var z = 2.5
        while (z <= 8.0) {
            for (restStep in 0..10) {
                val rest = restStep / 10.0
                assertEquals(
                    "z=$z rest=$rest drifted when ml_flag was absent",
                    classify(z, rest),
                    classify(z, rest, 0.0)
                )
            }
            z += 0.25
        }
    }

    @Test
    fun `Layer 2 alone asks the senior but never summons the barangay`() {
        // A finding about a block that has already closed is not a fall in progress. Its ceiling
        // is the wellness prompt — Medium — however isolated the day was, right up to a score of
        // 1.0. Reaching High needs a Layer 1 deviation to corroborate.
        var ml = IsolationForestDetector.THRESHOLD
        while (ml <= 1.0) {
            assertEquals(
                "ml=$ml alone should stay MEDIUM",
                FuzzyRiskClassifier.Risk.MEDIUM,
                classify(z = 0.0, rest = 0.0, ml = ml)
            )
            ml += 0.02
        }
    }

    @Test
    fun `Layer 2 alone is logged rather than waking a sleeping senior`() {
        // The nightly pass can land at any hour. Asking a senior at 3am whether yesterday morning
        // was alright is no use to her and teaches her to ignore the prompt.
        assertEquals(FuzzyRiskClassifier.Risk.LOW, classify(z = 0.0, rest = 1.0, ml = 0.95))
    }

    @Test
    fun `Layer 2 corroborating a moderate deviation escalates it`() {
        // The pair that justifies having a second layer at all: identical z, identical hour, and
        // the only difference is that Isolation Forest independently found the whole block odd.
        val z = 3.6
        val rest = 0.5
        assertEquals(FuzzyRiskClassifier.Risk.MEDIUM, classify(z, rest, ml = 0.0))
        assertEquals(FuzzyRiskClassifier.Risk.HIGH, classify(z, rest, ml = 0.62))
    }

    @Test
    fun `a strong Layer 2 score lifts an otherwise silent finding near bedtime`() {
        // Without it this row is Low, which means the finding is never mentioned at all.
        assertEquals(FuzzyRiskClassifier.Risk.LOW, classify(z = 0.0, rest = 0.5, ml = 0.0))
        assertEquals(FuzzyRiskClassifier.Risk.MEDIUM, classify(z = 0.0, rest = 0.5, ml = 0.95))
    }

    @Test
    fun `nothing at full rest is ever high, at any ml_flag score`() {
        // The original ceiling, re-proven across the new dimension — this is the invariant most
        // likely to be broken by a careless edit to the twenty-seven rows.
        var z = 0.0
        while (z <= 12.0) {
            var ml = 0.0
            while (ml <= 1.0) {
                assertFalse(
                    "z=$z ml=$ml at full rest should not be HIGH",
                    classify(z, rest = 1.0, ml = ml) == FuzzyRiskClassifier.Risk.HIGH
                )
                ml += 0.05
            }
            z += 0.25
        }
    }

    @Test
    fun `risk never decreases as the ml_flag score grows`() {
        // Monotonic in the third input too. A day the forest found *more* unusual must never
        // produce a calmer answer than one it found less so.
        val order = listOf(
            FuzzyRiskClassifier.Risk.LOW,
            FuzzyRiskClassifier.Risk.MEDIUM,
            FuzzyRiskClassifier.Risk.HIGH
        )
        var z = 0.0
        while (z <= 8.0) {
            for (restStep in 0..10) {
                val rest = restStep / 10.0
                var previous = -1
                var ml = 0.0
                while (ml <= 1.0) {
                    val rank = order.indexOf(classify(z, rest, ml))
                    assertTrue("risk fell at z=$z rest=$rest ml=$ml", rank >= previous)
                    previous = rank
                    ml += 0.02
                }
            }
            z += 0.5
        }
    }
}
