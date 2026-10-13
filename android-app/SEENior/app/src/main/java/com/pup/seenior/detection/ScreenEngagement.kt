package com.pup.seenior.detection

/**
 * Whether recent screen use explains a stillness reading, so it is logged and nobody is asked.
 *
 * Inactivity and movement come from the accelerometer alone, so a senior lying in bed with the
 * phone on their chest, watching or reading, scores as motionless for an hour or more while
 * plainly awake. Screen idle is the only signal that sees the screen, and it says nothing about
 * the other two. A screen that was on in the last few minutes is evidence of a conscious person,
 * so for those two signals the anomaly drops to Low: still recorded, no prompt.
 *
 * Screen idle itself is never excused (it is the engagement signal), and nothing here is on the
 * path of Layer 0 falls or SOS. A real stillness problem still alerts once the screen has been
 * quiet for longer than [WINDOW_SECONDS].
 */
object ScreenEngagement {

    /**
     * How recently the screen must have been on. One sampling period: the reading is the
     * seconds since the screen was last on, so anything up to 5 minutes means it was on during
     * the window this sample covers.
     */
    const val WINDOW_SECONDS = 300L

    private val EXCUSABLE = setOf("inactivity_duration", "movement_score")

    /** True when [featureName] is a stillness signal and the screen was on within [WINDOW_SECONDS]. */
    fun excuses(featureName: String, screenIdleSeconds: Long): Boolean =
        featureName in EXCUSABLE && screenIdleSeconds <= WINDOW_SECONDS
}
