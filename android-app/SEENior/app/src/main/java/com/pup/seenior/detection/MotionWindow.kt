package com.pup.seenior.detection

import kotlin.math.abs

/**
 * The running form of `pre_dev`: the mean of |acceleration magnitude - 1 g| over the last
 * [FallDetector.PRE_FALL_WINDOW_MS], computed the same way [FallDetector] computes it for the
 * two seconds before a free fall (same gravity, same window, same minimum sample count).
 *
 * Used by the walk-test recorder on the Demo page, so the value it shows and exports is the
 * figure a fall alert would have stored at that moment. No gate reads it.
 *
 * No Android imports, so JUnit can drive it.
 */
class MotionWindow(private val windowMs: Long = FallDetector.PRE_FALL_WINDOW_MS) {

    private val times = ArrayDeque<Long>()
    private val deviations = ArrayDeque<Float>()
    private var sum = 0.0

    /** Readings currently inside the window. */
    val size: Int get() = times.size

    /**
     * Adds one accelerometer reading and returns the current window mean, or null while the
     * window holds fewer than [FallDetector.PRE_FALL_MIN_SAMPLES] readings (the detector's rule).
     */
    fun add(nowMs: Long, magnitude: Float): Float? {
        val dev = abs(magnitude - FallDetector.GRAVITY)
        times.addLast(nowMs)
        deviations.addLast(dev)
        sum += dev
        // The detector keeps readings at most windowMs old (it stops at "older than windowMs").
        while (times.isNotEmpty() && nowMs - times.first() > windowMs) {
            times.removeFirst()
            sum -= deviations.removeFirst()
        }
        return current()
    }

    /** The current window mean, or null if there are too few readings. */
    fun current(): Float? =
        if (times.size < FallDetector.PRE_FALL_MIN_SAMPLES) null else (sum / times.size).toFloat()

    fun clear() {
        times.clear()
        deviations.clear()
        sum = 0.0
    }
}
