package com.pup.seenior.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallTraceTest {

    @Test
    fun `figures are written oldest first from a buffer that is walked newest first`() {
        // The detector walks its ring newest to oldest, so index 0 is the latest reading.
        val newestFirst = floatArrayOf(3.0f, 2.0f, 1.0f)
        assertEquals("dev=1.0000,2.0000,3.0000", FallTrace.encode(newestFirst, 3))
    }

    @Test
    fun `only count figures are used, not the whole buffer`() {
        val buffer = floatArrayOf(0.5f, 0.25f, 99f, 99f)
        assertEquals("dev=0.2500,0.5000", FallTrace.encode(buffer, 2))
    }

    @Test
    fun `it round trips to four decimals`() {
        val figures = floatArrayOf(30.7956f, 0.0034f, 157.0f)
        val back = FallTrace.decode(FallTrace.encode(figures, 3))!!
        assertEquals(listOf(157.0f, 0.0034f, 30.7956f), back)
    }

    @Test
    fun `the largest possible trace stays inside what the server accepts`() {
        // 256 readings of the sensor's range limit: three integer digits, four decimals, commas.
        val worst = FallTrace.encode(FloatArray(256) { 157.0f }, 256)
        assertTrue("was ${worst.length}", worst.length <= 2600)
    }

    @Test
    fun `it never writes a locale comma or a sign`() {
        val trace = FallTrace.encode(floatArrayOf(1.5f, 0.0f), 2)
        assertTrue(trace, Regex("^dev=[0-9]+\\.[0-9]{4}(,[0-9]+\\.[0-9]{4})*$").matches(trace))
    }

    @Test
    fun `unreadable input decodes to null`() {
        assertNull(FallTrace.decode(null))
        assertNull(FallTrace.decode(""))
        assertNull(FallTrace.decode("garbage"))
        assertNull(FallTrace.decode("dev="))
        assertNull(FallTrace.decode("dev=1.0,x"))
        assertNull(FallTrace.decode("pre_dev=2.8;active=1;night=0"))
    }
}
