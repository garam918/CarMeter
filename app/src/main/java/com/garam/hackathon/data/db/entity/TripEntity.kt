package com.garam.hackathon.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trip", indices = [Index("startTs")])
data class TripEntity(
    @PrimaryKey val tripId: Long,
    val startTs: Long,
    val endTs: Long?,
    val odoKmStart: Double?,
    val odoKmEnd: Double?,
    val drivingMinStart: Int?,
    val drivingMinEnd: Int?,
    val fuelEconomyEnd: Double?,
    val evEfficiencyEnd: Double?,
    val socStart: Int?,
    val socEnd: Int?,
)
