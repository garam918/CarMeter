package com.garam.hackathon.data

import com.garam.hackathon.vehicle.FeatureKey

/** One stretch of time a feature stayed at one level in one zone. [endTs] is null while open. */
data class Interval(
    val id: String,
    val featureKey: FeatureKey,
    val zone: Int,
    val level: Int,
    val startTs: Long,
    val endTs: Long? = null,
    val closedBy: ClosedBy? = null,
    /** The trip the interval started in, if a trip was running. */
    val tripId: Long? = null,
) {
    val isOpen: Boolean get() = endTs == null

    /** Closes the interval, never before it started. */
    fun close(timestamp: Long, by: ClosedBy) = copy(endTs = maxOf(timestamp, startTs), closedBy = by)
}

enum class ClosedBy {
    /** The feature turned off or changed level. */
    EVENT,

    /** The process died; the interval was closed at the last heartbeat. */
    HEARTBEAT,

    /** The collector stopped normally. */
    STOP,
}
