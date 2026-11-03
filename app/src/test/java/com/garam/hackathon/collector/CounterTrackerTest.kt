package com.garam.hackathon.collector

import com.garam.hackathon.vehicle.FeatureKey.DOOR_OPEN
import com.garam.hackathon.vehicle.FeatureKey.TURN_SIGNAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CounterTrackerTest {
    private var nextId = 0
    private val tracker = CounterTracker(
        newId = { "c${nextId++}" },
        mergeOffMs = { key -> if (key == TURN_SIGNAL) 1_500L else 0L },
    )

    @Test
    fun `the first reading only sets the baseline`() {
        assertNull(tracker.onLevel(DOOR_OPEN, 1, 1, 0, live = true))
        assertNull(tracker.onLevel(DOOR_OPEN, 2, 0, 0, live = false))
    }

    @Test
    fun `each opening after a close counts once`() {
        tracker.onLevel(DOOR_OPEN, 1, 0, 0, live = false)

        val first = tracker.onLevel(DOOR_OPEN, 1, 1, 1_000, live = true)
        assertNull(tracker.onLevel(DOOR_OPEN, 1, 1, 1_500, live = true))
        tracker.onLevel(DOOR_OPEN, 1, 0, 2_000, live = true)
        val second = tracker.onLevel(DOOR_OPEN, 1, 1, 3_000, live = true)

        assertEquals(1_000L, first!!.ts)
        assertEquals(3_000L, second!!.ts)
    }

    @Test
    fun `readings that are not live never count`() {
        tracker.onLevel(DOOR_OPEN, 1, 0, 0, live = false)

        assertNull(tracker.onLevel(DOOR_OPEN, 1, 1, 1_000, live = false))
    }

    @Test
    fun `zones count separately`() {
        tracker.onLevel(DOOR_OPEN, 1, 0, 0, live = false)
        tracker.onLevel(DOOR_OPEN, 4, 0, 0, live = false)

        val driver = tracker.onLevel(DOOR_OPEN, 1, 1, 1_000, live = true)
        val passenger = tracker.onLevel(DOOR_OPEN, 4, 1, 1_000, live = true)

        assertEquals(1, driver!!.zone)
        assertEquals(4, passenger!!.zone)
    }

    @Test
    fun `a short off between blinks is the same use`() {
        tracker.onLevel(TURN_SIGNAL, 1, 0, 0, live = false)

        assertNotNull(tracker.onLevel(TURN_SIGNAL, 1, 1, 1_000, live = true))
        tracker.onLevel(TURN_SIGNAL, 1, 0, 1_400, live = true)
        assertNull("blink", tracker.onLevel(TURN_SIGNAL, 1, 1, 1_800, live = true))
        tracker.onLevel(TURN_SIGNAL, 1, 0, 2_000, live = true)
        assertNotNull("new use after a long off", tracker.onLevel(TURN_SIGNAL, 1, 1, 5_000, live = true))
    }

    @Test
    fun `an unknown reading does not end a use`() {
        tracker.onLevel(DOOR_OPEN, 1, 0, 0, live = false)
        tracker.onLevel(DOOR_OPEN, 1, 1, 1_000, live = true)

        tracker.onLevel(DOOR_OPEN, 1, null, 1_500, live = true)
        val afterUnknown = tracker.onLevel(DOOR_OPEN, 1, 1, 2_000, live = true)

        // null reads as off, so the door reporting open again counts: the vehicle lost track.
        assertNotNull(afterUnknown)
    }
}
