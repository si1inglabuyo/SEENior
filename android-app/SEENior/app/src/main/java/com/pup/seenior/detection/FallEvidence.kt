package com.pup.seenior.detection

import java.util.Locale

/**
 * What each gate of [FallDetector] measured on the fall that confirmed. Stored on the alert so
 * the three phases can be inspected afterwards, not just seen scrolling past in logcat.
 *
 * Four summary figures only (durations, a peak magnitude, a peak angular speed), never the
 * sample stream, so the privacy boundary holds. It syncs with the alert when the alert
 * escalates (cloud `alerts.fall_evidence`, migration 0017), so the database shows which phases
 * satisfied the fall. A fall the senior cancels is recorded on the phone only.
 *
 * [rotationPeak] is null when the phone has no gyroscope and the rotation gate was off.
 */
data class FallEvidence(
    val freeFallMs: Long,
    val impactPeak: Float,
    val stillnessMs: Long,
    val rotationPeak: Float?
) {
    /** `ff=400;imp=35.0;still=10500;rot=4.5`, with `rot` omitted when there was no gyroscope. */
    fun encode(): String = buildString {
        append("ff=").append(freeFallMs)
        append(";imp=").append(num(impactPeak))
        append(";still=").append(stillnessMs)
        if (rotationPeak != null) append(";rot=").append(num(rotationPeak))
    }

    /** One line per gate, each against the threshold it had to clear. */
    fun lines(config: FallDetector.Config = FallDetector.Config(), filipino: Boolean = false): List<String> {
        val rotation = rotationPeak?.let {
            if (filipino) "3. Pag-ikot ng katawan: ${num(it)} rad/s (kailangan ≥ ${num(config.rotationMin)}) ✓"
            else "3. Body rotation: ${num(it)} rad/s (needed ≥ ${num(config.rotationMin)}) ✓"
        }
        return if (filipino) listOfNotNull(
            "1. Malayang pagbagsak: ${freeFallMs} ms (kailangan ${config.freeFallMinMs}–${config.freeFallMaxMs} ms) ✓",
            "2. Tama sa lupa: ${num(impactPeak)} m/s² (kailangan ≥ ${num(config.impactMin)}) ✓",
            "4. Hindi gumalaw: ${stillnessMs} ms (kailangan ≥ ${config.stillnessMs} ms) ✓",
            rotation
        ).sorted() else listOfNotNull(
            "1. Free fall: ${freeFallMs} ms (needed ${config.freeFallMinMs}–${config.freeFallMaxMs} ms) ✓",
            "2. Impact: ${num(impactPeak)} m/s² (needed ≥ ${num(config.impactMin)}) ✓",
            "4. Stillness: ${stillnessMs} ms (needed ≥ ${config.stillnessMs} ms) ✓",
            rotation
        ).sorted()
    }

    companion object {
        /** Null for anything unreadable, so a bad or missing value just shows no evidence. */
        fun decode(raw: String?): FallEvidence? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(';').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
            return runCatching {
                FallEvidence(
                    freeFallMs = parts.getValue("ff").toLong(),
                    impactPeak = parts.getValue("imp").toFloat(),
                    stillnessMs = parts.getValue("still").toLong(),
                    rotationPeak = parts["rot"]?.toFloat()
                )
            }.getOrNull()
        }

        private fun num(v: Float): String = String.format(Locale.US, "%.1f", v)
    }
}
