package com.example.hyundaihackathon.collector

import com.example.hyundaihackathon.data.ClosedBy
import com.example.hyundaihackathon.data.Interval
import com.example.hyundaihackathon.data.Trip
import com.example.hyundaihackathon.fakes.InMemoryAppStateRepository
import com.example.hyundaihackathon.fakes.InMemoryTripRepository
import com.example.hyundaihackathon.fakes.InMemoryUsageRepository
import com.example.hyundaihackathon.vehicle.FeatureKey.SEAT_HEAT
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryManagerTest {
    private val openSeat = Interval("a", SEAT_HEAT, 1, 2, startTs = 1_000)

    @Test
    fun `stale intervals close at the last heartbeat`() = runTest {
        val usage = InMemoryUsageRepository(listOf(openSeat))
        val recovery = RecoveryManager(usage, InMemoryTripRepository(), InMemoryAppStateRepository(lastAlive = 61_000))

        assertEquals(1, recovery.closeStale())

        assertEquals(openSeat.copy(endTs = 61_000, closedBy = ClosedBy.HEARTBEAT), usage.all.single())
    }

    @Test
    fun `without a heartbeat they close where they started`() = runTest {
        val usage = InMemoryUsageRepository(listOf(openSeat))

        RecoveryManager(usage, InMemoryTripRepository(), InMemoryAppStateRepository(lastAlive = null)).closeStale()

        assertEquals(1_000L, usage.all.single().endTs)
    }

    @Test
    fun `a heartbeat older than the interval never makes a negative duration`() = runTest {
        val usage = InMemoryUsageRepository(listOf(openSeat))

        RecoveryManager(usage, InMemoryTripRepository(), InMemoryAppStateRepository(lastAlive = 500)).closeStale()

        assertEquals(1_000L, usage.all.single().endTs)
    }

    @Test
    fun `closed intervals are left alone`() = runTest {
        val closed = openSeat.copy(endTs = 2_000, closedBy = ClosedBy.EVENT)
        val usage = InMemoryUsageRepository(listOf(closed))

        assertEquals(0, RecoveryManager(usage, InMemoryTripRepository(), InMemoryAppStateRepository(61_000)).closeStale())

        assertEquals(closed, usage.all.single())
        assertTrue(usage.saveCount == 0)
    }

    @Test
    fun `a trip left open ends at the last heartbeat`() = runTest {
        val trips = InMemoryTripRepository(listOf(Trip(id = 1_000, startTs = 1_000)))

        RecoveryManager(InMemoryUsageRepository(), trips, InMemoryAppStateRepository(61_000)).closeStale()

        assertEquals(61_000L, trips.trips.single().endTs)
    }
}
