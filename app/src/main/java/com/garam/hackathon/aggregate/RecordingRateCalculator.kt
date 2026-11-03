package com.garam.hackathon.aggregate

import com.garam.hackathon.data.Interval
import com.garam.hackathon.data.Trip
import com.garam.hackathon.data.VehicleSnapshot

/**
 * How much of the period's driving the app recorded, compared with the vehicle's own
 * counters. Gaps are never filled in: driving while the app was not running shows up
 * only in [reference], which lowers [ratio].
 */
data class RecordingRate(
    val basis: Basis,
    /** Driving the app recorded, in [Basis.unit]. */
    val recorded: Double,
    /** Driving the vehicle counted, in [Basis.unit]; null when it cannot be measured. */
    val reference: Double?,
) {
    enum class Basis(val unit: String) { DRIVING_TIME("분"), DISTANCE("km"), NONE("") }

    /** recorded ÷ reference, capped at 1. Null when there is no reference driving. */
    val ratio: Double?
        get() = reference?.takeIf { it > 0.0 }?.let { (recorded / it).coerceAtMost(1.0) }
}

object RecordingRateCalculator {
    /**
     * Prefers the vehicle's driving-time counter and falls back to the odometer.
     * [snapshots] must be sorted by time and may start with one baseline taken before [from].
     */
    fun calculate(
        drivingIntervals: List<Interval>,
        trips: List<Trip>,
        snapshots: List<VehicleSnapshot>,
        from: Long,
        to: Long,
    ): RecordingRate {
        val minutes = counterDelta(snapshots.mapNotNull { it.metrics.drivingTimeMin?.toDouble() })
        if (minutes != null) {
            return RecordingRate(RecordingRate.Basis.DRIVING_TIME, recordedMinutes(drivingIntervals, from, to), minutes)
        }
        val km = counterDelta(snapshots.mapNotNull { it.metrics.odometerKm })
        if (km != null) {
            return RecordingRate(RecordingRate.Basis.DISTANCE, recordedKm(trips, from, to), km)
        }
        return RecordingRate(RecordingRate.Basis.NONE, 0.0, null)
    }

    /** Minutes of driving intervals inside [from, to). Open intervals run until [to]. */
    fun recordedMinutes(drivingIntervals: List<Interval>, from: Long, to: Long): Double =
        drivingIntervals.sumOf { interval ->
            val start = maxOf(interval.startTs, from)
            val end = minOf(interval.endTs ?: to, to)
            (end - start).coerceAtLeast(0L)
        } / 60_000.0

    /** Odometer distance of trips that started in [from, to) and have both readings. */
    fun recordedKm(trips: List<Trip>, from: Long, to: Long): Double = trips
        .filter { it.startTs in from until to }
        .sumOf { trip ->
            val start = trip.start.odometerKm
            val end = trip.end.odometerKm
            if (start != null && end != null) (end - start).coerceAtLeast(0.0) else 0.0
        }

    /**
     * Growth of a cumulative counter across consecutive readings. A drop means the counter
     * was reset, and the reading after it counts from zero. Null with fewer than two readings.
     */
    fun counterDelta(readings: List<Double>): Double? {
        if (readings.size < 2) return null
        return readings.zipWithNext { previous, next -> if (next >= previous) next - previous else next }.sum()
    }
}
