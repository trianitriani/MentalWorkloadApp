package com.example.mentalworkloadapp.data.local.db

import android.content.Context
import androidx.room.Room
import com.example.mentalworkloadapp.data.local.db.dao.SampleEegDAO
import kotlinx.coroutines.sync.Mutex

object DatabaseProvider {
    val dbMutex = Mutex()
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "selene-db"
            )
            .createFromAsset("selene-db")
            .fallbackToDestructiveMigration()
            .build()
        }
    }

    fun getSampleEegDao(context: Context): SampleEegDAO {
        return getDatabase(context).sampleEegDao()
    }
}
