package com.example.mentalworkloadapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg

@Dao
interface SampleEegDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSampleEeg(vararg samplesEgg: SampleEeg)
    @Insert
    fun insertSamplesEeg(samplesEeg: List<SampleEeg>)

    @Update
    fun updateSamplesEeg(vararg samplesEeg: SampleEeg)

    @Delete
    fun deleteSamplesEeg(vararg samplesEeg: SampleEeg)
    @Delete
    fun deleteSamplesEeg(sampleEeg: List<SampleEeg>)

    // query to getting samples after a threshold timestamp
    @Query("SELECT * FROM SampleEeg WHERE timestamp >= :from")
    fun getAllSamplesFrom(from: Long) : List<SampleEeg>

    // query for delete samples before a threshold timestamp
    @Query("DELETE FROM SampleEeg WHERE timestamp < :thresholdTimestamp")
    fun deleteSamplesFrom(thresholdTimestamp: Long)

}