package com.pup.seenior.aggregation

import com.pup.seenior.database.entities.SensorData

/**
 * Turns a block's raw step readings into the number of steps actually taken inside it.
 *
 * `Sensor_Data.step_count` is `TYPE_STEP_COUNTER` verbatim: a counter that runs from boot and is
 * only meaningful as a difference between two readings. Summing positive differences is the
 * whole job, and would be the whole file, except that the counter does not always behave like a
 * counter.
 *
 * **Why a decrease is not proof of a reboot.** This used to credit `curr.stepCount` whenever the
 * reading fell, reasoning that the counter must have restarted from zero and everything on it
 * was therefore walked since. That holds on a reboot and nowhere else. Measured on the vivo
 * V2317 tester handset on 2026-09-23, with no reboot anywhere near it:
 *
 * ```
 * 11:33:24   step_count 9231
 * 11:40:34   step_count 9182     <- fell by 49, then sat flat
 * ```
 *
 * Funtouch re-baselined the counter by 49 steps. The old rule read that as a reboot and credited
 * the senior with **9,182 steps in seven minutes** -- 22 steps a second. Eight blocks in that
 * handset's history hold a single cumulative reading as their total this way (8,685 / 8,734 /
 * 8,757 / 9,017 / 9,096 / 9,127 / 9,248, and one of 35,897), and `step_count` is a Layer 1
 * Baseline feature, so each one moved that senior's idea of a normal block.
 *
 * Neither the pilot Infinix nor the realme shows a single decrease, so this is one vendor's
 * sensor stack and there is no reason to expect it to be the last.
 *
 * **The rule instead.** A claim is only believed if a person could physically have walked it in
 * the time available. That covers both halves of the old branch, because a jump of 9,000 inside
 * one five-minute poll is exactly as impossible whether it arrived as a rise or as a fall, and
 * the old code only ever checked one of them.
 *
 * A genuine mid-block reboot still survives it: the counter restarts at zero and has been
 * running for at most the length of the gap, so the reading it offers is small and plausible by
 * construction. What cannot survive is a whole day's walking presented as one poll's worth.
 */
object StepTotals {

    /**
     * The physical ceiling, per minute of elapsed time.
     *
     * Four steps a second, which is beyond sprinting and far beyond the target population -- and
     * generous on purpose. This is a guard against impossible readings, not a filter on real
     * ones: it has to sit high enough that no senior's actual walking is ever trimmed, and any
     * value it does reject is one no human produced.
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
     * Steps to credit between two consecutive readings, or 0 when the pair cannot be believed.
     *
     * Rejecting to 0 rather than to some salvaged figure is deliberate. Once the counter has
     * contradicted itself there is nothing left to derive a real number from, and a baseline
     * feature is better missing a few hundred steps than holding thousands nobody walked --
     * under-counting makes the senior look *less* active than they are, which is the direction
     * that raises an alert rather than suppressing one.
     */
    internal fun creditFor(prevAt: Long, prevCount: Int, currAt: Long, currCount: Int): Int {
        // A fall means the counter restarted or was re-based; either way everything it now holds
        // is the most it could be claiming. A rise is the ordinary difference.
        val claimed = if (currCount >= prevCount) currCount - prevCount else currCount

        val gapMinutes = (currAt - prevAt).coerceAtLeast(0L) / 60_000.0
        // Floored at one minute's worth so that two readings arriving in the same second -- a
        // duplicate row, a clock adjustment -- do not reject a perfectly ordinary handful of
        // steps for having no time to have happened in.
        val ceiling = (gapMinutes * MAX_STEPS_PER_MINUTE).toInt().coerceAtLeast(MAX_STEPS_PER_MINUTE)

        return if (claimed <= ceiling) claimed else 0
    }
}
