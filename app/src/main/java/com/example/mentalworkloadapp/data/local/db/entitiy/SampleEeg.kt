package com.example.mentalworkloadapp.data.local.db.entitiy

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity
data class SampleEeg(
    @PrimaryKey val timestamp: Long,
    @ColumnInfo(name = "channel_01") val ch_01: Double,
    @ColumnInfo(name = "channel_02") val ch_02: Double,
    @ColumnInfo(name = "channel_03") val ch_03: Double,
    @ColumnInfo(name = "channel_04") val ch_04: Double,
    @ColumnInfo(name = "channel_05") val ch_05: Double,
    @ColumnInfo(name = "channel_06") val ch_06: Double,
    @ColumnInfo(name = "channel_07") val ch_07: Double,
    @ColumnInfo(name = "channel_08") val ch_08: Double
)