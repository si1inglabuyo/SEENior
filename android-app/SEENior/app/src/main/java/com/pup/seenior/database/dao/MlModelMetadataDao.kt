package com.pup.seenior.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pup.seenior.database.entities.MlModelMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface MlModelMetadataDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(model: MlModelMetadata): Long

    @Update
    suspend fun update(model: MlModelMetadata)

    @Delete
    suspend fun delete(model: MlModelMetadata)

    @Query("SELECT * FROM ML_Model_Metadata ORDER BY trained_at DESC")
    fun getAll(): Flow<List<MlModelMetadata>>

    @Query("SELECT * FROM ML_Model_Metadata WHERE model_id = :modelId")
    suspend fun getById(modelId: Int): MlModelMetadata?

    /** Returns the currently active model for a given type — used before running daily inference */
    @Query("SELECT * FROM ML_Model_Metadata WHERE model_type = :modelType AND is_active = 1 LIMIT 1")
    suspend fun getActiveModel(modelType: String): MlModelMetadata?

    /**
     * Deactivates all models of a given type, then the caller activates the new one.
     * Ensures only one active model per type at a time.
     */
    @Query("UPDATE ML_Model_Metadata SET is_active = 0 WHERE model_type = :modelType")
    suspend fun deactivateAllOfType(modelType: String)

    @Query("UPDATE ML_Model_Metadata SET is_active = 1 WHERE model_id = :modelId")
    suspend fun activate(modelId: Int)

    @Query("SELECT * FROM ML_Model_Metadata WHERE model_type = :modelType ORDER BY trained_at DESC")
    suspend fun getAllOfType(modelType: String): List<MlModelMetadata>
}
