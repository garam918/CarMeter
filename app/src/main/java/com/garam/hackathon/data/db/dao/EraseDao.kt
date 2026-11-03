package com.garam.hackathon.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Deletes everything recorded about how the car was used. Keeps the capability snapshot
 * (facts about the vehicle, not its driver) and the settings, including consent.
 */
@Dao
interface EraseDao {
    @Query("DELETE FROM feature_interval")
    suspend fun deleteIntervals()

    @Query("DELETE FROM counter_event")
    suspend fun deleteCounters()

    @Query("DELETE FROM trip")
    suspend fun deleteTrips()

    @Query("DELETE FROM vehicle_snapshot")
    suspend fun deleteSnapshots()

    @Query("DELETE FROM daily_summary")
    suspend fun deleteSummaries()

    @Query("DELETE FROM app_state WHERE `key` = 'lastAlive'")
    suspend fun deleteLastAlive()

    @Transaction
    suspend fun eraseUsageData() {
        deleteIntervals()
        deleteCounters()
        deleteTrips()
        deleteSnapshots()
        deleteSummaries()
        deleteLastAlive()
    }

    @Query("DELETE FROM feature_interval WHERE endTs IS NOT NULL AND endTs < :cutoffTs")
    suspend fun deleteIntervalsBefore(cutoffTs: Long)

    @Query("DELETE FROM counter_event WHERE ts < :cutoffTs")
    suspend fun deleteCountersBefore(cutoffTs: Long)

    @Query("DELETE FROM trip WHERE endTs IS NOT NULL AND endTs < :cutoffTs")
    suspend fun deleteTripsBefore(cutoffTs: Long)

    /** Keeps the last snapshot before the cutoff: the recording rate's baseline for the first kept day. */
    @Query("DELETE FROM vehicle_snapshot WHERE ts < (SELECT MAX(ts) FROM vehicle_snapshot WHERE ts < :cutoffTs)")
    suspend fun deleteSnapshotsBefore(cutoffTs: Long)

    /** [cutoffDate] is an ISO date; ISO strings sort like dates. */
    @Query("DELETE FROM daily_summary WHERE date < :cutoffDate")
    suspend fun deleteSummariesBefore(cutoffDate: String)

    /** Deletes what ended before the cutoff. Open intervals and trips stay whatever their start. */
    @Transaction
    suspend fun deleteUsageBefore(cutoffTs: Long, cutoffDate: String) {
        deleteIntervalsBefore(cutoffTs)
        deleteCountersBefore(cutoffTs)
        deleteTripsBefore(cutoffTs)
        deleteSnapshotsBefore(cutoffTs)
        deleteSummariesBefore(cutoffDate)
    }
}
