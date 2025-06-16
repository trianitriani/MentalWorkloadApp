package com.example.mentalworkloadapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import com.example.mentalworkloadapp.data.local.db.dao.PredictedLevelDAO
import com.example.mentalworkloadapp.data.local.db.entitiy.PredictedLevel

@Database(entities = [SampleEeg::class, PredictedLevel::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sampleEegDao(): SampleEegDAO
    abstract fun predictedLevelDao(): PredictedLevelDAO
}