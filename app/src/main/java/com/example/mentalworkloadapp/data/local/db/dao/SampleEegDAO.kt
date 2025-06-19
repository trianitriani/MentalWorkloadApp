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
    suspend fun insertSampleEeg(vararg samplesEgg: SampleEeg)

    @Insert
    suspend fun insertSamplesEeg(samplesEeg: List<SampleEeg>)

    @Update
    suspend fun updateSamplesEeg(samplesEeg: List<SampleEeg>)

    @Delete
    suspend fun deleteSamplesEeg(vararg samplesEeg: SampleEeg)

    @Delete
    suspend fun deleteSamplesEeg(sampleEeg: List<SampleEeg>)

    // query to getting samples after a threshold timestamp
    @Query("SELECT * FROM SampleEeg WHERE timestamp >= :from")
    suspend fun getAllSamplesFrom(from: Long) : List<SampleEeg>

    // query to getting all the samples ordered by timestamp
    @Query("SELECT * FROM SampleEeg ORDER BY timestamp ASC")
    suspend fun getAllSamplesOrderedByTimestamp(): List<SampleEeg>

    // query to getting last 50 samples ordered by timestamp
    @Query("SELECT * FROM SampleEeg ORDER BY timestamp ASC LIMIT 50")
    suspend fun getLastSamplesOrderedByTimestamp(): List<SampleEeg>

    @Query("SELECT * FROM SampleEeg ORDER BY timestamp DESC LIMIT :count")
    suspend fun getLastNSamples(count: Int): List<SampleEeg>

    // query for delete samples before a threshold timestamp
    @Query("DELETE FROM SampleEeg WHERE timestamp < :thresholdTimestamp")
    suspend fun deleteSamplesFrom(thresholdTimestamp: Long)

    @Query("DELETE FROM SampleEeg WHERE tiredness = 0")
    suspend fun deleteSamplesWithoutTiredness()

    // query for setting the tiredness when a user vote
    @Query("""
        UPDATE SampleEeg
        SET tiredness = :newTiredness
        WHERE timestamp IN (
            SELECT timestamp FROM SampleEeg
            ORDER BY timestamp DESC
            LIMIT 180*100
        )
    """)
    suspend fun updateTirednessSince(newTiredness: Int)
}