package com.pup.seenior.detection

/**
 * Shows, gate by gate, how SEENior's real [FallDetector] judges what the phone is feeling right
 * now. Used by the walk-test recorder's live fall check, so a simulated fall on a mattress can be
 * watched passing (or failing) free fall, impact, rotation and stillness as it happens.
 *
 * It runs its own [FallDetector] instance with the production thresholds and never raises an
 * alert: nothing here touches the database or [com.pup.seenior.alerts.AlertResponder]. The real
 * background detector is separate and still runs, so a full simulated fall can raise a real alert
 * from it; the recorder screen warns about that.
 *
 * Gate status is read from the detector's own narration (the same lines it writes to logcat), so
 * what the screen shows is what the detector decided, not a second copy of its rules.
 *
 * No Android imports, so JUnit can drive it.
 */
class FallCheckMonitor(val config: FallDetector.Config = FallDetector.Config()) {

    enum class Gate { FREE_FALL, IMPACT, ROTATION, STILLNESS }
    enum class Status { WAITING, IN_PROGRESS, PASSED, FAILED, NOT_USED }
    enum class Outcome { NONE, CONFIRMED, NOT_CONFIRMED }

    data class Snapshot(
        val statuses: Map<Gate, Status>,
        /** Free fall duration: running while in progress, final once passed or failed. */
        val freeFallMs: Long?,
        /** Hardest reading at or after the end of free fall (m/s2). */
        val impactPeak: Float?,
        /** Fastest rotation around the impact (rad/s). */
        val rotationPeak: Float?,
        /** How long the phone has stayed still after the settle period (ms). */
        val stillMs: Long?,
        val outcome: Outcome,
        /** The detector's latest narration line. */
        val message: String?,
        val config: FallDetector.Config,
        /** Sensor-clock times (ms) of this attempt's events, for markers on the graph. */
        val freeFallStartAtMs: Long? = null,
        val freeFallEndAtMs: Long? = null,
        val impactAtMs: Long? = null,
        /** When the attempt was confirmed or abandoned. */
        val endedAtMs: Long? = null,
        /** Time since the impact (ms), for the settle and stillness timer. */
        val sinceImpactMs: Long? = null
    ) {
        companion object {
            fun idle(config: FallDetector.Config) = Snapshot(
                statuses = initialStatuses(config),
                freeFallMs = null, impactPeak = null, rotationPeak = null, stillMs = null,
                outcome = Outcome.NONE, message = null, config = config
            )

            fun initialStatuses(config: FallDetector.Config): Map<Gate, Status> = mapOf(
                Gate.FREE_FALL to Status.WAITING,
                Gate.IMPACT to Status.WAITING,
                Gate.ROTATION to if (config.requireRotation) Status.WAITING else Status.NOT_USED,
                Gate.STILLNESS to Status.WAITING
            )
        }
    }

    private var detector = FallDetector(config) { onTrace(it) }
    private val statuses = Snapshot.initialStatuses(config).toMutableMap()
    private var freeFallStartMs = 0L
    private var freeFallStartAt: Long? = null
    private var freeFallEndAt: Long? = null
    private var endedAt: Long? = null
    private var sinceImpact: Long? = null
    private var freeFallMs: Long? = null
    private var impactAtMs: Long? = null
    private var impactPeak: Float? = null
    private var awaitingPeak = 0f
    private var rotationPeak: Float? = null
    private var stillMs: Long? = null
    private var outcome = Outcome.NONE
    private var message: String? = null

    // The reading being processed when the detector narrates.
    private var nowMs = 0L
    private var nowMagnitude = 0f

    // Recent rotation, so the peak around an impact includes the turn on the way down.
    private val rotTimes = ArrayDeque<Long>()
    private val rotValues = ArrayDeque<Float>()

    fun onRotation(timestampNanos: Long, angularSpeed: Float) {
        val ms = timestampNanos / NANOS_PER_MS
        rotTimes.addLast(ms)
        rotValues.addLast(angularSpeed)
        while (rotTimes.isNotEmpty() && ms - rotTimes.first() > config.rotationMemoryMs) {
            rotTimes.removeFirst()
            rotValues.removeFirst()
        }
        val impact = impactAtMs
        if (impact != null && outcome == Outcome.NONE && ms - impact <= config.rotationMemoryMs) {
            rotationPeak = maxOf(rotationPeak ?: 0f, angularSpeed)
            if (config.requireRotation && rotationPeak!! >= config.rotationMin) {
                statuses[Gate.ROTATION] = Status.PASSED
            }
        }
        detector.onRotation(timestampNanos, angularSpeed)
    }

