package com.example.mentalworkloadapp.data.local.db.entitiy

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "predicted_levels")
data class PredictedLevel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val livelloStanchezza: Int
)