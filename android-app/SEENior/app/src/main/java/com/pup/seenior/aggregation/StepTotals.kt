package com.pup.seenior.aggregation

import com.pup.seenior.database.entities.SensorData

/**
 * Turns a block's raw step readings into the steps actually taken inside it.
 *
 * `Sensor_Data.step_count` is `TYPE_STEP_COUNTER` verbatim: a counter that runs from boot and
 * only means something as a difference between readings.
 *
 * A decrease is not proof of a reboot. This used to credit the whole reading when it fell,
 * which holds for a reboot only. On the vivo V2317 tester handset (2026-09-23) the counter
 * fell from 9231 to 9182 with no reboot and the senior was credited 9,182 steps in seven
 * minutes; eight blocks in that history held a cumulative reading as their total, and
 * `step_count` is a Layer 1 baseline feature.
 *
 * Instead, a claim is only believed if a person could physically have walked it in the time
 * available. That covers rises and falls alike. A real mid-block reboot still passes, since
 * the counter restarts at zero and its reading is small.
 */
object StepTotals {

    /**
     * The physical ceiling per minute of elapsed time: four steps a second, beyond sprinting.
     * It guards against impossible readings and must never trim real walking.
     */
    const val MAX_STEPS_PER_MINUTE = 240

    /** Steps taken during a block, from its raw readings in any order. */
    fun forBlock(rows: List<SensorData>): Int =
        rows.sortedBy { it.timestamp }
            .zipWithNext { prev, curr ->
                creditFor(prev.timestamp, prev.stepCount, curr.timestamp, curr.stepCount)
            }
            .sum()
            .coerceAtLeast(0)

    /**
     * Steps to credit between two consecutive readings, or 0 when the pair can't be believed.
     * Rejecting to 0 rather than salvaging a figure is deliberate: under-counting makes the
     * senior look less active, which raises an alert rather than suppressing one.
     */
    internal fun creditFor(prevAt: Long, prevCount: Int, currAt: Long, currCount: Int): Int {
        // A fall means the counter restarted or was re-based, so it now holds the most it could claim.
        val claimed = if (currCount >= prevCount) currCount - prevCount else currCount

        val gapMinutes = (currAt - prevAt).coerceAtLeast(0L) / 60_000.0
        // Floored at one minute so two readings in the same second (a duplicate row) don't reject ordinary steps.
        val ceiling = (gapMinutes * MAX_STEPS_PER_MINUTE).toInt().coerceAtLeast(MAX_STEPS_PER_MINUTE)

        return if (claimed <= ceiling) claimed else 0
    }
}
