package com.example.hyundaihackathon.data.repo

import com.example.hyundaihackathon.data.SnapshotReason
import com.example.hyundaihackathon.data.Trip
import com.example.hyundaihackathon.data.VehicleMetrics
import com.example.hyundaihackathon.data.VehicleSnapshot
import com.example.hyundaihackathon.data.db.dao.TripDao
import com.example.hyundaihackathon.data.db.entity.TripEntity
import com.example.hyundaihackathon.data.db.entity.VehicleSnapshotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TripRepository {
    suspend fun saveTrip(trip: Trip)

    suspend fun openTrips(): List<Trip>

    suspend fun lastTrip(): Trip?

    fun recentTrips(limit: Int): Flow<List<Trip>>

    suspend fun tripsStartedBetween(from: Long, to: Long): List<Trip>

    suspend fun saveSnapshot(snapshot: VehicleSnapshot)

    /** Snapshots in [from, to), preceded by the last snapshot before [from] if there is one. */
    suspend fun snapshotsWithBaseline(from: Long, to: Long): List<VehicleSnapshot>
}

class RoomTripRepository(private val dao: TripDao) : TripRepository {
    override suspend fun saveTrip(trip: Trip) = dao.upsertTrip(trip.toEntity())

    override suspend fun openTrips(): List<Trip> = dao.openTrips().map { it.toTrip() }

    override suspend fun lastTrip(): Trip? = dao.lastTrip()?.toTrip()

    override fun recentTrips(limit: Int): Flow<List<Trip>> = dao.recentTrips(limit).map { rows -> rows.map { it.toTrip() } }

    override suspend fun tripsStartedBetween(from: Long, to: Long): List<Trip> =
        dao.tripsStartedBetween(from, to).map { it.toTrip() }

    override suspend fun saveSnapshot(snapshot: VehicleSnapshot) = dao.insertSnapshot(
        VehicleSnapshotEntity(
            ts = snapshot.ts,
            reason = snapshot.reason.name,
            odometerKm = snapshot.metrics.odometerKm,
            drivingTimeMin = snapshot.metrics.drivingTimeMin,
            fuelEconomy = snapshot.metrics.fuelEconomy,
            evEfficiency = snapshot.metrics.evEfficiency,
            socPercent = snapshot.metrics.socPercent,
        ),
    )

    override suspend fun snapshotsWithBaseline(from: Long, to: Long): List<VehicleSnapshot> =
        dao.snapshotsWithBaseline(from, to).mapNotNull { row ->
            val reason = SnapshotReason.entries.firstOrNull { it.name == row.reason } ?: return@mapNotNull null
            VehicleSnapshot(
                ts = row.ts,
                reason = reason,
                metrics = VehicleMetrics(row.odometerKm, row.drivingTimeMin, row.fuelEconomy, row.evEfficiency, row.socPercent),
            )
        }
}

private fun Trip.toEntity() = TripEntity(
    tripId = id,
    startTs = startTs,
    endTs = endTs,
    odoKmStart = start.odometerKm,
    odoKmEnd = end.odometerKm,
    drivingMinStart = start.drivingTimeMin,
    drivingMinEnd = end.drivingTimeMin,
    fuelEconomyEnd = end.fuelEconomy,
    evEfficiencyEnd = end.evEfficiency,
    socStart = start.socPercent,
    socEnd = end.socPercent,
)

private fun TripEntity.toTrip() = Trip(
    id = tripId,
    startTs = startTs,
    endTs = endTs,
    start = VehicleMetrics(odometerKm = odoKmStart, drivingTimeMin = drivingMinStart, socPercent = socStart),
    end = VehicleMetrics(
        odometerKm = odoKmEnd,
        drivingTimeMin = drivingMinEnd,
        fuelEconomy = fuelEconomyEnd,
        evEfficiency = evEfficiencyEnd,
        socPercent = socEnd,
    ),
)
