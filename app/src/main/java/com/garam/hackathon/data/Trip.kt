package com.garam.hackathon.data

/**
 * Vehicle readings taken at a moment, each null when the vehicle does not report it.
 * Units follow the SDK: km, minutes, L/100 km, km/kWh and percent.
 */
data class VehicleMetrics(
    val odometerKm: Double? = null,
    val drivingTimeMin: Int? = null,
    val fuelEconomy: Double? = null,
    val evEfficiency: Double? = null,
    val socPercent: Int? = null,
) {
    val isEmpty: Boolean
        get() = odometerKm == null && drivingTimeMin == null && fuelEconomy == null &&
            evEfficiency == null && socPercent == null
}

/** A drive. [id] is the start time. Short stops in park do not end a trip (see TripTracker). */
data class Trip(
    val id: Long,
    val startTs: Long,
    val endTs: Long? = null,
    val start: VehicleMetrics = VehicleMetrics(),
    val end: VehicleMetrics = VehicleMetrics(),
) {
    val isOpen: Boolean get() = endTs == null
}

enum class SnapshotReason { COLLECTOR_START, COLLECTOR_STOP, TRIP_START, TRIP_END }

/** Readings the recording rate compares against what the app recorded. */
data class VehicleSnapshot(val ts: Long, val reason: SnapshotReason, val metrics: VehicleMetrics)
