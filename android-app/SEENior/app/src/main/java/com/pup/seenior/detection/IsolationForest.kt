package com.pup.seenior.detection

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Layer 2: catches the day Layer 1 can't. Layer 1 asks every five minutes whether one signal
 * is unusual; this asks once a day whether the combination of movement, stillness, screen
 * idle, unlocks, steps and charging for a time block is unusual for this senior. See
 * [IsolationTree] for how one tree turns "unusual" into a path length; this class grows the
 * forest and turns the average path length into a 0-1 score.
 *
 * It knows nothing about seniors, blocks or baselines, only [DoubleArray] rows, so it can be
 * unit tested with invented numbers. Building those rows from `DailyAggregate` and `Baseline`
 * is `AggregateFeatures`' job.
 *
 * It is trained fresh on the device every night (from
 * `com.pup.seenior.aggregation.NightlyAggregationWorker`) rather than shipped as a `.pkl`;
 * with dozens of rows that takes milliseconds and can't go stale. No Android imports.
 */
class IsolationForest private constructor(
    private val trees: List<IsolationTree>,
    /** The subsample size each tree actually trained on (what [train] resolved `psi` to), used to normalise scores. */
    private val subsampleSize: Int
) {

    /**
     * Anomaly score in (0, 1]. Close to 1 means [point] was isolated in very few cuts (an
     * unusual combination), around 0.5 is inconclusive, and well below 0.5 is unremarkable.
     * It returns only the raw score, never a yes/no, so this layer's output stays separate
     * from Layer 1's z-score and Layer 3's risk level.
     */
    fun score(point: DoubleArray): Double {
        val averagePathLength = trees.map { it.pathLength(point) }.average()
        val normalizer = IsolationTree.averagePathLengthCorrection(subsampleSize)
        return 2.0.pow(-averagePathLength / normalizer)
    }

    companion object {

        /** Trees per forest, from the plan agreed 2026-09-03 (see project memory). */
        const val DEFAULT_TREES = 100

        /** Subsample size per tree ("psi" in the original paper), same source as [DEFAULT_TREES]. */
        const val DEFAULT_PSI = 32

        /**
         * Builds a forest of [trees] trees, each grown on a random subsample of [psi] rows drawn
         * without replacement, or all of [data] if there are fewer rows than [psi].
         *
         * @param seed fixed by the caller, with no default, so every caller chooses on purpose.
         *   Unseeded randomness makes tests fail intermittently. Production should pass a real
         *   seed such as `System.nanoTime()`; only tests need reproducibility.
         */
        fun train(
            data: List<DoubleArray>,
            trees: Int = DEFAULT_TREES,
            psi: Int = DEFAULT_PSI,
            seed: Long
        ): IsolationForest {
            require(data.size >= 2) {
                "Need at least 2 rows to train an Isolation Forest, got ${data.size}"
            }
            require(psi >= 2) { "psi must be at least 2, got $psi" }
            val dimensions = data[0].size
            require(data.all { it.size == dimensions }) {
                "All feature vectors must have the same length ($dimensions)"
            }

            val random = Random(seed)
            val subsampleSize = minOf(psi, data.size)
            // The paper's height limit: trees only need to be tall enough for anomalies, which isolate near the root, to stand out.
            val heightLimit = ceil(ln(subsampleSize.toDouble()) / ln(2.0)).toInt().coerceAtLeast(1)

            val forest = List(trees) {
                val subsample = data.shuffled(random).take(subsampleSize)
                IsolationTree.build(subsample, heightLimit, random)
            }
            return IsolationForest(forest, subsampleSize)
        }
    }
}
