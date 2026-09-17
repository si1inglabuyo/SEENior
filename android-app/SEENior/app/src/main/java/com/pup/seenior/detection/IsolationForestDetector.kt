package com.pup.seenior.detection

import com.pup.seenior.database.dao.AlertDao
import com.pup.seenior.database.dao.BaselineDao
import com.pup.seenior.database.dao.DailyAggregateDao
import com.pup.seenior.database.dao.MlModelMetadataDao
import com.pup.seenior.database.entities.Alert
import com.pup.seenior.database.entities.DailyAggregate
import com.pup.seenior.database.entities.MlModelMetadata
import com.pup.seenior.database.entities.SeniorOnboarding
import java.util.UUID

/**
 * Phase 5 of the Isolation Forest build — the once-a-day pass that trains Layer 2 on the senior's
 * own trailing fortnight, scores the blocks nobody has scored yet, and raises an `ml_flag` alert
 * when the freshest one looks wrong (CLAUDE.md §5).
 *
 * Called from [com.pup.seenior.aggregation.NightlyAggregationWorker] immediately after
 * `BaselineUpdater`: the aggregates have just been written, the baseline they are scored against is
 * current, and the database is already open. No second worker and no second schedule to debug.
 *
 * **Idempotent by construction, not by a date check.** It scores only what
 * `getWithoutIsolationForestScore()` returns, so a second run the same day finds nothing left to
 * do — and unlike a date check, that still holds after a run is missed entirely.
 *
 * **Trained fresh every run and never persisted.** Design decision 3 of the plan: no `.pkl`, no
 * export pipeline, no shipped model. Fifty-six rows and a hundred trees is a few milliseconds, and
 * a model retrained nightly cannot go stale against a baseline that keeps moving underneath it.
 * The [MlModelMetadata] row written per run is an audit record of *that* training, not a pointer to
 * a file that exists.
 */
object IsolationForestDetector {

    /**
     * Score at or above which a block is flagged. Tuned against the nine cases in
     * `IsolationForestTest` (Phase 4), not chosen by feel: the plan's 0.62 starting point sat
     * inside the flag cluster and would have missed the "combination" case entirely. See that
     * class's tuning note for the measured spread this sits in the middle of.
     */
    const val THRESHOLD = 0.58

    /**
     * Below this many usable block-days, the run bails out and scores nothing.
     *
     * This is the cold-start guard, and it is a product decision rather than an algorithmic one —
     * [IsolationForest.train] itself is happy with two rows. A forest trained on five days would
     * flag the sixth as strange purely for being sixth, and "strange" is not a claim worth making
     * about a senior whose routine the app has barely met. Silence is the correct output here:
     * Layer 1 has been protecting her from a seed baseline since day one.
     */
    const val MIN_TRAINING_ROWS = 20

    const val MODEL_TYPE = "isolation_forest"

    /** Bumped when the feature set or the scoring arithmetic changes, so old audit rows stay readable. */
    private const val MODEL_VERSION = "1.0.0"

    /** Trailing window to train on — 14 days × 4 blocks = the 56 rows of design decision 1. */
    private const val TRAINING_WINDOW_DAYS = 14

    private const val TRIGGER_TYPE = "ml_flag"

    private const val STATUS_LOGGED = "logged"

    /**
     * How recently a block must have been rolled up for a flag on it to be worth raising.
     *
     * The first run on a phone with history scores the *entire* backlog at once, and without this
     * a fortnight of unscored blocks could raise a fortnight's worth of alerts in one pass — about
     * a senior who is standing right there, fine. Older blocks are still scored, silently: the
     * score lands in the column for the record and for the next training window, and nobody is
     * telephoned about last Tuesday.
     */
    private const val RAISE_WINDOW_MILLIS = 12L * 60 * 60 * 1000

    /**
     * Placeholder for [MlModelMetadata.modelFilePath], which is declared non-null because it was
     * written for the shipped-`.pkl` design this project deliberately did not take.
     */
    private const val IN_MEMORY = "in-memory (trained on device, never written to disk)"

    /** What one nightly pass did, in enough detail to debug it from a log line. */
    sealed interface Outcome {
        /** An alert was raised and the caller owes it a response chain. */
        data class Raised(val alert: Alert, val score: Double) : Outcome

        /**
         * A block was flagged, but Layer 3 judged it not worth telling anyone about at this hour —
         * recorded with status `logged` and nobody notified, exactly as Layer 1 does with its own
         * low-risk findings.
         */
        data class Logged(val score: Double) : Outcome

        /** Scores were written; nothing recent crossed [THRESHOLD]. The ordinary outcome. */
        data class NothingFlagged(val scored: Int) : Outcome

        /** Cold start — see [MIN_TRAINING_ROWS]. Nothing was scored, which is the point. */
        data class NotEnoughData(val usableRows: Int) : Outcome

        /** Inside the senior's declared nap, where stillness is the expected reading (§6). */
        data object SuppressedByNap : Outcome

        /** An `ml_flag` alert is already working its way through the chain. */
        data object AlreadyActive : Outcome
    }

