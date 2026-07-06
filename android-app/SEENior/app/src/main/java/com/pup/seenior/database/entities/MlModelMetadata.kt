package com.pup.seenior.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ML_Model_Metadata",
    indices = [Index("is_active"), Index("model_type")]
)
data class MlModelMetadata(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "model_id") val modelId: Int = 0,
    /** e.g. "isolation_forest" */
    @ColumnInfo(name = "model_type") val modelType: String,
    /** Semantic version string, e.g. "1.0.0" */
    @ColumnInfo(name = "model_version") val modelVersion: String,
    /** Epoch millis of when this model was trained server-side */
    @ColumnInfo(name = "trained_at") val trainedAt: Long,
    /** Absolute path to the model file (.pkl / .tflite) stored on the device */
    @ColumnInfo(name = "model_file_path") val modelFilePath: String,
    /** JSON array of feature names the model was trained on, e.g. ["avg_movement_score", ...] */
    @ColumnInfo(name = "feature_names") val featureNames: String,
    /** Only one model of a given type should be active at a time */
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    /** Validation accuracy score from the training run, null if not reported */
    @ColumnInfo(name = "accuracy_score") val accuracyScore: Double? = null
)
