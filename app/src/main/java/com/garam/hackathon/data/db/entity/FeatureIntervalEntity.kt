package com.garam.hackathon.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feature_interval",
    indices = [Index("startTs"), Index("endTs")],
)
data class FeatureIntervalEntity(
    @PrimaryKey val id: String,
    val featureKey: String,
    val zone: Int,
    val level: Int,
    val startTs: Long,
    val endTs: Long?,
    val tripId: Long?,
    val closedBy: String?,
)
