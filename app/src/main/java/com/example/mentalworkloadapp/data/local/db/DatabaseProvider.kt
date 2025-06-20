package com.example.mentalworkloadapp.data.local.db

import android.content.Context
import androidx.room.Room
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO

object DatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "selene-db"
            ).build().also { INSTANCE = it }
        }
    }

    fun getSampleEegDao(context: Context): SampleEegDAO {
        return getDatabase(context).sampleEegDao()
    }
}
