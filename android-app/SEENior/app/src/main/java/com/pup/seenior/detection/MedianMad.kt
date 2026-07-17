package com.pup.seenior.detection

import kotlin.math.abs

object MedianMad {
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
        return abs(current - medianValue) / effectiveMad
    }
}
