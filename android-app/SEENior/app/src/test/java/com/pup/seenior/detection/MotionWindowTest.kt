package com.pup.seenior.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recorder's running pre_dev must match the detector's definition. */
class MotionWindowTest {

    private val g = FallDetector.GRAVITY

    @Test
    fun `too few readings gives no value, as in the detector`() {
        val w = MotionWindow()
        repeat(FallDetector.PRE_FALL_MIN_SAMPLES - 1) { i -> assertNull(w.add(i * 20L, g)) }
        assertNotNull(w.add((FallDetector.PRE_FALL_MIN_SAMPLES - 1) * 20L, g))
    }

    @Test
    fun `a still phone reads zero`() {
        val w = MotionWindow()
        var v: Float? = null
        repeat(100) { i -> v = w.add(i * 20L, g) }
        assertEquals(0f, v!!, 1e-6f)
    }

    @Test
    fun `value is the mean distance from gravity`() {
        val w = MotionWindow()
        var v: Float? = null
        // Alternating 1 above and 1 below gravity: mean distance 1.
        repeat(100) { i -> v = w.add(i * 20L, if (i % 2 == 0) g + 1f else g - 1f) }
        assertEquals(1f, v!!, 1e-4f)
    }

    @Test
    fun `readings older than the window drop out`() {
        val w = MotionWindow()
        // 2 s of strong movement, then 2.1 s of stillness at 50 Hz.
        repeat(100) { i -> w.add(i * 20L, g + 3f) }
        var v: Float? = null
        repeat(105) { i -> v = w.add(2_000L + i * 20L, g) }
        assertEquals(0f, v!!, 1e-6f)
        assertTrue(w.size <= 101)
    }

    @Test
    fun `clear empties the window`() {
        val w = MotionWindow()
        repeat(50) { i -> w.add(i * 20L, g + 1f) }
        w.clear()
        assertEquals(0, w.size)
        assertNull(w.current())
    }
}
