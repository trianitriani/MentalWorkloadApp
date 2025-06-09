package com.example.mentalworkloadapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import kotlin.reflect.KClass

@Dao
interface SampleEegDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSampleEeg(vararg samplesEgg: SampleEeg)
    @Insert
    suspend fun insertSamplesEeg(samplesEeg: List<SampleEeg>)

    @Update
    suspend fun updateSamplesEeg(vararg samplesEeg: SampleEeg)

    @Delete
    suspend fun deleteSamplesEeg(vararg samplesEeg: SampleEeg)
    @Delete
    suspend fun deleteSamplesEeg(sampleEeg: List<SampleEeg>)

    // query to getting samples after a threshold timestamp
    @Query("SELECT * FROM SampleEeg WHERE timestamp >= :from")
    suspend fun getAllSamplesFrom(from: Long) : List<SampleEeg>

    // query for delete samples before a threshold timestamp
    @Query("DELETE FROM SampleEeg WHERE timestamp < :thresholdTimestamp")
    suspend fun deleteSamplesFrom(thresholdTimestamp: Long)

    @Query("DELETE FROM SampleEeg WHERE tiredness = 0")
    suspend fun deleteSamplesWithoutTiredness()

    // query for setting the tiredness when a user vote
    @Query("UPDATE SampleEeg SET tiredness = :newTiredness WHERE timestamp >= :since")
    suspend fun updateTirednessSince(newTiredness: Int, since: Long)

}