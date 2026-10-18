package com.pup.seenior.network

import com.pup.seenior.network.dto.RoutineRowDto
import com.pup.seenior.ui.family.RoutineWording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoutineSyncerTest {

    private fun row(
        feature: String = "movement_score",
        block: String = "morning",
        median: Double = 0.04,
        mad: Double = 0.05,
        seed: Boolean = false
    ) = RoutineRowDto(feature, block, median, mad, 14, seed)

    @Test
    fun signatureIgnoresRowOrder() {
        val a = row(block = "morning")
        val b = row(block = "night")
        assertEquals(RoutineSyncer.signatureOf(listOf(a, b)), RoutineSyncer.signatureOf(listOf(b, a)))
    }

    @Test
    fun signatureChangesWhenANumberMoves() {
        assertNotEquals(
            RoutineSyncer.signatureOf(listOf(row(median = 0.04))),
            RoutineSyncer.signatureOf(listOf(row(median = 0.05)))
        )
    }

    @Test
    fun signatureChangesWhenSeedHandsOverToRealData() {
        assertNotEquals(
            RoutineSyncer.signatureOf(listOf(row(seed = true))),
            RoutineSyncer.signatureOf(listOf(row(seed = false)))
        )
    }

    @Test
    fun durationsReadInPlainUnits() {
        assertEquals("45 sec", RoutineWording.duration(45.0, false))
        assertEquals("25 min", RoutineWording.duration(1500.0, false))
        assertEquals("1 hr 5 min", RoutineWording.duration(3900.0, false))
        assertEquals("2 hr", RoutineWording.duration(7200.0, false))
        assertEquals("1 oras 5 min", RoutineWording.duration(3900.0, true))
    }

    @Test
    fun valuesUseTheRightUnitPerFeature() {
        assertEquals("30 min", RoutineWording.value(row("inactivity_duration", median = 1800.0), false))
        assertEquals("0.04", RoutineWording.value(row("movement_score", median = 0.04), false).replace(',', '.'))
        assertEquals("412", RoutineWording.value(row("step_count", median = 411.6), false))
    }

    @Test
    fun everyFingerprintFeatureHasALabelInBothLanguages() {
        RoutineWording.FEATURE_ORDER.forEach {
            assertNotEquals(it, RoutineWording.featureLabel(it, false))
            assertNotEquals(it, RoutineWording.featureLabel(it, true))
        }
        RoutineWording.BLOCK_ORDER.forEach {
            assertNotEquals(it, RoutineWording.blockLabel(it, true))
        }
    }
}
