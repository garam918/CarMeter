package com.example.hyundaihackathon.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "counter_event", indices = [Index("ts")])
data class CounterEventEntity(
    @PrimaryKey val id: String,
    val featureKey: String,
    val zone: Int,
    val ts: Long,
    val tripId: Long?,
)
