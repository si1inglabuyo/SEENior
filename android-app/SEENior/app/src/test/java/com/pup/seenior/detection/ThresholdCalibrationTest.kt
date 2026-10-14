package com.pup.seenior.detection

import com.pup.seenior.database.entities.Baseline
import kotlin.random.Random
import org.junit.Test

/**
 * Prints the score of each scenario over many seeds, so the Layer 2 threshold is chosen from a
 * distribution and not from one lucky draw. Run it, read the table, then fix the threshold in
 * [IsolationForestDetector.THRESHOLD] and [IsolationForestTest].
 */
class ThresholdCalibrationTest {

    private val baselines = listOf(
        Baseline(seniorId = 1, featureName = "movement_score", timeBlock = "morning", medianValue = 0.0346271005188309, madValue = 0.05, sampleCount = 9),
        Baseline(seniorId = 1, featureName = "inactivity_duration", timeBlock = "morning", medianValue = 2753.35714285714, madValue = 1108.28571428571, sampleCount = 9),
        Baseline(seniorId = 1, featureName = "screen_idle_duration", timeBlock = "morning", medianValue = 2335.28571428571, madValue = 412.5, sampleCount = 9),
        Baseline(seniorId = 1, featureName = "screen_unlock_count", timeBlock = "morning", medianValue = 6.35714285714286, madValue = 1.64285714285714, sampleCount = 9),
        Baseline(seniorId = 1, featureName = "step_count", timeBlock = "morning", medianValue = 414.0, madValue = 184.5, sampleCount = 9),
    )

    private data class Case(val name: String, val shouldFlag: Boolean, val movement: Double, val steps: Double, val stillness: Double)

    private val cases = listOf(
        Case("total stillness", true, 0.05, 0.0, 1.0),
        Case("combination (mildly off, all drifting)", true, 0.0, 0.15, 1.38),
        Case("long stillness, little movement", true, 0.2, 0.2, 2.5),
        Case("normal day", false, 1.0, 1.0, 1.0),
        Case("lazy Sunday", false, 0.6, 0.5, 1.0),
        Case("energetic day (3x steps)", false, 1.0, 3.0, 1.0),
        Case("very energetic (10x steps and movement)", false, 10.0, 10.0, 1.0),
    )

    @Test
    fun `print score distribution per scenario`() {
        val runs = 200
        val sb = StringBuilder("\nCALIBRATION over $runs seeds\n")
        for (spread in listOf(0.75, 1.5, 3.0)) {
        sb.append("--- spread $spread z ---\n")
        for (case in cases) {
            val scores = (1..runs).map { i ->
                val days = AggregateSimulator.normalDays(56, seniorId = 1, block = "morning", baselines = baselines, seed = i * 100L, spread = spread)
                val forest = IsolationForest.train(
                    days.map { AggregateFeatures.vector(it, baselines) },
                    trees = IsolationForest.DEFAULT_TREES, psi = IsolationForest.DEFAULT_PSI, seed = i * 100L + 1
                )
                val day = AggregateSimulator.day(
                    seniorId = 1, date = "2099-01-01", block = "morning", baselines = baselines,
                    movementFactor = case.movement, stepsFactor = case.steps, stillnessFactor = case.stillness,
                    spread = spread, random = Random(i * 100L + 2)
                )
                if (false) sb.append("  vec[" + case.name + "] = " + AggregateFeatures.vector(day, baselines).joinToString { "%.2f".format(it) } + "\n")
                forest.score(AggregateFeatures.vector(day, baselines))
            }.sorted()
            sb.append("%-42s %s min %.3f  p10 %.3f  median %.3f  p90 %.3f  max %.3f\n".format(
                case.name, if (case.shouldFlag) "FLAG " else "QUIET",
                scores.first(), scores[runs / 10], scores[runs / 2], scores[runs * 9 / 10], scores.last()
            ))
            sb.append("    share flagged at: " + listOf(0.58, 0.60, 0.62, 0.64, 0.66, 0.68).joinToString("  ") { t ->
                "%.2f=%3d%%".format(t, scores.count { it >= t } * 100 / runs)
            } + "\n")
        }
        }
        println(sb)
    }
}
