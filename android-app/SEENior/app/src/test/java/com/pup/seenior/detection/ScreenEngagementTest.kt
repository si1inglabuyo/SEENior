package com.pup.seenior.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenEngagementTest {

    @Test
    fun `a screen that is on right now excuses both stillness signals`() {
        assertTrue(ScreenEngagement.excuses("inactivity_duration", 0))
        assertTrue(ScreenEngagement.excuses("movement_score", 0))
    }

    @Test
    fun `a screen last on within five minutes still counts`() {
        assertTrue(ScreenEngagement.excuses("inactivity_duration", 300))
        assertTrue(ScreenEngagement.excuses("movement_score", 120))
    }

    @Test
    fun `a screen quiet for longer than five minutes excuses nothing`() {
        assertFalse(ScreenEngagement.excuses("inactivity_duration", 301))
        assertFalse(ScreenEngagement.excuses("movement_score", 3_600))
    }

    @Test
    fun `screen idle is never excused by the screen, since it is the engagement signal`() {
        assertFalse(ScreenEngagement.excuses("screen_idle_duration", 0))
    }

    @Test
    fun `the demo injection is not swallowed because it pins screen idle at its median`() {
        // The simulator pins other signals to their medians, which are over 1,000 s in every block.
        assertFalse(ScreenEngagement.excuses("inactivity_duration", 1_314))
    }
}
