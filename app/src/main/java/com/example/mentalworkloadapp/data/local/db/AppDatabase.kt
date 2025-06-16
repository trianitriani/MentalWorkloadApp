package com.example.mentalworkloadapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import com.example.mentalworkloadapp.data.local.db.dao.PredictedLevelDao
import com.example.mentalworkloadapp.data.local.db.entitiy.PredictedLevelEntity

@Database(entities = [SampleEeg::class, PredictedLevelEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sampleEegDao(): SampleEegDAO
    abstract fun predictedLevelDao(): PredictedLevelDao
}