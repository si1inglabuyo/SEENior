package com.pup.seenior.detection

import android.content.Context
import com.pup.seenior.alerts.AlertResponder
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Alert

/**
 * Demo and test driver for Layer 0.
 *
 * Like [AnomalySimulator] this fabricates *sensor samples*, never an alert. The stream is fed to
 * a real [FallDetector] with production thresholds, and the alert — if there is one — is written
 * by the same code the live sensor stream uses. What is being demonstrated is the detector, not
 * a screen that can be made to appear on demand.
 *
 * The samples carry fabricated timestamps, so a fall whose stillness phase takes twelve seconds
 * of wall-clock time to observe replays instantly. That is also what makes the same generator
 * usable from a JUnit test, which is the only honest way to check a fall detector: CLAUDE.md §10
 * rules out waiting for a real emergency, and no one is throwing a test phone down the stairs.
 */
object FallSimulator {

    private const val SAMPLE_INTERVAL_MS = 20L
    private const val UPRIGHT = 9.81f
    private const val FREE_FALL = 1.2f
    private const val RESTING_ROTATION = 0.02f

    data class Sample(val timestampNanos: Long, val magnitude: Float, val angularSpeed: Float)

    sealed interface Result {
        data class Raised(val alert: Alert) : Result
        /** The detector saw the stream and declined to confirm — the negative cases below. */
        data object NotConfirmed : Result
        data object AlreadyActive : Result
        data object NoSenior : Result
    }

    /**
     * Builds one 50 Hz accelerometer + gyroscope stream. Defaults describe a textbook fall;
     * every phase is a parameter so the near-misses can be built from the same generator rather
     * than from hand-written arrays that might quietly disagree with it.
     */
    fun stream(
        freeFallMs: Long = 400,
        impactMagnitude: Float = 35f,
        stillMs: Long = 12_000,
        rotationPeak: Float = 4.5f,
        standsUpAfterMs: Long? = null
    ): List<Sample> {
        val samples = mutableListOf<Sample>()
        var elapsedMs = 0L

        fun emit(magnitude: Float, angularSpeed: Float) {
            samples += Sample(elapsedMs * NANOS_PER_MS, magnitude, angularSpeed)
            elapsedMs += SAMPLE_INTERVAL_MS
        }

        fun hold(durationMs: Long, magnitude: Float, angularSpeed: Float) {
            repeat((durationMs / SAMPLE_INTERVAL_MS).toInt()) { emit(magnitude, angularSpeed) }
        }

        // Standing, phone in a pocket.
        hold(1_000, UPRIGHT, RESTING_ROTATION)

        // Phase 1 — the drop. Rotation builds as the body turns on the way down.
        val freeFallSamples = (freeFallMs / SAMPLE_INTERVAL_MS).toInt()
        repeat(freeFallSamples) { index ->
            val progress = (index + 1).toFloat() / freeFallSamples
            emit(FREE_FALL, rotationPeak * progress)
        }

        // Phase 2 — impact, then the bounce and roll that always follow it.
        emit(impactMagnitude, rotationPeak)
        hold(300, 13f, rotationPeak / 2f)

        // Phase 3 — lying still, unless this stream is of someone who gets back up.
        if (standsUpAfterMs == null) {
            hold(stillMs, UPRIGHT, RESTING_ROTATION)
        } else {
            hold(standsUpAfterMs, UPRIGHT, RESTING_ROTATION)
            hold(600, 15f, 1.5f)
            hold(stillMs, UPRIGHT, RESTING_ROTATION)
        }

        return samples
    }

    /** Runs [samples] through a real detector and reports whether it confirmed a fall. */
    fun replay(samples: List<Sample>, detector: FallDetector = FallDetector()): Boolean =
        samples.any { sample ->
            detector.onRotation(sample.timestampNanos, sample.angularSpeed)
            detector.onAcceleration(sample.timestampNanos, sample.magnitude)
        }

    suspend fun simulate(context: Context, db: SeniorAppDatabase): Result {
        val senior = db.seniorDao().getOnboardedSenior() ?: return Result.NoSenior
        if (db.alertDao().getActiveAlert(senior.seniorId, "fall_pattern") != null) return Result.AlreadyActive
        if (!replay(stream())) return Result.NotConfirmed

        val alert = AlertResponder.raise(context, db, "fall_pattern", "high")
            ?: return Result.AlreadyActive
        return Result.Raised(alert)
    }

    private const val NANOS_PER_MS = 1_000_000L
}
