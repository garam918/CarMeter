package com.garam.hackathon.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.garam.hackathon.data.db.entity.TripEntity
import com.garam.hackathon.data.db.entity.VehicleSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Upsert
    suspend fun upsertTrip(trip: TripEntity)

    @Query("SELECT * FROM trip WHERE endTs IS NULL")
    suspend fun openTrips(): List<TripEntity>

    @Query("SELECT * FROM trip ORDER BY startTs DESC LIMIT 1")
    suspend fun lastTrip(): TripEntity?

    @Query("SELECT * FROM trip ORDER BY startTs DESC LIMIT :limit")
    fun recentTrips(limit: Int): Flow<List<TripEntity>>

    @Query("SELECT * FROM trip WHERE startTs >= :from AND startTs < :to ORDER BY startTs")
    suspend fun tripsStartedBetween(from: Long, to: Long): List<TripEntity>

    @Insert
    suspend fun insertSnapshot(snapshot: VehicleSnapshotEntity)

    /** Snapshots in [from, to), plus the last one before [from] as the baseline. */
    @Query(
        "SELECT * FROM vehicle_snapshot WHERE ts >= (" +
            "SELECT COALESCE(MAX(ts), :from) FROM vehicle_snapshot WHERE ts < :from" +
            ") AND ts < :to ORDER BY ts, id",
    )
    suspend fun snapshotsWithBaseline(from: Long, to: Long): List<VehicleSnapshotEntity>
}
