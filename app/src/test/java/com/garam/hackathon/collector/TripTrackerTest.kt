package com.garam.hackathon.collector

import com.garam.hackathon.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTrackerTest {
    private val tracker = TripTracker(mergeGapMs = 300_000)

    @Test
    fun `driving starts a trip and stopping ends it`() {
        val started = tracker.onDrivingStarted(10_000) as TripTracker.Change.Started
        val ended = tracker.onDrivingStopped(70_000) as TripTracker.Change.Ended

        assertEquals(10_000L, started.trip.id)
        assertEquals(70_000L, ended.trip.endTs)
        assertNull(tracker.current)
    }

    @Test
    fun `driving again within the gap continues the same trip`() {
        tracker.onDrivingStarted(10_000)
        tracker.onDrivingStopped(70_000)

        val resumed = tracker.onDrivingStarted(70_000 + 299_999)

        assertTrue(resumed is TripTracker.Change.Resumed)
        assertEquals(10_000L, resumed!!.trip.id)
        assertNull(resumed.trip.endTs)
    }

    @Test
    fun `driving again after the gap starts a new trip`() {
        tracker.onDrivingStarted(10_000)
        tracker.onDrivingStopped(70_000)

        val next = tracker.onDrivingStarted(70_000 + 300_000)

        assertTrue(next is TripTracker.Change.Started)
        assertEquals(370_000L, next!!.trip.id)
    }

    @Test
    fun `repeated start or stop signals change nothing`() {
        tracker.onDrivingStarted(10_000)
        assertNull(tracker.onDrivingStarted(20_000))

        tracker.onDrivingStopped(30_000)
        assertNull(tracker.onDrivingStopped(40_000))
    }

    @Test
    fun `a trip that ended before a restart can continue`() {
        tracker.restore(Trip(id = 1_000, startTs = 1_000, endTs = 61_000))

        val resumed = tracker.onDrivingStarted(120_000)

        assertTrue(resumed is TripTracker.Change.Resumed)
        assertEquals(1_000L, resumed!!.trip.id)
    }

    @Test
    fun `readings saved with update survive a resume`() {
        val started = tracker.onDrivingStarted(10_000)!!.trip
        tracker.update(started.copy(start = started.start.copy(odometerKm = 42.0)))
        tracker.onDrivingStopped(20_000)

        val resumed = tracker.onDrivingStarted(30_000)!!.trip

        assertEquals(42.0, resumed.start.odometerKm!!, 0.0)
    }
}
