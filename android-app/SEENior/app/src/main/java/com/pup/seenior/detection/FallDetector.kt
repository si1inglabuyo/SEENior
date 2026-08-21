package com.pup.seenior.detection

import kotlin.math.abs

/**
 * Layer 0 of the detection pipeline (CLAUDE.md §5) — real-time fall detection.
 *
 * Unlike Layers 1-3 this compares against nothing. There is no baseline, no z-score and no
 * 14-day warm-up: a fall looks like a fall on the senior's first day with the app, which is
 * exactly why it runs from day one.
 *
 * The signature is three phases in sequence, and all three are required:
 *
 * 1. **Free fall** — the phone accelerates toward the ground, so measured acceleration collapses
 *    toward zero g instead of resting at one g.
 * 2. **Impact** — a short, violent spike as the body hits the floor.
 * 3. **Post-fall stillness** — the person does not get up.
 *
 * Requiring all three is what separates a fall from the two things that otherwise look identical
 * to an accelerometer: a phone dropped on a table (impact and stillness, no free fall) and a
 * phone tossed onto a sofa (free fall and impact, but it is picked up again moments later).
 * Someone who falls and immediately gets up produces no alert either — deliberately. They can
 * move, so they are not the emergency this tier exists for, and a gradual decline afterwards is
 * still covered by the Median-MAD inactivity signal.
 *
 * Deliberately free of Android imports. It is fed timestamped magnitudes and nothing else, so
 * the whole state machine can be driven from a synthetic sample stream in a JUnit test — the
 * validation approach CLAUDE.md §10 requires, since real falls cannot be collected on demand.
 *
 * **Timestamps must come from `SensorEvent.timestamp`, not the wall clock.** The service batches
 * sensor samples to protect the battery target, so a whole second of readings can arrive in one
 * burst; wall-clock timing would see them as simultaneous and no phase would ever have duration.
 */