    /** Returns true on the reading that confirms a fall, exactly as [FallDetector.onAcceleration] does. */
    fun onAcceleration(timestampNanos: Long, magnitude: Float): Boolean {
        nowMs = timestampNanos / NANOS_PER_MS
        nowMagnitude = magnitude
        when (statuses[Gate.FREE_FALL]) {
            Status.IN_PROGRESS -> freeFallMs = nowMs - freeFallStartMs
            else -> Unit
        }
        if (statuses[Gate.IMPACT] == Status.IN_PROGRESS) {
            awaitingPeak = maxOf(awaitingPeak, magnitude)
            impactPeak = awaitingPeak
        }
        val impact = impactAtMs
        if (impact != null && statuses[Gate.STILLNESS] == Status.IN_PROGRESS) {
            stillMs = (nowMs - impact - config.settleGraceMs).coerceAtLeast(0)
            sinceImpact = nowMs - impact
        }
        return detector.onAcceleration(timestampNanos, magnitude)
    }

    fun snapshot(): Snapshot = Snapshot(
        statuses = statuses.toMap(),
        freeFallMs = freeFallMs,
        impactPeak = impactPeak,
        rotationPeak = rotationPeak,
        stillMs = stillMs,
        outcome = outcome,
        message = message,
        config = config,
        freeFallStartAtMs = freeFallStartAt,
        freeFallEndAtMs = freeFallEndAt,
        impactAtMs = impactAtMs,
        endedAtMs = endedAt,
        sinceImpactMs = sinceImpact
    )

    /** Clears the panel and the detector, including its one-minute pause after a confirmed fall. */
    fun reset() {
        detector = FallDetector(config) { onTrace(it) }
        clearAttempt()
        message = null
    }

    private fun clearAttempt() {
        statuses.clear()
        statuses.putAll(Snapshot.initialStatuses(config))
        freeFallStartAt = null
        freeFallEndAt = null
        endedAt = null
        sinceImpact = null
        freeFallMs = null
        impactAtMs = null
        impactPeak = null
        awaitingPeak = 0f
        rotationPeak = null
        stillMs = null
        outcome = Outcome.NONE
    }

    private fun onTrace(line: String) {
        message = line
        when {
            line.startsWith("1/4 free fall begins") -> {
                clearAttempt()
                freeFallStartMs = nowMs
                freeFallStartAt = nowMs
                freeFallMs = 0
                statuses[Gate.FREE_FALL] = Status.IN_PROGRESS
            }
            line.startsWith("1/4 PASSED") -> {
                freeFallMs = nowMs - freeFallStartMs
                freeFallEndAt = nowMs
                statuses[Gate.FREE_FALL] = Status.PASSED
                statuses[Gate.IMPACT] = Status.IN_PROGRESS
                awaitingPeak = nowMagnitude
                impactPeak = nowMagnitude
            }
            line.startsWith("2/4 PASSED") -> {
                impactAtMs = nowMs
                sinceImpact = 0
                impactPeak = nowMagnitude
                statuses[Gate.IMPACT] = Status.PASSED
                statuses[Gate.STILLNESS] = Status.IN_PROGRESS
                stillMs = 0
                rotationPeak = recentRotationPeak()
                if (config.requireRotation) {
                    statuses[Gate.ROTATION] =
                        if (rotationPeak!! >= config.rotationMin) Status.PASSED else Status.IN_PROGRESS
                }
            }
            line.startsWith("ABANDONED at gate ") -> {
                val gate = when (line.removePrefix("ABANDONED at gate ").firstOrNull()) {
                    '1' -> Gate.FREE_FALL
                    '2' -> Gate.IMPACT
                    '3' -> Gate.ROTATION
                    else -> Gate.STILLNESS
                }
                if (gate == Gate.ROTATION) statuses[Gate.STILLNESS] = Status.PASSED
                statuses[gate] = Status.FAILED
                outcome = Outcome.NOT_CONFIRMED
                endedAt = nowMs
                impactAtMs?.let { sinceImpact = nowMs - it }
            }
            line.startsWith("ABANDONED") -> {
                statuses[Gate.FREE_FALL] = Status.FAILED
                outcome = Outcome.NOT_CONFIRMED
                endedAt = nowMs
            }
            line.startsWith("CONFIRMED") -> {
                detector.lastEvidence?.let { evidence ->
                    freeFallMs = evidence.freeFallMs
                    impactPeak = evidence.impactPeak
                    stillMs = evidence.stillnessMs
                    evidence.rotationPeak?.let { rotationPeak = it }
                }
                for (gate in Gate.entries) {
                    if (statuses[gate] != Status.NOT_USED) statuses[gate] = Status.PASSED
                }
                outcome = Outcome.CONFIRMED
                endedAt = nowMs
                impactAtMs?.let { sinceImpact = nowMs - it }
            }
        }
    }

    private fun recentRotationPeak(): Float {
        var peak = 0f
        for (i in rotTimes.indices) {
            if (nowMs - rotTimes[i] <= config.rotationMemoryMs) peak = maxOf(peak, rotValues[i])
        }
        return peak
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
