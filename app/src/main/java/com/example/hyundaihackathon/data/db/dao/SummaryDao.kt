package com.example.hyundaihackathon.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.hyundaihackathon.data.db.entity.DailySummaryEntity

@Dao
interface SummaryDao {
    /** Rows for dates in [from, to], both ISO dates. ISO strings sort like dates. */
    @Query("SELECT * FROM daily_summary WHERE date >= :from AND date <= :to")
    suspend fun between(from: String, to: String): List<DailySummaryEntity>

    @Upsert
    suspend fun upsertAll(rows: List<DailySummaryEntity>)
}
