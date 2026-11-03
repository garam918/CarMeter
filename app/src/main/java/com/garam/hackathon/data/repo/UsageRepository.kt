package com.garam.hackathon.data.repo

import com.garam.hackathon.data.ClosedBy
import com.garam.hackathon.data.CounterEvent
import com.garam.hackathon.data.Interval
import com.garam.hackathon.data.db.dao.IntervalDao
import com.garam.hackathon.data.db.entity.CounterEventEntity
import com.garam.hackathon.data.db.entity.FeatureIntervalEntity
import com.garam.hackathon.vehicle.FeatureKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UsageRepository {
    /** Saves one batch atomically. Rows with an existing ID are replaced. */
    suspend fun save(intervals: List<Interval>, counters: List<CounterEvent> = emptyList())

    suspend fun openIntervals(): List<Interval>

    fun recentIntervals(limit: Int): Flow<List<Interval>>

    fun recentCounters(limit: Int): Flow<List<CounterEvent>>

    /** Intervals of [featureKey] that overlap [from, to). */
    suspend fun intervalsOverlapping(featureKey: FeatureKey, from: Long, to: Long): List<Interval>

    /** Intervals of every feature that overlap [from, to). */
    suspend fun allIntervalsOverlapping(from: Long, to: Long): List<Interval>

    suspend fun countersBetween(from: Long, to: Long): List<CounterEvent>

    /** Whether an interval that started before [ts] is still open, so days before it may still change. */
    suspend fun hasOpenIntervalStartedBefore(ts: Long): Boolean
}

class RoomUsageRepository(private val dao: IntervalDao) : UsageRepository {
    override suspend fun save(intervals: List<Interval>, counters: List<CounterEvent>) =
        dao.saveBatch(intervals.map { it.toEntity() }, counters.map { it.toEntity() })

    override suspend fun openIntervals(): List<Interval> = dao.openIntervals().mapNotNull { it.toInterval() }

    override fun recentIntervals(limit: Int): Flow<List<Interval>> =
        dao.recentIntervals(limit).map { rows -> rows.mapNotNull { it.toInterval() } }

    override fun recentCounters(limit: Int): Flow<List<CounterEvent>> =
        dao.recentCounters(limit).map { rows -> rows.mapNotNull { it.toCounter() } }

    override suspend fun intervalsOverlapping(featureKey: FeatureKey, from: Long, to: Long): List<Interval> =
        dao.intervalsOverlapping(featureKey.name, from, to).mapNotNull { it.toInterval() }

    override suspend fun allIntervalsOverlapping(from: Long, to: Long): List<Interval> =
        dao.allIntervalsOverlapping(from, to).mapNotNull { it.toInterval() }

    override suspend fun countersBetween(from: Long, to: Long): List<CounterEvent> =
        dao.countersBetween(from, to).mapNotNull { it.toCounter() }

    override suspend fun hasOpenIntervalStartedBefore(ts: Long): Boolean = dao.hasOpenIntervalStartedBefore(ts)
}

/** Rows of features this build no longer knows are skipped. */
internal fun featureKeyOf(name: String): FeatureKey? = FeatureKey.entries.firstOrNull { it.name == name }

private fun Interval.toEntity() = FeatureIntervalEntity(
    id = id,
    featureKey = featureKey.name,
    zone = zone,
    level = level,
    startTs = startTs,
    endTs = endTs,
    tripId = tripId,
    closedBy = closedBy?.name,
)

private fun FeatureIntervalEntity.toInterval(): Interval? {
    val key = featureKeyOf(featureKey) ?: return null
    return Interval(
        id = id,
        featureKey = key,
        zone = zone,
        level = level,
        startTs = startTs,
        endTs = endTs,
        closedBy = closedBy?.let { name -> ClosedBy.entries.firstOrNull { it.name == name } },
        tripId = tripId,
    )
}

private fun CounterEvent.toEntity() = CounterEventEntity(id, featureKey.name, zone, ts, tripId)

private fun CounterEventEntity.toCounter(): CounterEvent? =
    featureKeyOf(featureKey)?.let { CounterEvent(id, it, zone, ts, tripId) }
