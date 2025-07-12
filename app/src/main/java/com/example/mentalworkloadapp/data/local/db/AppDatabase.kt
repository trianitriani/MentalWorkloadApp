package com.example.mentalworkloadapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg

@Database(entities = [SampleEeg::class], version = 5)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sampleEegDao(): SampleEegDAO
}