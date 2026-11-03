package com.example.hyundaihackathon.collector

import com.example.hyundaihackathon.data.ClosedBy
import com.example.hyundaihackathon.data.Interval
import com.example.hyundaihackathon.fakes.InMemoryUsageRepository
import com.example.hyundaihackathon.vehicle.FeatureKey.HVAC_AC
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class EventBufferTest {
    private fun interval(id: String) = Interval(id, HVAC_AC, 1, 1, startTs = 0)

    @Test
    fun `rows wait in memory until flush`() = runTest {
        val usage = InMemoryUsageRepository()
        val buffer = EventBuffer(usage, maxPending = 10)

        buffer.add(listOf(interval("a"), interval("b")))
        assertEquals(0, usage.all.size)

        buffer.flush()
        assertEquals(2, usage.all.size)
        assertEquals(1, usage.saveCount)
    }

    @Test
    fun `reaching maxPending writes immediately`() = runTest {
        val usage = InMemoryUsageRepository()
        val buffer = EventBuffer(usage, maxPending = 3)

        buffer.add(listOf(interval("a"), interval("b"), interval("c")))

        assertEquals(3, usage.all.size)
        assertEquals(0, buffer.pendingCount)
    }

    @Test
    fun `an interval opened and closed between flushes is written once in its final state`() = runTest {
        val usage = InMemoryUsageRepository()
        val buffer = EventBuffer(usage, maxPending = 10)
        val open = interval("a")

        buffer.add(listOf(open))
        buffer.add(listOf(open.close(5_000, ClosedBy.EVENT)))
        buffer.flush()

        assertEquals(5_000L, usage.all.single().endTs)
        assertEquals(1, usage.saveCount)
    }

    @Test
    fun `a failed write keeps the rows for the next flush`() = runTest {
        val usage = InMemoryUsageRepository()
        val buffer = EventBuffer(usage, maxPending = 10)
        buffer.add(listOf(interval("a")))
        usage.failNextSave = true

        try {
            buffer.flush()
            fail("flush should rethrow the write error")
        } catch (expected: IllegalStateException) {
        }
        assertEquals(1, buffer.pendingCount)

        buffer.flush()
        assertEquals(1, usage.all.size)
    }
}
