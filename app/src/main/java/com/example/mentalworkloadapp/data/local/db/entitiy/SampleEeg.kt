package com.example.mentalworkloadapp.data.local.db.entitiy

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import mylibrary.mindrove.SensorData

@Entity
class SampleEeg(
    @PrimaryKey val timestamp: Long,
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
    // fields not saved in the database
    @Ignore var accelerationX: Double = 0.0
    @Ignore var accelerationY: Double = 0.0
    @Ignore var accelerationZ: Double = 0.0
    @Ignore var angularRateX: Double = 0.0
    @Ignore var angularRateY: Double = 0.0
    @Ignore var angularRateZ: Double = 0.0
    @Ignore var voltage: UInt = 0u
    @Ignore var numberOfMeasurement: UInt = 0u

    // internal constructor ignored by room
    @Ignore
    constructor(
        timestamp: Long,
        ch_c1: Double,
        ch_c2: Double,
        ch_c3: Double,
        ch_c4: Double,
        ch_c5: Double,
        ch_c6: Double,
        ch_r_ear: Double,
        ch_l_ear: Double,
        tiredness: Int,
        session_id: Int,
        accelerationX: Double,
        accelerationY: Double,
        accelerationZ: Double,
        angularRateX: Double,
        angularRateY: Double,
        angularRateZ: Double,
        voltage: UInt,
        numberOfMeasurement: UInt
    ) : this(timestamp, ch_c1, ch_c2, ch_c3, ch_c4, ch_c5, ch_c6, ch_r_ear, ch_l_ear, tiredness, session_id) {
        this.accelerationX = accelerationX
        this.accelerationY = accelerationY
        this.accelerationZ = accelerationZ
        this.angularRateX = angularRateX
        this.angularRateY = angularRateY
        this.angularRateZ = angularRateZ
        this.voltage = voltage
        this.numberOfMeasurement = numberOfMeasurement
    }

    companion object {
        fun fromSensorData(sensorData: SensorData, sessionId: Int): SampleEeg {
            return SampleEeg(
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
                session_id = sessionId,
                accelerationX = sensorData.accelerationX * 0.061035 * 0.001,
                accelerationY = sensorData.accelerationY * 0.061035 * 0.001,
                accelerationZ = sensorData.accelerationZ * 0.061035 * 0.001,
                angularRateX = sensorData.angularRateX * 0.01526,
                angularRateY = sensorData.angularRateY * 0.01526,
                angularRateZ = sensorData.angularRateZ * 0.01526,
                voltage = sensorData.voltage,
                numberOfMeasurement = sensorData.numberOfMeasurement
            )
        }
    }
}
