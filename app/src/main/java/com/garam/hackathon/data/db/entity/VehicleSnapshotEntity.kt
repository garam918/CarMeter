package com.garam.hackathon.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_snapshot", indices = [Index("ts")])
data class VehicleSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val reason: String,
    val odometerKm: Double?,
    val drivingTimeMin: Int?,
    val fuelEconomy: Double?,
    val evEfficiency: Double?,
    val socPercent: Int?,
)
