package com.example.hyundaihackathon.data.db.entity

import androidx.room.Entity

/** A cached day of one report item. [date] is the local date, ISO formatted. */
@Entity(tableName = "daily_summary", primaryKeys = ["date", "itemKey"])
data class DailySummaryEntity(
    val date: String,
    val itemKey: String,
    val onSec: Long,
    val count: Int,
    val avgLevel: Double?,
)
