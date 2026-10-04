package com.pup.seenior.detection

import kotlin.math.abs

/**
 * Layer 0: real-time fall detection. It compares against no baseline, so it works from day one.
 *
 * The signature has three required phases:
 * 1. Free fall: measured acceleration collapses toward zero g.
 * 2. Impact: a short, violent spike.
 * 3. Post-fall stillness: the person does not get up.
 *
 * Requiring all three separates a fall from a phone dropped on a table (no free fall) or
 * tossed on a sofa (picked up again). Someone who gets up immediately raises no alert; a slow
 * decline is covered by the inactivity signal.
 *
 * No Android imports: it takes timestamped magnitudes only, so a JUnit test can drive it with
 * a synthetic stream. Timestamps must come from `SensorEvent.timestamp`, not the wall clock,
 * because batched samples arrive in bursts.
 */
class FallDetector(
    private val config: Config = Config(),
    /**
     * Optional narration of every decision. A callback rather than `android.util.Log` so this
     * class stays free of Android imports; [com.pup.seenior.sensors.SensorCollectionService]
     * wires it to logcat, and a test can wire it to a list. Without it a drop that fails to
     * confirm looks the same as one never seen.
     */
    private val trace: (String) -> Unit = {}
) {

    /**
     * Thresholds in SI units (m/s2, rad/s, ms). The defaults are on the conservative end of
     * published ranges: a missed fall is caught late by the inactivity signal, but a false one
     * wakes a family member at 3am.
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
        /** Peak angular speed expected as the body rotates during the fall (spec §4). */
        val rotationMin: Float = 2.0f,
        /** How far either side of impact a rotation peak still counts as part of this event. */
        val rotationMemoryMs: Long = 3_000,
        /** Whether rotation is required to confirm. Set from whether the phone has a gyroscope; without one, requiring it would never detect a fall. */
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

    /** Feeds one accelerometer sample in. Returns true once per confirmed fall, on the sample that completes the stillness window. */
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
     * Feeds one gyroscope sample in (angular velocity magnitude). Gyroscope and accelerometer
     * samples arrive in separate batches, so the rotation for a fall can come before or after
     * the acceleration. A rolling peak handles the first case and the branch below the second.
     */
    fun onRotation(timestampNanos: Long, angularSpeed: Float) {
        val nowMs = timestampNanos / NANOS_PER_MS

        if (phase == Phase.SETTLING && abs(nowMs - impactMs) <= config.rotationMemoryMs) {
            impactRotationPeak = maxOf(impactRotationPeak, angularSpeed)
        }

        // The peak forgets anything older than the event window, so old rotation can't vouch for the stillness.
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
     * Freezes how much the body was rotating around the impact. Taken now rather than at
     * confirmation, eight seconds later, when the phone is lying still.
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
