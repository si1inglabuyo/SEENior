package com.pup.seenior.detection

import java.util.Locale

/**
 * The working behind a fall alert's `pre_dev`: one figure for each accelerometer reading in the 2 s
 * before the free fall began, oldest first.
 *
 * `dev=0.0034,0.0034,0.0034,...` where each figure is `|magnitude - 9.80665|` in m/s2, the same
 * number [FallDetector] adds up for [FallContext]'s `sum`. Adding them gives `sum`; dividing by
 * `n` gives `pre_dev`. Four decimals, so the sum still lands on the one in the context note.
 *
 * This is the first per-reading series that leaves the phone (CLAUDE.md section 11, amended
 * 2026-11-10 at the panel's request). Each figure is the distance of the TOTAL acceleration from
 * 1 g, so the three axes, the phone's orientation and any timestamp stay on the device. Confirmed
 * falls only, and it syncs only when the alert escalates. A record, not a rule: no detection gate
 * reads it.
 */
object FallTrace {

    private const val PREFIX = "dev="

    /**
     * [newestFirst] is how the detector's buffer is walked (newest reading first); the string is
     * written oldest first, the order the readings arrived in.
     */
    fun encode(newestFirst: FloatArray, count: Int): String = buildString(PREFIX.length + count * 7) {
        append(PREFIX)
        for (i in count - 1 downTo 0) {
            if (i != count - 1) append(',')
            append(String.format(Locale.US, "%.4f", newestFirst[i]))
        }
    }

    /** The figures, oldest first. Null for anything unreadable, including falls recorded before this existed. */
    fun decode(raw: String?): List<Float>? {
        if (raw.isNullOrBlank() || !raw.startsWith(PREFIX)) return null
        val figures = raw.removePrefix(PREFIX).split(',').map { it.toFloatOrNull() ?: return null }
        return figures.takeIf { it.isNotEmpty() }
    }
}
