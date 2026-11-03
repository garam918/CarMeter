package com.garam.hackathon.data

import com.garam.hackathon.data.repo.RetentionRepository
import com.garam.hackathon.fakes.FakeClock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRetentionTest {
    private val zone = ZoneId.of("Asia/Seoul")

    private class FakeRetention : RetentionRepository {
        var purgedOn: LocalDate? = null
        val deletions = mutableListOf<Pair<Long, LocalDate>>()

        override suspend fun lastPurgedOn() = purgedOn

        override suspend fun setLastPurgedOn(date: LocalDate) {
            purgedOn = date
        }

        override suspend fun deleteBefore(cutoffTs: Long, cutoffDate: LocalDate) {
            deletions += cutoffTs to cutoffDate
        }
    }

    @Test
    fun `thirteen months are kept, this one included`() {
        assertEquals(LocalDate.of(2025, 11, 1), DataRetention.cutoff(LocalDate.of(2026, 11, 3)))
        assertEquals(LocalDate.of(2025, 2, 1), DataRetention.cutoff(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `it deletes before the cutoff at most once a day`() = runTest {
        val repository = FakeRetention()
        val clock = FakeClock(LocalDate.of(2026, 11, 3).atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        val retention = DataRetention(repository, clock) { zone }

        assertTrue(retention.purgeIfDue())
        assertFalse(retention.purgeIfDue())

        val cutoff = LocalDate.of(2025, 11, 1)
        assertEquals(listOf(cutoff.atStartOfDay(zone).toInstant().toEpochMilli() to cutoff), repository.deletions)

        clock.time = LocalDate.of(2026, 11, 4).atTime(0, 5).atZone(zone).toInstant().toEpochMilli()
        assertTrue(retention.purgeIfDue())
        assertEquals(2, repository.deletions.size)
    }
}
