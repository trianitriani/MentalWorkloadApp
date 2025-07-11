package com.example.mentalworkloadapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import org.checkerframework.checker.units.qual.Time
import java.sql.Timestamp

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
    @Query("SELECT * FROM SampleEeg ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getSessionSamplesOrderedByTimestamp(limit:Int , offset: Int): List<SampleEeg>

    @Query("""
        SELECT * 
        FROM SampleEeg 
        WHERE tiredness = -1
        ORDER BY timestamp DESC 
        LIMIT :count
    """)
    suspend fun getLastNSamplesOfLastSession(count: Int): List<SampleEeg>

    @Query("""
        SELECT MAX(session_id)
        FROM SampleEeg
    """)
    suspend fun getLastSessionId() : Int?

    // query for delete samples before a threshold timestamp
    @Query("DELETE FROM SampleEeg WHERE timestamp < :thresholdTimestamp")
    suspend fun deleteSamplesFrom(thresholdTimestamp: Long)

    @Query("DELETE FROM SampleEeg WHERE tiredness = -1")
    suspend fun deleteSamplesWithoutTiredness()

    // query for setting the tiredness when a user vote
    @Query("UPDATE SampleEeg SET tiredness = :newTiredness WHERE timestamp >= :since")
    suspend fun updateTirednessSince(newTiredness: Int, since: Long)

    //query for counting the number of samples available in the database
    @Query("SELECT COUNT(*) FROM SampleEEg")
    suspend fun countSamples() : Long

    //query to delete all the data in the database
    @Query("DELETE FROM SampleEeg")
    suspend fun deleteAllData() : Int

    @Query("SELECT * FROM SampleEeg ORDER BY timestamp DESC LIMIT :count")
    suspend fun getLastNRawSamples(count: Int): List<SampleEeg>
}