    /**
     * Trains, scores, and raises at most one alert.
     *
     * Scoring always happens for every usable unscored row; raising is deliberately narrower, and
     * only ever for the single highest-scoring block inside [RAISE_WINDOW_MILLIS]. One nightly run
     * can produce at most one alert — a layer that can produce twelve at once is a layer a family
     * mutes.
     */
    suspend fun run(
        seniorId: Int,
        onboarding: SeniorOnboarding,
        dailyAggregateDao: DailyAggregateDao,
        baselineDao: BaselineDao,
        alertDao: AlertDao,
        mlModelMetadataDao: MlModelMetadataDao,
        now: Long = System.currentTimeMillis()
    ): Outcome {
        val baselines = baselineDao.getAllBySeniorOnce(seniorId)

        val trainingRows = dailyAggregateDao
            .getRecentDays(seniorId, TRAINING_WINDOW_DAYS)
            .filter { AggregateFeatures.isUsable(it, onboarding) }

        if (trainingRows.size < MIN_TRAINING_ROWS) return Outcome.NotEnoughData(trainingRows.size)

        val forest = IsolationForest.train(
            data = trainingRows.map { AggregateFeatures.vector(it, baselines) },
            // Not a fixed seed. Reproducibility is a property the tests need, not this: two
            // nightly runs over the same rows landing on slightly different scores is honest,
            // since the forest is genuinely a random construction either way.
            seed = now
        )

        recordTrainingRun(mlModelMetadataDao, trainingRows.size, now)

        val scored = mutableListOf<Pair<DailyAggregate, Double>>()
        for (aggregate in dailyAggregateDao.getWithoutIsolationForestScore(seniorId)) {
            // A thin block keeps its null score rather than being given a number nobody should
            // trust. Null is the honest record of "never judged", and it costs one skipped row
            // per run to leave it that way.
            if (!AggregateFeatures.isUsable(aggregate, onboarding)) continue

            val score = forest.score(AggregateFeatures.vector(aggregate, baselines))
            dailyAggregateDao.updateIsolationForestScore(aggregate.aggregateId, score)
            scored += aggregate to score
        }

        val candidate = scored
            .filter { (aggregate, score) ->
                score >= THRESHOLD && now - aggregate.createdAt <= RAISE_WINDOW_MILLIS
            }
            .maxByOrNull { (_, score) -> score }
            ?: return Outcome.NothingFlagged(scored.size)

        return raise(seniorId, onboarding, alertDao, candidate.first, candidate.second, now)
    }

    private suspend fun raise(
        seniorId: Int,
        onboarding: SeniorOnboarding,
        alertDao: AlertDao,
        aggregate: DailyAggregate,
        score: Double,
        now: Long
    ): Outcome {
        val minuteOfDay = FuzzyRiskClassifier.minuteOfDay(now)

        // §6, and the case [FuzzyRiskClassifier.isWithinNapWindow]'s own KDoc was written for:
        // "Only Layer 1 and Layer 2 consult this."
        if (FuzzyRiskClassifier.isWithinNapWindow(
                minuteOfDay,
                onboarding.napTime.takeIf { onboarding.hasNap },
                onboarding.napDurationMinutes
            )
        ) {
            return Outcome.SuppressedByNap
        }

        if (alertDao.getActiveAlert(seniorId, TRIGGER_TYPE) != null) return Outcome.AlreadyActive

        // Layer 3. deviationScore stays 0.0 — CLAUDE.md §8 forbids putting a path-length score in
        // the z-score column, and there genuinely is no z-score here. Zero puts Deviation.MILD at
        // full membership, which is what caps a pure Layer 2 finding at Medium.
        val risk = FuzzyRiskClassifier.classify(
            FuzzyRiskClassifier.Inputs(
                deviationScore = 0.0,
                restExpectation = FuzzyRiskClassifier.restExpectation(
                    minuteOfDay, onboarding.wakeTime, onboarding.sleepTime
                ),
                mlFlagScore = score
            )
        )

        val alert = Alert(
            seniorId = seniorId,
            syncId = UUID.randomUUID().toString(),
            triggerType = TRIGGER_TYPE,
            riskLevel = risk.stored,
            // The block that scored, not the block it is now. This is what the wellness prompt
            // turns into "your morning looked unusual" rather than a bare question.
            timeBlock = aggregate.timeBlock,
            // Null, permanently, for this trigger type (CLAUDE.md §8). The path-length score lives
            // in Daily_Aggregates.isolation_forest_score, where it was just written.
            deviationScore = null,
            status = if (risk == FuzzyRiskClassifier.Risk.LOW) STATUS_LOGGED else "pending",
            triggeredAt = now
        )

        val stored = alert.copy(alertId = alertDao.insert(alert).toInt())
        return if (risk == FuzzyRiskClassifier.Risk.LOW) {
            Outcome.Logged(score)
        } else {
            Outcome.Raised(stored, score)
        }
    }

    /**
     * One audit row per training run.
     *
     * [MlModelMetadata.accuracyScore] is left null and always will be: Isolation Forest is
     * unsupervised, so there is no labelled set to be accurate against, and inventing a number
     * would be worse than the empty column. What this row is actually for is answering "what was
     * the model that scored this block, and how much of her routine had it seen" months later.
     */
    private suspend fun recordTrainingRun(dao: MlModelMetadataDao, rows: Int, now: Long) {
        dao.deactivateAllOfType(MODEL_TYPE)
        val id = dao.insert(
            MlModelMetadata(
                modelType = MODEL_TYPE,
                modelVersion = MODEL_VERSION,
                trainedAt = now,
                modelFilePath = IN_MEMORY,
                // Built by hand rather than with org.json so this object stays usable from a plain
                // JVM unit test, where the framework's JSON classes are stubs that throw.
                featureNames = AggregateFeatures.FEATURE_NAMES.joinToString(
                    separator = "\",\"", prefix = "[\"", postfix = "\"]"
                ),
                isActive = true,
                accuracyScore = null
            )
        ).toInt()
        dao.activate(id)
        android.util.Log.i("IsolationForest", "Trained on $rows usable block-days (model $id)")
    }

    /**
     * Whether this block was summarised from enough readings to judge.
     *
     * The expected count is computed from *this senior's own* declared schedule rather than read
     * from a constant: a five-hour night and an eleven-hour night are both perfectly normal
     * depending on whose they are, and one senior's full night is another's thin one.
     */
}
