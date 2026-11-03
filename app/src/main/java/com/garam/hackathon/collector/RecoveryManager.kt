package com.garam.hackathon.collector

import com.garam.hackathon.data.ClosedBy
import com.garam.hackathon.data.repo.AppStateRepository
import com.garam.hackathon.data.repo.TripRepository
import com.garam.hackathon.data.repo.UsageRepository

/**
 * Closes intervals and trips a previous process left open because it died without a
 * stop signal. They end at the last heartbeat. Without a heartbeat they end where they
 * started, so no usage is ever guessed.
 */
class RecoveryManager(
    private val usage: UsageRepository,
    private val trips: TripRepository,
    private val appState: AppStateRepository,
) {
    /** Returns how many intervals were closed. */
    suspend fun closeStale(): Int {
        val lastAlive = appState.lastAlive()
        val staleIntervals = usage.openIntervals()
        if (staleIntervals.isNotEmpty()) {
            usage.save(staleIntervals.map { it.close(lastAlive ?: it.startTs, ClosedBy.HEARTBEAT) })
        }
        for (trip in trips.openTrips()) {
            trips.saveTrip(trip.copy(endTs = maxOf(lastAlive ?: trip.startTs, trip.startTs)))
        }
        return staleIntervals.size
    }
}
