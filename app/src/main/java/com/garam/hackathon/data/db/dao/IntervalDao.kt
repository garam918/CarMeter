package com.garam.hackathon.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.garam.hackathon.data.db.entity.CounterEventEntity
import com.garam.hackathon.data.db.entity.FeatureIntervalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntervalDao {
    @Upsert
    suspend fun upsertIntervals(intervals: List<FeatureIntervalEntity>)

    @Upsert
    suspend fun upsertCounters(counters: List<CounterEventEntity>)

    @Transaction
    suspend fun saveBatch(intervals: List<FeatureIntervalEntity>, counters: List<CounterEventEntity>) {
        if (intervals.isNotEmpty()) upsertIntervals(intervals)
        if (counters.isNotEmpty()) upsertCounters(counters)
    }

    @Query("SELECT * FROM feature_interval WHERE endTs IS NULL")
    suspend fun openIntervals(): List<FeatureIntervalEntity>

    @Query("SELECT * FROM feature_interval ORDER BY startTs DESC LIMIT :limit")
    fun recentIntervals(limit: Int): Flow<List<FeatureIntervalEntity>>

    @Query("SELECT * FROM counter_event ORDER BY ts DESC LIMIT :limit")
    fun recentCounters(limit: Int): Flow<List<CounterEventEntity>>

    /** Every interval that overlaps [from, to); open ones count as running until now. */
    @Query("SELECT * FROM feature_interval WHERE startTs < :to AND (endTs IS NULL OR endTs > :from)")
    suspend fun allIntervalsOverlapping(from: Long, to: Long): List<FeatureIntervalEntity>

    @Query("SELECT * FROM counter_event WHERE ts >= :from AND ts < :to")
    suspend fun countersBetween(from: Long, to: Long): List<CounterEventEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM feature_interval WHERE endTs IS NULL AND startTs < :ts)")
    suspend fun hasOpenIntervalStartedBefore(ts: Long): Boolean

    /** Intervals that overlap [from, to): open ones count as running until now. */
    @Query(
        "SELECT * FROM feature_interval WHERE featureKey = :featureKey " +
            "AND startTs < :to AND (endTs IS NULL OR endTs > :from)",
    )
    suspend fun intervalsOverlapping(featureKey: String, from: Long, to: Long): List<FeatureIntervalEntity>
}
