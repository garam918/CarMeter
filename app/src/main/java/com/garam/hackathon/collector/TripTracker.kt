package com.garam.hackathon.collector

import com.garam.hackathon.data.Trip

/**
 * Groups driving into trips without a timer. Driving that resumes within [mergeGapMs]
 * of the last stop continues the same trip, so shifting into park for a moment does
 * not split a drive. Not thread-safe: call it from one coroutine at a time.
 */
class TripTracker(private val mergeGapMs: Long = MERGE_GAP_MS) {
    sealed interface Change {
        val trip: Trip

        data class Started(override val trip: Trip) : Change

        data class Resumed(override val trip: Trip) : Change

        data class Ended(override val trip: Trip) : Change
    }

    /** The running trip, if driving is in progress. */
    var current: Trip? = null
        private set

    private var lastEnded: Trip? = null

    /** Lets a trip that ended just before a process restart continue. */
    fun restore(lastTrip: Trip?) {
        if (current == null) lastEnded = lastTrip?.takeUnless { it.isOpen }
    }

    fun onDrivingStarted(timestamp: Long): Change? {
        if (current != null) return null
        val previous = lastEnded
        val previousEnd = previous?.endTs
        return if (previous != null && previousEnd != null && timestamp - previousEnd in 0 until mergeGapMs) {
            val resumed = previous.copy(endTs = null)
            current = resumed
            lastEnded = null
            Change.Resumed(resumed)
        } else {
            val started = Trip(id = timestamp, startTs = timestamp)
            current = started
            lastEnded = null
            Change.Started(started)
        }
    }

    fun onDrivingStopped(timestamp: Long): Change? {
        val running = current ?: return null
        val ended = running.copy(endTs = maxOf(timestamp, running.startTs))
        current = null
        lastEnded = ended
        return Change.Ended(ended)
    }

    /** Records readings taken for [trip] so a later change keeps them. */
    fun update(trip: Trip) {
        if (current?.id == trip.id) current = trip
        if (lastEnded?.id == trip.id) lastEnded = trip
    }

    companion object {
        const val MERGE_GAP_MS = 5 * 60_000L
    }
}
