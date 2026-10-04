package com.pup.seenior.detection

import kotlin.math.ln
import kotlin.random.Random

/**
 * One random-split tree in an [IsolationForest] (Layer 2).
 *
 * Pick a random feature and a random split point between its min and max, then recurse on
 * both halves. A point far from the rest is isolated after few cuts, while one in a dense
 * cluster needs many, so path length is an anomaly signal without defining "normal" up
 * front. That makes the forest unsupervised: it needs no labelled emergencies.
 *
 * No Android imports, like [FallDetector] and [FuzzyRiskClassifier], so a tree can be tested
 * with fabricated vectors.
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
     * How many splits it took to isolate [point], plus a correction for the rows still bundled
     * at the leaf it landed on. A leaf with several rows means splitting stopped early (height
     * limit, or identical rows), so [averagePathLengthCorrection] estimates the missing depth.
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
         * Grows one tree from [data], the per-tree subsample [IsolationForest] drew.
         *
         * @param heightLimit splitting stops at this depth. Anomalies isolate near the root, so
         *   a tree needn't isolate every ordinary row. [IsolationForest] passes
         *   `ceil(log2(subsampleSize))`, the original paper's value.
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
            // A feature that is constant in this subsample can't split anything (is_charging
            // often is). Try a few other features before making a leaf.
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
         * Average path length of an unsuccessful search in a binary search tree of [size] items
         * (Liu, Ting & Zhou, 2008; the formula scikit-learn uses), as the correction for a leaf
         * that stopped early. `size <= 1` needs none and `size == 2` uses the exact value.
         * `internal` because [IsolationForest] also uses it to normalise the final 0-1 score.
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
