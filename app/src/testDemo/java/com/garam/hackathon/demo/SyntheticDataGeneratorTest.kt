package com.garam.hackathon.demo

import com.garam.hackathon.aggregate.DailyAggregator
import com.garam.hackathon.aggregate.Period
import com.garam.hackathon.aggregate.PeriodKind
import com.garam.hackathon.aggregate.RecordingRateCalculator
import com.garam.hackathon.aggregate.ReportItem
import com.garam.hackathon.aggregate.UsageReportBuilder
import com.garam.hackathon.vehicle.FeatureKey
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticDataGeneratorTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val everything = emptySet<FeatureKey>()

    private fun endOf(date: LocalDate) = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun generate(from: LocalDate, to: LocalDate, supported: Set<FeatureKey> = everything, now: Long = endOf(to), endKm: Double? = null) =
        SyntheticDataGenerator().generate(from, to, zone, now, supported, endKm)

    /** Seconds at a positive level: for seat heat this leaves out ventilation (negative levels). */
    private fun seconds(output: SyntheticDataGenerator.Output, key: FeatureKey) =
        output.intervals.filter { it.featureKey == key && it.level > 0 }.sumOf { it.endTs!! - it.startTs } / 1000

    @Test
    fun `the same seed gives the same rows`() {
        val a = generate(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        val b = generate(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

        assertEquals(a, b)
    }

    @Test
    fun `a day produces the same rows whatever range it is generated in`() {
        val day = LocalDate.of(2026, 10, 15)
        val alone = generate(day, day).intervals
        val inMonth = generate(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).intervals
            .filter { it.id.startsWith("demo-${day.toEpochDay()}-") }

        assertEquals(alone.map { it.copy(tripId = null) }, inMonth.map { it.copy(tripId = null) })
    }

    @Test
    fun `winter brings seat and steering heat, summer brings air conditioning`() {
        val january = generate(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31))
        val july = generate(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))

        assertTrue(seconds(january, FeatureKey.SEAT_HEAT) > 5 * seconds(july, FeatureKey.SEAT_HEAT))
        assertTrue(seconds(january, FeatureKey.STEERING_WHEEL_HEAT) > 0)
        assertTrue(seconds(july, FeatureKey.HVAC_AC) > 2 * seconds(january, FeatureKey.HVAC_AC))
        assertTrue("ventilation shows up as negative seat levels in summer", july.intervals.any { it.featureKey == FeatureKey.SEAT_HEAT && it.level < 0 })
    }

    @Test
    fun `nothing ends after now`() {
        val today = LocalDate.of(2026, 11, 3)
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val output = generate(LocalDate.of(2026, 10, 1), today, now = now)

        assertTrue(output.intervals.all { it.endTs!! <= now + 20_000 && it.endTs!! > it.startTs })
        assertTrue(output.trips.all { it.endTs!! <= now })
    }

    @Test
    fun `only supported features are written`() {
        val supported = setOf(FeatureKey.DRIVING, FeatureKey.SEAT_HEAT, FeatureKey.DOOR_OPEN)

        val output = generate(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), supported)

        assertTrue(output.intervals.all { it.featureKey in supported })
        assertTrue(output.counters.all { it.featureKey in supported })
        assertTrue(output.intervals.none { it.featureKey == FeatureKey.DRIVING_MODE })
    }

    @Test
    fun `odometer readings end where asked and never go backwards`() {
        val output = generate(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), endKm = 670_403.0)
        val readings = output.snapshots.sortedBy { it.ts }.mapNotNull { it.metrics.odometerKm }

        assertTrue(readings.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(readings.last() <= 670_403.0 + 1e-6)
        assertTrue(output.trips.zipWithNext().all { (a, b) -> b.startTs >= a.endTs!! })
    }

    @Test
    fun `missed drives keep the recording rate below 100 percent`() {
        val from = LocalDate.of(2026, 10, 1)
        val to = LocalDate.of(2026, 10, 31)
        val output = generate(from, to)

        val rate = RecordingRateCalculator.calculate(
            output.intervals.filter { it.featureKey == FeatureKey.DRIVING },
            output.trips,
            output.snapshots.sortedBy { it.ts },
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            endOf(to),
        )

        assertTrue(output.missedTrips > 0)
        assertTrue(rate.ratio!! in 0.8..0.99)
    }

    @Test
    fun `november uses more seat heat than october, and the bill says so`() {
        val output = generate(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30))
        val now = endOf(LocalDate.of(2026, 11, 30))
        fun days(month: YearMonth) = generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { it <= month.atEndOfMonth() }
            .flatMap { DailyAggregator.aggregate(it, zone, output.intervals, output.counters, now) }.toList()

        val november = YearMonth.of(2026, 11)
        val report = UsageReportBuilder.build(
            Period.of(PeriodKind.MONTH, november.atDay(1)), november.atEndOfMonth(), days(november), days(november.minusMonths(1)), supported = null,
            recordingRate = RecordingRateCalculator.calculate(emptyList(), emptyList(), emptyList(), 0, 1),
        )

        val heat = report.usages.single { it.item == ReportItem.SEAT_HEAT }
        assertTrue(heat.changePercent!! > 0)
    }
}
