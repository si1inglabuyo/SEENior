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
 * The once-a-day Layer 2 pass: trains on the senior's trailing fortnight, scores blocks not
 * yet scored, and raises an `ml_flag` alert when the freshest one looks wrong.
 *
 * Called from [com.pup.seenior.aggregation.NightlyAggregationWorker] right after
 * `BaselineUpdater`. Idempotent: it only scores rows `getWithoutIsolationForestScore()`
 * returns, so a second run finds nothing to do. The model is trained fresh every run and
 * never persisted (no `.pkl`); the [MlModelMetadata] row is an audit record of that run.
 */
object IsolationForestDetector {

    /**
     * Score at or above which a block is flagged. Tuned against the nine cases in
     * `IsolationForestTest`; see that class's tuning note.
     */
    const val THRESHOLD = 0.58

    /**
     * Below this many usable block-days the run scores nothing. A product decision: a forest
     * trained on a few days would flag the next one just for being new. Layer 1 protects the
     * senior in the meantime.
     */
    const val MIN_TRAINING_ROWS = 20

    const val MODEL_TYPE = "isolation_forest"

    /** Bumped when the feature set or scoring changes, so old audit rows stay readable. */
    private const val MODEL_VERSION = "1.0.0"

    /** Trailing window to train on — 14 days × 4 blocks = the 56 rows of design decision 1. */
    private const val TRAINING_WINDOW_DAYS = 14

    private const val TRIGGER_TYPE = "ml_flag"

    private const val STATUS_LOGGED = "logged"

    /**
     * How recently a block must have been rolled up for a flag to be raised. The first run
     * scores the whole backlog, and without this it could raise a fortnight of alerts at once.
     * Older blocks are still scored, silently.
     */
    private const val RAISE_WINDOW_MILLIS = 12L * 60 * 60 * 1000

    /** Placeholder for [MlModelMetadata.modelFilePath], which is non-null but unused since there is no model file. */
    private const val IN_MEMORY = "in-memory (trained on device, never written to disk)"

    /** What one nightly pass did, in enough detail to debug it from a log line. */
    sealed interface Outcome {
        /** An alert was raised and the caller owes it a response chain. */
        data class Raised(val alert: Alert, val score: Double) : Outcome

        /** A block was flagged but Layer 3 judged it not worth telling anyone: recorded as `logged`, like Layer 1's low-risk findings. */
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
     * Trains, scores, and raises at most one alert. Every usable unscored row is scored, but
     * only the highest-scoring block inside [RAISE_WINDOW_MILLIS] can raise, so one nightly
     * run produces at most one alert.
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
            // Not a fixed seed: tests need reproducibility, this doesn't.
            seed = now
        )

        recordTrainingRun(mlModelMetadataDao, trainingRows.size, now)

        val scored = mutableListOf<Pair<DailyAggregate, Double>>()
        for (aggregate in dailyAggregateDao.getWithoutIsolationForestScore(seniorId)) {
            // A thin block keeps a null score ("never judged") rather than a number nobody should trust.
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

        // Layer 1 and Layer 2 respect the declared nap (see [FuzzyRiskClassifier.isWithinNapWindow]).
        if (FuzzyRiskClassifier.isWithinNapWindow(
                minuteOfDay,
                onboarding.napTime.takeIf { onboarding.hasNap },
                onboarding.napDurationMinutes
            )
        ) {
            return Outcome.SuppressedByNap
        }

        if (alertDao.getActiveAlert(seniorId, TRIGGER_TYPE) != null) return Outcome.AlreadyActive

        // Layer 3. deviationScore stays 0.0 because a path-length score doesn't go in the
        // z-score column; zero caps a pure Layer 2 finding at Medium.
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
            // The block that scored, so the prompt can say "your morning looked unusual".
            timeBlock = aggregate.timeBlock,
            // Null for this trigger type; the score lives in Daily_Aggregates.isolation_forest_score.
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
     * One audit row per training run. [MlModelMetadata.accuracyScore] stays null: the model is
     * unsupervised, so there is no labelled set to measure against.
     */
    private suspend fun recordTrainingRun(dao: MlModelMetadataDao, rows: Int, now: Long) {
        dao.deactivateAllOfType(MODEL_TYPE)
        val id = dao.insert(
            MlModelMetadata(
                modelType = MODEL_TYPE,
                modelVersion = MODEL_VERSION,
                trainedAt = now,
                modelFilePath = IN_MEMORY,
                // Built by hand rather than org.json, so this works in plain JVM unit tests.
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
     * Whether this block was summarised from enough readings to judge. The expected count
     * comes from the senior's own schedule, since a full night differs between people.
     */
}
