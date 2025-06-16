package com.example.mentalworkloadapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mentalworkloadapp.data.local.db.entitiy.PredictedLevel

@Dao
interface PredictedLevelDAO {
    @Insert
    suspend fun insert(prediction: PredictedLevel)

    @Query("SELECT * FROM predicted_levels ORDER BY timestamp DESC")
    suspend fun getAll(): List<PredictedLevel>
}
