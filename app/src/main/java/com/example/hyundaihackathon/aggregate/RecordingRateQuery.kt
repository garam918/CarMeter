package com.example.hyundaihackathon.aggregate

import com.example.hyundaihackathon.data.repo.TripRepository
import com.example.hyundaihackathon.data.repo.UsageRepository
import com.example.hyundaihackathon.vehicle.FeatureKey

/** Loads what [RecordingRateCalculator] needs for one period. */
class RecordingRateQuery(private val usage: UsageRepository, private val trips: TripRepository) {
    suspend fun recordingRate(from: Long, to: Long): RecordingRate = RecordingRateCalculator.calculate(
        drivingIntervals = usage.intervalsOverlapping(FeatureKey.DRIVING, from, to),
        trips = trips.tripsStartedBetween(from, to),
        snapshots = trips.snapshotsWithBaseline(from, to),
        from = from,
        to = to,
    )
}
