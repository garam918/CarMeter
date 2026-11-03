package com.garam.hackathon.aggregate

import com.garam.hackathon.data.Interval
import com.garam.hackathon.fakes.FakeClock
import com.garam.hackathon.fakes.InMemoryCapabilityRepository
import com.garam.hackathon.fakes.InMemorySettingsRepository
import com.garam.hackathon.fakes.InMemorySummaryRepository
import com.garam.hackathon.fakes.InMemoryTripRepository
import com.garam.hackathon.fakes.InMemoryUsageRepository
import com.garam.hackathon.vehicle.CapabilityResult
import com.garam.hackathon.vehicle.FeatureCategory
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.ZoneRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportLoaderTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 3)
    private val hour = 3_600_000L
    private fun startOf(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private val clock = FakeClock(startOf(today) + 12 * hour)
    private val summaries = InMemorySummaryRepository()

    private val settings = InMemorySettingsRepository()

    private fun loader(usage: InMemoryUsageRepository, capabilities: Map<FeatureKey, CapabilityResult> = emptyMap()) = ReportLoader(
        usage = usage,
        summaries = summaries,
        capabilities = InMemoryCapabilityRepository(capabilities),
        settings = settings,
        recordingRate = RecordingRateQuery(usage, InMemoryTripRepository()),
        clock = clock,
        timeZone = { zone },
    )

    @Test
    fun `finished days are cached and today stays live`() = runTest {
        val usage = InMemoryUsageRepository(
            listOf(
                Interval("a", FeatureKey.HVAC_AC, 1, 1, startOf(today.minusDays(1)) + hour, startOf(today.minusDays(1)) + 3 * hour),
                Interval("b", FeatureKey.HVAC_AC, 1, 1, startOf(today) + hour, startOf(today) + 2 * hour),
            ),
        )

        val report = loader(usage).load(Period.of(PeriodKind.MONTH, today))

        assertEquals(3 * 3600L, report.usages.single().onSec)
        // Finished days of both months are cached, today never is.
        assertTrue(summaries.savedDates.containsAll(listOf(today.minusDays(2), today.minusDays(1), LocalDate.of(2026, 10, 3))))
        assertTrue(today !in summaries.savedDates)

        // A second load reads the cache instead of counting yesterday twice.
        assertEquals(3 * 3600L, loader(usage).load(Period.of(PeriodKind.MONTH, today)).usages.single().onSec)
    }

    @Test
    fun `a day is not cached while an interval that started in it is still open`() = runTest {
        val usage = InMemoryUsageRepository(
            listOf(Interval("open", FeatureKey.HVAC_POWER, 117, 1, startOf(today.minusDays(1)) + 20 * hour, null)),
        )

        val report = loader(usage).load(Period.of(PeriodKind.MONTH, today))

        assertEquals(4 * 3600L + 12 * 3600L, report.usages.single().onSec)
        assertTrue(today.minusDays(1) !in summaries.savedDates)
        assertTrue(today.minusDays(2) in summaries.savedDates)
    }

    @Test
    fun `today is compared with the same weekday last week up to the same time`() = runTest {
        val lastWeek = today.minusWeeks(1)
        val usage = InMemoryUsageRepository(
            listOf(
                Interval("morning", FeatureKey.HVAC_AC, 117, 1, startOf(lastWeek) + 8 * hour, startOf(lastWeek) + 9 * hour),
                // After the time of day it is now, so not compared yet.
                Interval("evening", FeatureKey.HVAC_AC, 117, 1, startOf(lastWeek) + 18 * hour, startOf(lastWeek) + 20 * hour),
                Interval("today", FeatureKey.HVAC_AC, 117, 1, startOf(today) + 8 * hour, startOf(today) + 10 * hour),
            ),
        )

        val report = loader(usage).load(Period.of(PeriodKind.DAY, today))

        val ac = report.usages.single()
        assertEquals(2 * 3600L, ac.onSec)
        assertEquals(3600L, ac.previousOnSec)
        assertEquals(100, report.totalChangePercent)
        assertEquals(3600L, report.buckets[8].seconds)
        assertEquals(3600L, report.buckets[9].seconds)
        assertTrue(report.buckets[11].isNow)
        assertTrue(report.buckets[12].isFuture)
        assertEquals(report.totalSec, report.buckets.sumOf { it.seconds })
    }

    @Test
    fun `a past day is compared with all of the same weekday a week before`() = runTest {
        val day = today.minusDays(1)
        val usage = InMemoryUsageRepository(
            listOf(
                Interval("before", FeatureKey.HVAC_AC, 117, 1, startOf(day.minusWeeks(1)) + 18 * hour, startOf(day.minusWeeks(1)) + 20 * hour),
                Interval("day", FeatureKey.HVAC_AC, 117, 1, startOf(day) + 8 * hour, startOf(day) + 9 * hour),
            ),
        )

        val report = loader(usage).load(Period.of(PeriodKind.DAY, day))

        assertEquals(-50, report.usages.single().changePercent)
        assertTrue(report.buckets.none { it.isFuture || it.isNow })
    }

    @Test
    fun `unused items come from the capability snapshot`() = runTest {
        val supported = mapOf(
            FeatureKey.HVAC_AC to CapabilityResult(true, listOf(ZoneRange(117))),
            FeatureKey.WIPER to CapabilityResult(true, listOf(ZoneRange(1))),
            FeatureKey.DRIVING_MODE to CapabilityResult(false, emptyList(), "ERROR_NOT_SUPPORTED_PROPERTY"),
        )
        val usage = InMemoryUsageRepository(listOf(Interval("a", FeatureKey.HVAC_AC, 117, 1, startOf(today), startOf(today) + hour)))

        val report = loader(usage, supported).load(Period.of(PeriodKind.MONTH, today))

        assertEquals(listOf(ReportItem.WIPER), report.unused)
    }

    @Test
    fun `a switched-off category is not listed as unused`() = runTest {
        val supported = mapOf(
            FeatureKey.HVAC_AC to CapabilityResult(true, listOf(ZoneRange(117))),
            FeatureKey.WIPER to CapabilityResult(true, listOf(ZoneRange(1))),
            FeatureKey.DOOR_OPEN to CapabilityResult(true, listOf(ZoneRange(1))),
        )
        settings.setCategoryEnabled(FeatureCategory.DRIVING, false)
        val usage = InMemoryUsageRepository(listOf(Interval("a", FeatureKey.HVAC_AC, 117, 1, startOf(today), startOf(today) + hour)))

        val report = loader(usage, supported).load(Period.of(PeriodKind.MONTH, today))

        assertEquals(listOf(ReportItem.DOOR_OPEN), report.unused)
    }
}

