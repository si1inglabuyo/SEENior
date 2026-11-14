package com.pup.seenior.ui

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitTextTest {

    @Test
    fun `system font size is honoured up to the cap and no further`() {
        assertEquals(1.0f, FontScaleGuard.cap(1.0f), 0f)
        assertEquals(1.15f, FontScaleGuard.cap(1.15f), 0f)
        assertEquals(FontScaleGuard.MAX_FONT_SCALE, FontScaleGuard.cap(1.3f), 0f)
        assertEquals("1.5x is Large on many phones, and is held to the cap", FontScaleGuard.MAX_FONT_SCALE, FontScaleGuard.cap(1.5f), 0f)
        assertEquals("the largest setting is held to the cap", FontScaleGuard.MAX_FONT_SCALE, FontScaleGuard.cap(2.0f), 0f)
    }

    @Test
    fun `a very small system size is not made smaller than readable`() {
        assertEquals(0.85f, FontScaleGuard.cap(0.5f), 0f)
    }

    @Test
    fun `capping leaves the screen density alone`() {
        val capped = FontScaleGuard.capped(Density(density = 2.75f, fontScale = 2.0f))
        assertEquals(2.75f, capped.density, 0f)
        assertEquals(FontScaleGuard.MAX_FONT_SCALE, capped.fontScale, 0f)
    }

    @Test
    fun `text that overflows shrinks a step at a time and never below the minimum`() {
        var scale = 1f
        repeat(50) { scale = FitScale.next(scale, overflowsWidth = true) }
        assertEquals(FitScale.MIN, scale, 0f)
    }

    @Test
    fun `text that fits is left alone, so a settled label does not keep changing`() {
        assertEquals(1f, FitScale.next(1f, overflowsWidth = false), 0f)
        assertEquals(0.8f, FitScale.next(0.8f, overflowsWidth = false), 0f)
    }

    @Test
    fun `each overflow step is smaller than the last and a few steps are enough to reach the floor`() {
        var scale = 1f
        var steps = 0
        while (FitScale.next(scale, true) != scale) {
            val next = FitScale.next(scale, true)
            assertTrue(next < scale)
            scale = next
            steps++
        }
        assertTrue("took $steps steps; each is one layout pass", steps <= 8)
    }
}
