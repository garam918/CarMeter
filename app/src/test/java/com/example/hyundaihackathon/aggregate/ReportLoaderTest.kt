package com.example.hyundaihackathon.aggregate

import com.example.hyundaihackathon.data.Interval
import com.example.hyundaihackathon.fakes.FakeClock
import com.example.hyundaihackathon.fakes.InMemoryCapabilityRepository
import com.example.hyundaihackathon.fakes.InMemorySettingsRepository
import com.example.hyundaihackathon.fakes.InMemorySummaryRepository
import com.example.hyundaihackathon.fakes.InMemoryTripRepository
import com.example.hyundaihackathon.fakes.InMemoryUsageRepository
import com.example.hyundaihackathon.vehicle.CapabilityResult
import com.example.hyundaihackathon.vehicle.FeatureCategory
import com.example.hyundaihackathon.vehicle.FeatureKey
import com.example.hyundaihackathon.vehicle.ZoneRange
import java.time.LocalDate
import java.time.YearMonth
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

        val report = loader(usage).load(YearMonth.from(today))

        assertEquals(3 * 3600L, report.usages.single().onSec)
        // Finished days of both months are cached, today never is.
        assertTrue(summaries.savedDates.containsAll(listOf(today.minusDays(2), today.minusDays(1), LocalDate.of(2026, 10, 3))))
        assertTrue(today !in summaries.savedDates)

        // A second load reads the cache instead of counting yesterday twice.
        assertEquals(3 * 3600L, loader(usage).load(YearMonth.from(today)).usages.single().onSec)
    }

    @Test
    fun `a day is not cached while an interval that started in it is still open`() = runTest {
        val usage = InMemoryUsageRepository(
            listOf(Interval("open", FeatureKey.HVAC_POWER, 117, 1, startOf(today.minusDays(1)) + 20 * hour, null)),
        )

        val report = loader(usage).load(YearMonth.from(today))

        assertEquals(4 * 3600L + 12 * 3600L, report.usages.single().onSec)
        assertTrue(today.minusDays(1) !in summaries.savedDates)
        assertTrue(today.minusDays(2) in summaries.savedDates)
    }

    @Test
    fun `unused items come from the capability snapshot`() = runTest {
        val supported = mapOf(
            FeatureKey.HVAC_AC to CapabilityResult(true, listOf(ZoneRange(117))),
            FeatureKey.WIPER to CapabilityResult(true, listOf(ZoneRange(1))),
            FeatureKey.DRIVING_MODE to CapabilityResult(false, emptyList(), "ERROR_NOT_SUPPORTED_PROPERTY"),
        )
        val usage = InMemoryUsageRepository(listOf(Interval("a", FeatureKey.HVAC_AC, 117, 1, startOf(today), startOf(today) + hour)))

        val report = loader(usage, supported).load(YearMonth.from(today))

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

        val report = loader(usage, supported).load(YearMonth.from(today))

        assertEquals(listOf(ReportItem.DOOR_OPEN), report.unused)
    }
}

