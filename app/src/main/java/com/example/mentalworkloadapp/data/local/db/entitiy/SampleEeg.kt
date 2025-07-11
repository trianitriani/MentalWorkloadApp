package com.example.mentalworkloadapp.data.local.db.entitiy

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import mylibrary.mindrove.SensorData

@Entity
data class SampleEeg(
    // Add an auto-generating primary key. Must be a var with a default value.
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    // Make timestamp a regular, indexed column for fast queries
    @ColumnInfo(index = true)
    val timestamp: Long,

    @ColumnInfo(name = "channel_c1") val ch_c1: Double,
    @ColumnInfo(name = "channel_c2") val ch_c2: Double,
    @ColumnInfo(name = "channel_c3") val ch_c3: Double,
    @ColumnInfo(name = "channel_c4") val ch_c4: Double,
    @ColumnInfo(name = "channel_c5") val ch_c5: Double,
    @ColumnInfo(name = "channel_c6") val ch_c6: Double,
    @ColumnInfo(name = "channel_right_ear") val ch_r_ear: Double,
    @ColumnInfo(name = "channel_left_ear") val ch_l_ear: Double,
    @ColumnInfo(name = "tiredness") val tiredness: Int,
    @ColumnInfo(name = "session_id") val session_id: Int
) {
    @Ignore
    var accelerationX: Double = 0.0
    @Ignore
    var accelerationY: Double = 0.0
    @Ignore
    var accelerationZ: Double = 0.0
    @Ignore
    var angularRateX: Double = 0.0
    @Ignore
    var angularRateY: Double = 0.0
    @Ignore
    var angularRateZ: Double = 0.0
    @Ignore
    var voltage: UInt = 0u
    @Ignore
    var numberOfMeasurement: UInt = 0u

    companion object {
        fun fromSensorData(sensorData: SensorData, sessionId: Int): SampleEeg {
            val sample = SampleEeg(
                // id is not provided here, Room will generate it on insert
                timestamp = System.currentTimeMillis(),
                ch_c1 = sensorData.channel1 * 0.045,
                ch_c2 = sensorData.channel2 * 0.045,
                ch_c3 = sensorData.channel3 * 0.045,
                ch_c4 = sensorData.channel4 * 0.045,
                ch_c5 = sensorData.channel5 * 0.045,
                ch_c6 = sensorData.channel6 * 0.045,
                ch_r_ear = sensorData.channel7 * 0.045,
                ch_l_ear = sensorData.channel8 * 0.045,
                tiredness = -1,
                session_id = sessionId
            )
            // Assign the ignored properties
            sample.accelerationX = sensorData.accelerationX * 0.061035 * 0.001
            sample.accelerationY = sensorData.accelerationY * 0.061035 * 0.001
            sample.accelerationZ = sensorData.accelerationZ * 0.061035 * 0.001
            sample.angularRateX = sensorData.angularRateX * 0.01526
            sample.angularRateY = sensorData.angularRateY * 0.01526
            sample.angularRateZ = sensorData.angularRateZ * 0.01526
            sample.voltage = sensorData.voltage
            sample.numberOfMeasurement = sensorData.numberOfMeasurement
            return sample
        }
    }
}