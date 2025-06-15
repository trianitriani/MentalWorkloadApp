package com.example.mentaloadapp.data.local.db.dao.entitiy
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "predicted_levels")
data class PredictedLevelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val livelloStanchezza: Int
)