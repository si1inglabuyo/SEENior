package com.pup.seenior.detection

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Layer 2 of the detection pipeline (CLAUDE.md §5) — catches the day Layer 1 cannot.
 *
 * Median-MAD (Layer 1) asks, every five minutes, whether *one* signal is unusual for the senior
 * right now. This layer asks a different question, once a day: whether the *combination* of
 * movement, stillness, screen idle, unlocks, steps and charging for one time block is unusual for
 * her, even when no single one of them crossed its own threshold. See [IsolationTree] for how one
 * tree turns "unusual" into a path length; this class only owns growing a forest of them and
 * turning their average path length into a single 0-1 score.
 *
 * **This class knows nothing about seniors, blocks, or baselines.** It trains on whatever
 * [DoubleArray] rows it is handed and scores whatever row it is asked to score. Turning a real
 * day's `DailyAggregate` and `Baseline` rows into those feature vectors — six numbers, five of
 * them z-scores against that block's own baseline, per the design decision that they must be
 * z-scores and never raw values — is a separate class's job (Phase 2, `AggregateFeatures`),
 * precisely so this class can be unit-tested against invented numbers (Phase 4) with nothing about
 * Room or a real senior involved.
 *
 * Trained fresh, on-device, every night (Phase 5, from
 * `com.pup.seenior.aggregation.NightlyAggregationWorker`) rather than shipped as a `.pkl`. At the
 * data volumes here — dozens of rows, a couple hundred trees — retraining from scratch takes
 * milliseconds: cheaper than the machinery a shipped, versioned model would need, and it can never
 * go stale against a baseline that keeps moving underneath it.
 *
 * No Android imports, like every other detection-layer class in this package.
 */
class IsolationForest private constructor(
    private val trees: List<IsolationTree>,
    /**
     * The subsample size each tree actually trained on — what [train] resolved `psi` to, never
     * `psi` itself. A senior with fewer block-days than `psi` still needs a score normalised
     * against how many rows each tree actually saw, not against a target that was never reached.
     */
    private val subsampleSize: Int
) {

    /**
     * Anomaly score in (0, 1]. Close to 1 means [point] isolated in very few random cuts across
     * the forest — the "unusual combination" case this layer exists for. Around 0.5 is
     * inconclusive. Comfortably below 0.5 is unremarkable. Phase 4's test suite is what turns this
     * into an actual pass/fail threshold (the plan's starting point is 0.62); this class only ever
     * returns the raw score, never a yes/no, in keeping with CLAUDE.md §14's requirement that this
     * layer's output stay a distinct thing from Layer 1's z-score and Layer 3's risk level.
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
         * Builds a forest of [trees] trees, each grown on its own random subsample of [psi] rows
         * drawn without replacement from [data] — or all of [data], if there are fewer rows than
         * [psi] to draw from (a senior early in her fortnight has fewer than 32 block-days banked).
         *
         * @param seed Fixed by the caller, never left to a platform default. Unseeded randomness
         *   here is one of the implementation traps worth naming explicitly: it passes four test
         *   runs and then fails the fifth, on a machine nobody is sitting at to explain why. There
         *   is deliberately no default value for this parameter — production code (Phase 5) should
         *   still pass a real seed, e.g. `System.nanoTime()`; training itself is not required to
         *   be reproducible night to night, only tests are, and a missing default forces every
         *   caller to make that choice on purpose instead of inheriting one by accident.
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
            // The original paper's height limit: a tree does not need to be tall enough to fully
            // isolate every ordinary row, only tall enough that anomalies — which isolate near the
            // root — stand out against the ones that don't.
            val heightLimit = ceil(ln(subsampleSize.toDouble()) / ln(2.0)).toInt().coerceAtLeast(1)

            val forest = List(trees) {
                val subsample = data.shuffled(random).take(subsampleSize)
                IsolationTree.build(subsample, heightLimit, random)
            }
            return IsolationForest(forest, subsampleSize)
        }
    }
}
