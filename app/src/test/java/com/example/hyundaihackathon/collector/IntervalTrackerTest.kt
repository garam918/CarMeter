package com.example.hyundaihackathon.collector

import com.example.hyundaihackathon.data.ClosedBy
import com.example.hyundaihackathon.data.Interval
import com.example.hyundaihackathon.vehicle.FeatureKey.HVAC_AC
import com.example.hyundaihackathon.vehicle.FeatureKey.SEAT_HEAT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IntervalTrackerTest {
    private lateinit var tracker: IntervalTracker
    private var nextId = 0

    @Before
    fun setUp() {
        nextId = 0
        tracker = IntervalTracker(newId = { "i${nextId++}" })
    }

    @Test
    fun `turning on opens an interval`() {
        val changed = tracker.onLevel(SEAT_HEAT, DRIVER, 2, 1_000)

        assertEquals(listOf(Interval("i0", SEAT_HEAT, DRIVER, 2, 1_000)), changed)
        assertEquals(1, tracker.openIntervals.size)
    }

    @Test
    fun `turning off closes the interval by event`() {
        tracker.onLevel(SEAT_HEAT, DRIVER, 2, 1_000)

        val changed = tracker.onLevel(SEAT_HEAT, DRIVER, 0, 5_000)

        assertEquals(listOf(Interval("i0", SEAT_HEAT, DRIVER, 2, 1_000, 5_000, ClosedBy.EVENT)), changed)
        assertTrue(tracker.openIntervals.isEmpty())
    }

    @Test
    fun `a null level closes the interval like off`() {
        tracker.onLevel(HVAC_AC, DRIVER, 1, 1_000)

        val changed = tracker.onLevel(HVAC_AC, DRIVER, null, 3_000)

        assertEquals(3_000L, changed.single().endTs)
        assertTrue(tracker.openIntervals.isEmpty())
    }

    @Test
    fun `changing level closes the old interval and opens a new one at the same time`() {
        tracker.onLevel(SEAT_HEAT, DRIVER, 1, 1_000)

        val changed = tracker.onLevel(SEAT_HEAT, DRIVER, 3, 4_000)

        assertEquals(
            listOf(
                Interval("i0", SEAT_HEAT, DRIVER, 1, 1_000, 4_000, ClosedBy.EVENT),
                Interval("i1", SEAT_HEAT, DRIVER, 3, 4_000),
            ),
            changed,
        )
    }

    @Test
    fun `repeating the current level changes nothing`() {
        tracker.onLevel(SEAT_HEAT, DRIVER, 2, 1_000)

        assertTrue(tracker.onLevel(SEAT_HEAT, DRIVER, 2, 2_000).isEmpty())
        assertEquals(1_000L, tracker.openIntervals.single().startTs)
    }

    @Test
    fun `off while nothing is open changes nothing`() {
        assertTrue(tracker.onLevel(SEAT_HEAT, DRIVER, 0, 1_000).isEmpty())
        assertTrue(tracker.onLevel(SEAT_HEAT, DRIVER, null, 2_000).isEmpty())
        assertTrue(tracker.openIntervals.isEmpty())
    }

    @Test
    fun `zones and features are tracked separately`() {
        tracker.onLevel(SEAT_HEAT, DRIVER, 2, 1_000)
        tracker.onLevel(SEAT_HEAT, PASSENGER, 1, 1_500)
        tracker.onLevel(HVAC_AC, DRIVER, 1, 2_000)

        val changed = tracker.onLevel(SEAT_HEAT, DRIVER, 0, 3_000)

        assertEquals("i0", changed.single().id)
        assertEquals(setOf("i1", "i2"), tracker.openIntervals.map { it.id }.toSet())
    }

    @Test
    fun `negative levels count as on`() {
        val changed = tracker.onLevel(SEAT_HEAT, DRIVER, -2, 1_000)

        assertEquals(-2, changed.single().level)
    }

    @Test
    fun `closeAll closes every open interval with the given reason`() {
        tracker.onLevel(SEAT_HEAT, DRIVER, 2, 1_000)
        tracker.onLevel(HVAC_AC, DRIVER, 1, 2_000)

        val closed = tracker.closeAll(60_000, ClosedBy.HEARTBEAT)

        assertEquals(2, closed.size)
        assertTrue(closed.all { it.endTs == 60_000L && it.closedBy == ClosedBy.HEARTBEAT })
        assertTrue(tracker.openIntervals.isEmpty())
    }

    @Test
    fun `a reading older than the open interval never makes a negative duration`() {
        tracker.onLevel(SEAT_HEAT, DRIVER, 2, 5_000)

        val changed = tracker.onLevel(SEAT_HEAT, DRIVER, 3, 4_000)

        assertEquals(5_000L, changed[0].endTs)
        assertEquals(5_000L, changed[1].startTs)
    }

    @Test
    fun `a new tracker after a restart starts empty and opens from the next reading`() {
        tracker.onLevel(SEAT_HEAT, DRIVER, 2, 1_000)

        val restarted = IntervalTracker(newId = { "r${nextId++}" })
        val changed = restarted.onLevel(SEAT_HEAT, DRIVER, 2, 90_000)

        assertEquals(listOf(Interval("r1", SEAT_HEAT, DRIVER, 2, 90_000)), changed)
    }

    private companion object {
        const val DRIVER = 1
        const val PASSENGER = 4
    }
}
