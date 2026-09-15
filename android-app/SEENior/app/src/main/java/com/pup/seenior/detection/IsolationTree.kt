package com.pup.seenior.detection

import kotlin.math.ln
import kotlin.random.Random

/**
 * One random-split tree in an [IsolationForest] (CLAUDE.md §5, Layer 2).
 *
 * The idea, plainly: pick a random feature and a random split point between its min and max in
 * the current subsample, and recurse on both halves. A point sitting apart from the rest of the
 * data gets separated onto its own branch after only a handful of these random cuts, because
 * almost any cut nearby lands on the correct side of it; a point buried in a dense cluster needs
 * many cuts before it stands alone, because most random cuts fall between other points instead of
 * near it. Path length — how many cuts it took to isolate a point — is therefore an anomaly signal
 * on its own, with no notion of "normal" ever having to be defined up front. That is what makes
 * the whole forest unsupervised (CLAUDE.md §5, §10): it needs no labelled emergencies to train on,
 * only the senior's own block-days.
 *
 * Deliberately free of Android imports, like [FallDetector] and [FuzzyRiskClassifier] — a tree can
 * be built and queried from a plain JUnit test with fabricated feature vectors, which is the whole
 * point of the Isolation Forest test suite that follows in Phase 4.
 */
class IsolationTree private constructor(private val root: Node) {

    private sealed interface Node {
        data class Internal(
            val featureIndex: Int,
            val splitValue: Double,
            val left: Node,
            val right: Node
        ) : Node

        /** [size] is how many training rows still shared this branch when splitting stopped. */
        data class Leaf(val size: Int) : Node
    }

    /**
     * How many splits it took to isolate [point] on its own, plus a correction for the rows still
     * bundled together at the leaf it landed on.
     *
     * A leaf holding more than one row means splitting stopped early — hitting the tree's height
     * limit, or every remaining row in that branch being identical — not that isolation actually
     * finished there. Those rows are not "more anomalous than they look"; they are exactly as
     * anomalous as a leaf of that size implies, which [averagePathLengthCorrection] estimates
     * rather than silently treating them as if they had isolated down to one row at depth zero.
     */
    fun pathLength(point: DoubleArray): Double = pathLength(point, root, depth = 0)

    private fun pathLength(point: DoubleArray, node: Node, depth: Int): Double = when (node) {
        is Node.Leaf -> depth + averagePathLengthCorrection(node.size)
        is Node.Internal ->
            if (point[node.featureIndex] < node.splitValue) {
                pathLength(point, node.left, depth + 1)
            } else {
                pathLength(point, node.right, depth + 1)
            }
    }

    companion object {

        /**
         * Grows one tree from [data] — already the per-tree subsample [IsolationForest] drew, not
         * the full training set.
         *
         * @param heightLimit Splitting stops at this depth regardless of what is left to isolate.
         *   Anomalies isolate near the root; a tree does not need to be tall enough to fully
         *   isolate every ordinary row to be useful, and letting it try would cost time for no
         *   signal. [IsolationForest] passes `ceil(log2(subsampleSize))`, the original paper's
         *   value.
         */
        fun build(data: List<DoubleArray>, heightLimit: Int, random: Random): IsolationTree =
            IsolationTree(buildNode(data, depth = 0, heightLimit, random))

        private fun buildNode(
            data: List<DoubleArray>,
            depth: Int,
            heightLimit: Int,
            random: Random
        ): Node {
            if (depth >= heightLimit || data.size <= 1 || allIdentical(data)) {
                return Node.Leaf(data.size)
            }

            val dimensions = data[0].size
            // A feature that happens to be constant across this subsample can't split anything —
            // e.g. is_charging is 0 for an entire subsample far more often than not. Try a few
            // other random features before giving up and leafing here; one failed attempt does
            // not mean this branch is done isolating.
            repeat(MAX_SPLIT_ATTEMPTS) {
                val featureIndex = random.nextInt(dimensions)
                val values = data.map { it[featureIndex] }
                val min = values.min()
                val max = values.max()
                if (max > min) {
                    val splitValue = min + random.nextDouble() * (max - min)
                    val left = data.filter { it[featureIndex] < splitValue }
                    val right = data.filter { it[featureIndex] >= splitValue }
                    if (left.isNotEmpty() && right.isNotEmpty()) {
                        return Node.Internal(
                            featureIndex,
                            splitValue,
                            buildNode(left, depth + 1, heightLimit, random),
                            buildNode(right, depth + 1, heightLimit, random)
                        )
                    }
                }
            }
            return Node.Leaf(data.size)
        }

        private fun allIdentical(data: List<DoubleArray>): Boolean =
            data.all { it.contentEquals(data[0]) }

        /**
         * Average path length of an unsuccessful search in a binary search tree of [size] items —
         * the standard correction (Liu, Ting & Zhou, 2008; the same formula scikit-learn's
         * `IsolationForest` uses) for a leaf that stopped early rather than isolating down to a
         * single row. `size <= 1` needs no correction; `size == 2` uses the exact value (`H(1) =
         * 1`) rather than the log approximation below it, matching scikit-learn's own special case.
         *
         * `internal` rather than `private`: [IsolationForest] also calls this, on the subsample
         * size, to normalise a forest's raw average path length into the final 0-1 score.
         */
        internal fun averagePathLengthCorrection(size: Int): Double = when {
            size <= 1 -> 0.0
            size == 2 -> 1.0
            else -> 2.0 * (ln(size - 1.0) + EULER_MASCHERONI) - (2.0 * (size - 1) / size)
        }

        /** Euler-Mascheroni constant, for the harmonic-number approximation above. */
        private const val EULER_MASCHERONI = 0.5772156649015329

        private const val MAX_SPLIT_ATTEMPTS = 10
    }
}
