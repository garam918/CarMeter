package com.garam.hackathon.aggregate

import com.garam.hackathon.data.repo.TripRepository
import com.garam.hackathon.data.repo.UsageRepository
import com.garam.hackathon.vehicle.FeatureKey

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
