package com.pup.seenior.detection

import kotlin.math.abs

object MedianMad {
    /**
     * Iglewicz & Hoaglin (1993) scale a MAD-based score by 0.6745 so it reads on the same
     * scale as an ordinary z-score. Without it the score runs about 1.48x high, and the 3.5
     * cutoff taken from that paper would not mean what the paper means. Added 2026-11-13;
     * before that the gates 2.5 and 3.5 were applied to the unscaled |x - median| / MAD.
     */
    const val MODIFIED_Z_SCALE = 0.6745

    fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0
        else sorted[mid]
    }
    
    fun mad(values: List<Double>, median: Double): Double {
        val deviations = values.map { abs(it - median) }
        return median(deviations)
    }

    fun deviationsScore(
        current: Double,
        medianValue: Double,
        madValue: Double,
        madFloor: Double
    ): Double {
        val effectiveMad = madValue.coerceAtLeast(madFloor)
        return MODIFIED_Z_SCALE * abs(current - medianValue) / effectiveMad
    }
}