class FallDetector(
    private val config: Config = Config(),
    /**
     * Optional narration of every decision this state machine makes.
     *
     * A callback rather than `android.util.Log` on purpose: this class is deliberately free of
     * Android imports so the whole thing can be driven from a synthetic stream in a plain JUnit
     * test, and `Log` is not mocked there. [com.pup.seenior.sensors.SensorCollectionService]
     * wires this to logcat; a test can wire it to a list and assert on the reasoning.
     *
     * It earns its place: without it, a drop that fails to confirm is indistinguishable from a
     * drop that was never seen at all, and all four gates fail silently and identically.
     */
    private val trace: (String) -> Unit = {}
) {

    /**
     * Thresholds in SI units — m/s² for acceleration, rad/s for rotation, milliseconds for time.
     *
     * The defaults sit at the conservative end of the published ranges for waist/pocket-worn
     * phone detectors. A missed fall is still caught late by the inactivity signal; a false one
     * wakes a family member at 3am and teaches them to ignore the next alert.
     */
    data class Config(
        /** Below this, the phone is not being held up against gravity. ~0.3 g. */
        val freeFallMax: Float = 3.0f,
        /** Shorter than this is sensor noise or a hand gesture, not a drop. */
        val freeFallMinMs: Long = 100,
        /** Longer than this is not a fall — a human drop is well under a second. */
        val freeFallMaxMs: Long = 2_000,
        /** Impact spike. ~2.5 g. */
        val impactMin: Float = 25.0f,
        /** How long after free fall ends the impact must land. */
        val impactWindowMs: Long = 800,
        /** Bouncing, rolling and settling right after impact are expected — ignore them. */
        val settleGraceMs: Long = 1_500,
        /** How long the person must then stay still to be considered unable to get up. */
        val stillnessMs: Long = 8_000,
        /** How far from one g a sample may drift and still count as "not moving". */
        val stillnessTolerance: Float = 2.5f,
        /** Peak angular speed expected as the body rotates during the fall (CLAUDE.md §4). */
        val rotationMin: Float = 2.0f,
        /** How far either side of impact a rotation peak still counts as part of this event. */
        val rotationMemoryMs: Long = 3_000,
        /**
         * Whether rotation is required to confirm. The caller sets this from whether the device
         * actually has a gyroscope — on a phone without one, demanding rotation would mean
         * never detecting a fall at all, which is worse than confirming on acceleration alone.
         */
        val requireRotation: Boolean = true,
        /** After a confirmed fall, ignore new candidates for this long. */
        val cooldownMs: Long = 60_000
    )

    private enum class Phase { IDLE, FREE_FALL, AWAITING_IMPACT, SETTLING }

    private var phase = Phase.IDLE
    private var freeFallStartMs = 0L
    private var freeFallEndMs = 0L
    private var impactMs = 0L
    private var rotationPeak = 0f
    private var rotationPeakAtMs = Long.MIN_VALUE
    private var impactRotationPeak = 0f
    private var cooldownUntilMs = Long.MIN_VALUE
    /** Highest magnitude seen while awaiting impact, so a near miss can say how near. */
    private var awaitingPeak = 0f

    /**
     * Feeds one accelerometer sample in. Returns true exactly once per confirmed fall, on the
     * sample that completes the stillness window.
     */
    fun onAcceleration(timestampNanos: Long, magnitude: Float): Boolean {
        val nowMs = timestampNanos / NANOS_PER_MS
        if (nowMs < cooldownUntilMs) return false

        when (phase) {
            Phase.IDLE ->
                if (magnitude <= config.freeFallMax) {
                    phase = Phase.FREE_FALL
                    freeFallStartMs = nowMs
                    trace("1/4 free fall begins (mag ${f(magnitude)} <= ${f(config.freeFallMax)})")
                }

            Phase.FREE_FALL ->
                if (magnitude <= config.freeFallMax) {
                    // Sustained weightlessness is a stuck or faulty sensor, not a person falling.
                    if (nowMs - freeFallStartMs > config.freeFallMaxMs) {
                        phase = Phase.IDLE
                        trace("ABANDONED: weightless for ${nowMs - freeFallStartMs} ms, over the ${config.freeFallMaxMs} ms cap — stuck sensor, not a fall")
                    }
                } else if (nowMs - freeFallStartMs < config.freeFallMinMs) {
                    phase = Phase.IDLE
                    trace("ABANDONED at gate 1: free fall lasted only ${nowMs - freeFallStartMs} ms, needs ${config.freeFallMinMs} ms — dropped from too low, or never actually released")
                } else {
                    freeFallEndMs = nowMs
                    phase = Phase.AWAITING_IMPACT
                    awaitingPeak = magnitude
                    trace("1/4 PASSED: free fall ${nowMs - freeFallStartMs} ms — awaiting impact >= ${f(config.impactMin)}")
                    // At 50 Hz the sample that ends free fall is often the impact itself.
                    if (magnitude >= config.impactMin) beginSettling(nowMs)
                }

            Phase.AWAITING_IMPACT ->
                if (magnitude >= config.impactMin) {
                    beginSettling(nowMs)
                } else {
                    awaitingPeak = maxOf(awaitingPeak, magnitude)
                    if (nowMs - freeFallEndMs > config.impactWindowMs) {
                        phase = Phase.IDLE
                        trace("ABANDONED at gate 2: no impact inside ${config.impactWindowMs} ms — hardest sample was ${f(awaitingPeak)}, needed ${f(config.impactMin)}. Landing surface too soft.")
                    }
                }

            Phase.SETTLING -> {
                val sinceImpact = nowMs - impactMs
                if (sinceImpact < config.settleGraceMs) return false
                if (abs(magnitude - GRAVITY) > config.stillnessTolerance) {
                    // They moved. Not the case this tier is for.
                    phase = Phase.IDLE
                    trace("ABANDONED at gate 4: moved at +${sinceImpact} ms (mag ${f(magnitude)}, ${f(abs(magnitude - GRAVITY))} off gravity, tolerance ${f(config.stillnessTolerance)}) — needed stillness until +${config.settleGraceMs + config.stillnessMs} ms. Picked up too early, or still rocking.")
                    return false
                }
                if (sinceImpact >= config.settleGraceMs + config.stillnessMs) {
                    phase = Phase.IDLE
                    if (config.requireRotation && !rotationConfirms()) {
                        trace("ABANDONED at gate 3: stillness held, but rotation peaked at ${f(impactRotationPeak)} rad/s and needs ${f(config.rotationMin)} — it fell flat instead of tumbling.")
                        return false
                    }
                    cooldownUntilMs = nowMs + config.cooldownMs
                    trace("CONFIRMED: all four gates passed (rotation ${f(impactRotationPeak)} rad/s)")
                    return true
                }
            }
        }
        return false
    }

    /**
     * Feeds one gyroscope sample in, as the magnitude of the angular velocity vector.
     *
     * Accelerometer and gyroscope samples do not arrive interleaved — with batching they come in
     * separate bursts — so the rotation belonging to a fall can be delivered either side of the
     * acceleration that identifies it. Both orderings are handled: a rolling peak covers rotation
     * that arrives first, and the branch below covers rotation that arrives after the impact has
     * already been recognised.
     */
    fun onRotation(timestampNanos: Long, angularSpeed: Float) {
        val nowMs = timestampNanos / NANOS_PER_MS

        if (phase == Phase.SETTLING && abs(nowMs - impactMs) <= config.rotationMemoryMs) {
            impactRotationPeak = maxOf(impactRotationPeak, angularSpeed)
        }

        // The rolling peak forgets anything older than the event window, so the stillness after a
        // fall cannot be vouched for by rotation from minutes earlier.
        if (nowMs - rotationPeakAtMs > config.rotationMemoryMs) rotationPeak = 0f
        if (angularSpeed >= rotationPeak) {
            rotationPeak = angularSpeed
            rotationPeakAtMs = nowMs
        }
    }

    /** Drops any half-matched candidate. Used when sensor delivery is interrupted. */
    fun reset() {
        phase = Phase.IDLE
        rotationPeak = 0f
        rotationPeakAtMs = Long.MIN_VALUE
        impactRotationPeak = 0f
    }

    /**
     * Freezes how much the body was rotating around the impact. Taken here rather than read live
     * at confirmation because confirmation happens eight seconds later, by which time the phone
     * is lying still and the live reading says nothing about the fall.
     */
    private fun beginSettling(nowMs: Long) {
        impactMs = nowMs
        phase = Phase.SETTLING
        impactRotationPeak =
            if (abs(nowMs - rotationPeakAtMs) <= config.rotationMemoryMs) rotationPeak else 0f
        trace("2/4 PASSED: impact at +${nowMs - freeFallEndMs} ms — now hold still until +${config.settleGraceMs + config.stillnessMs} ms (rotation so far ${f(impactRotationPeak)} rad/s)")
    }

    /** One decimal place, so a trace line stays readable. */
    private fun f(v: Float): String = ((v * 10).toInt() / 10.0).toString()

    private fun rotationConfirms(): Boolean = impactRotationPeak >= config.rotationMin

    private companion object {
        const val NANOS_PER_MS = 1_000_000L

        /** Standard gravity. Not taken from SensorManager so this class stays framework-free. */
        const val GRAVITY = 9.80665f
    }
}
