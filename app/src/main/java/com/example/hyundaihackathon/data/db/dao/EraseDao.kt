package com.example.hyundaihackathon.data.db.dao

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
}
