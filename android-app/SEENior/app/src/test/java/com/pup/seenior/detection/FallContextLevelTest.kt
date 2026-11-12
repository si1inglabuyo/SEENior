package com.pup.seenior.detection

import com.pup.seenior.detection.FallContext.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three movement bands for the 2 s value, and that they stay out of the stored note. */
class FallContextLevelTest {

    @Test
    fun `values map to still, gentle and moving`() {
        assertEquals(Level.STILL, FallContext.levelOf(0.0f))
        assertEquals(Level.STILL, FallContext.levelOf(0.2f))
        assertEquals(Level.GENTLE, FallContext.levelOf(0.7f))
        assertEquals(Level.MOVING, FallContext.levelOf(1.4f))
        assertEquals(Level.MOVING, FallContext.levelOf(3.0f))
    }

    @Test
    fun `the edges belong to the higher band`() {
        assertEquals(Level.GENTLE, FallContext.levelOf(FallContext.STILL_MAX))
        assertEquals(Level.MOVING, FallContext.levelOf(FallContext.ACTIVE_MIN))
    }

    @Test
    fun `the bands are ordered`() {
        assertTrue(FallContext.STILL_MAX < FallContext.ACTIVE_MIN)
    }

    @Test
    fun `active flag in the note still means moving, and no band name is written`() {
        val moving = FallContext.encode(FallContext.ACTIVE_MIN, "afternoon")
        val gentle = FallContext.encode(0.7f, "afternoon")
        assertTrue(moving, FallContext.decode(moving)!!.active == true)
        assertTrue(gentle, FallContext.decode(gentle)!!.active == false)
        // The cloud accepts only key=number pairs, so a word like "gentle" would be rejected.
        assertFalse(gentle, gentle.contains("level") || gentle.contains("gentle"))
    }
}
