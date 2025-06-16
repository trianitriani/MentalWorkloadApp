package com.example.mentalworkloadapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mentalworkloadapp.data.local.db.entitiy.PredictedLevelEntity

@Dao
interface PredictedLevelDao {
    @Insert
    suspend fun insert(prediction: PredictedLevelEntity)

    @Query("SELECT * FROM predicted_levels ORDER BY timestamp DESC")
    suspend fun getAll(): List<PredictedLevelEntity>
}
