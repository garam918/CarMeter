package com.garam.hackathon.aggregate

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageReportBuilderTest {
    private val november = YearMonth.of(2026, 11)
    private val thisMonth = Period.of(PeriodKind.MONTH, november.atDay(1))
    private val rate = RecordingRate(RecordingRate.Basis.DISTANCE, recorded = 95.0, reference = 100.0)

    private fun day(month: YearMonth, day: Int, item: ReportItem, onSec: Long = 0, count: Int = 0, avg: Double? = null) =
        DailySummary(month.atDay(day), item, onSec, count, avg)

    private fun month(
        period: Period = thisMonth,
        today: LocalDate = LocalDate.of(2026, 11, 3),
        current: List<DailySummary> = emptyList(),
        previous: List<DailySummary> = emptyList(),
        supported: Set<ReportItem>? = null,
    ) = UsageReportBuilder.build(period, today, current, previous, supported, rate)

    @Test
    fun `days add up and the change is against last month`() {
        val report = month(
            today = LocalDate.of(2026, 11, 10),
            current = listOf(
                day(november, 1, ReportItem.SEAT_HEAT, onSec = 8 * 3600, avg = 2.0),
                day(november, 2, ReportItem.SEAT_HEAT, onSec = 8 * 3600, avg = 1.0),
            ),
            previous = listOf(day(november.minusMonths(1), 5, ReportItem.SEAT_HEAT, onSec = 10 * 3600)),
        )

        val heat = report.top.single()
        assertEquals(16 * 3600L, heat.onSec)
        assertEquals(60, heat.changePercent)
        assertEquals(1.5, heat.avgLevel!!, 0.001)
        assertEquals(LocalDate.of(2026, 10, 1)..LocalDate.of(2026, 10, 10), report.compared)
    }

    @Test
    fun `a feature unused last month is new rather than an infinite change`() {
        val ac = month(current = listOf(day(november, 1, ReportItem.AIR_CONDITIONING, onSec = 600))).usages.single()

        assertTrue(ac.isNew)
        assertNull(ac.changePercent)
    }

    @Test
    fun `the total adds time items and compares with the previous period`() {
        val report = month(
            current = listOf(
                day(november, 1, ReportItem.SEAT_HEAT, onSec = 3600),
                day(november, 1, ReportItem.FAN, onSec = 1800),
                day(november, 1, ReportItem.DRIVING, onSec = 7200, count = 2),
                day(november, 1, ReportItem.DOOR_OPEN, count = 4),
            ),
            previous = listOf(day(november.minusMonths(1), 1, ReportItem.SEAT_HEAT, onSec = 3600)),
        )

        assertEquals(5400L, report.totalSec)
        assertEquals(3600L, report.previousTotalSec)
        assertEquals(50, report.totalChangePercent)
        assertEquals(2, report.drivingCount)
        assertEquals("지난달 같은 기간보다 +50%", ReportFormat.totalChange(report))
    }

    @Test
    fun `only supported usage items with no use are listed as unused`() {
        val report = month(
            current = listOf(day(november, 1, ReportItem.SEAT_HEAT, onSec = 600)),
            previous = listOf(day(november.minusMonths(1), 1, ReportItem.SEAT_VENTILATION, onSec = 600)),
            supported = setOf(ReportItem.SEAT_HEAT, ReportItem.SEAT_VENTILATION, ReportItem.DRIVING),
        )

        assertEquals(listOf(ReportItem.SEAT_VENTILATION), report.unused)
    }

    @Test
    fun `a month the app recorded nothing in lists nothing as unused`() {
        val report = month(period = thisMonth.previous(), supported = setOf(ReportItem.SEAT_HEAT, ReportItem.WIPER))

        assertTrue(report.unused.isEmpty())
        assertEquals("10월", report.label)
        assertEquals("10월에는 기록이 없어요.", report.tip)
    }

    @Test
    fun `without a capability snapshot nothing is claimed unused`() {
        val report = month()

        assertTrue(report.unused.isEmpty())
        assertFalse(report.hasData)
    }

    @Test
    fun `the top list ranks time items only, at most five, without driving or temperature`() {
        val items = listOf(
            ReportItem.SEAT_HEAT, ReportItem.AIR_CONDITIONING, ReportItem.FAN, ReportItem.LOW_BEAM,
            ReportItem.WIPER, ReportItem.CRUISE,
        )
        val current = items.mapIndexed { i, item -> day(november, 1, item, onSec = (i + 1) * 100L) } + listOf(
            day(november, 1, ReportItem.DRIVING, onSec = 99_999),
            day(november, 1, ReportItem.TARGET_TEMPERATURE, onSec = 99_999, avg = 225.0),
            day(november, 1, ReportItem.DOOR_OPEN, count = 50),
        )

        val report = month(current = current)

        assertEquals(listOf(ReportItem.CRUISE, ReportItem.WIPER, ReportItem.LOW_BEAM, ReportItem.FAN, ReportItem.AIR_CONDITIONING), report.top.map { it.item })
        assertEquals(99_999L, report.drivingSec)
        assertEquals(22.5, report.avgTargetCelsius!!, 0.001)
        assertTrue(report.usages.none { it.item == ReportItem.DRIVING || it.item == ReportItem.TARGET_TEMPERATURE })
    }

    @Test
    fun `rows for an unsupported feature never reach the report or its chart`() {
        val report = month(
            current = listOf(day(november, 1, ReportItem.ECO_MODE, onSec = 3600), day(november, 1, ReportItem.SEAT_HEAT, onSec = 600)),
            supported = setOf(ReportItem.SEAT_HEAT),
        )

        assertEquals(listOf(ReportItem.SEAT_HEAT), report.usages.map { it.item })
        assertEquals(600L, report.totalSec)
        assertEquals(600L, report.buckets.first().seconds)
    }

    @Test
    fun `a month has one bar per day, today marked and the rest in the future`() {
        val report = month(current = listOf(day(november, 2, ReportItem.SEAT_HEAT, onSec = 600), day(november, 2, ReportItem.FAN, onSec = 300)))

        assertEquals(30, report.buckets.size)
        assertEquals(900L, report.buckets[1].seconds)
        assertTrue(report.buckets[2].isNow)
        assertTrue(report.buckets.drop(3).all { it.isFuture })
        assertFalse(report.buckets[1].isFuture)
    }

    @Test
    fun `a week has a bar per weekday, no unused list and no tip`() {
        val week = Period.of(PeriodKind.WEEK, LocalDate.of(2026, 11, 4))
        val report = UsageReportBuilder.build(
            week, LocalDate.of(2026, 11, 4),
            current = listOf(day(november, 3, ReportItem.SEAT_HEAT, onSec = 1200)),
            previous = emptyList(),
            supported = setOf(ReportItem.SEAT_HEAT, ReportItem.WIPER),
            recordingRate = rate,
        )

        assertEquals(listOf("월", "화", "수", "목", "금", "토", "일"), report.buckets.map { it.label })
        assertEquals(1200L, report.buckets[1].seconds)
        assertTrue(report.unused.isEmpty())
        assertEquals("", report.tip)
        assertEquals("이번 주", report.label)
        assertEquals("지난주 같은 기간에는 기록이 없어요", ReportFormat.totalChange(report))
    }

    @Test
    fun `a day has a bar per hour, with hours not reached yet empty`() {
        val today = LocalDate.of(2026, 11, 3)
        val hourly = List(24) { hour ->
            when {
                hour > 9 -> null
                hour == 8 -> listOf(DailySummary(today, ReportItem.SEAT_HEAT, 1800, 1), DailySummary(today, ReportItem.DRIVING, 1800, 1))
                else -> emptyList()
            }
        }
        val report = UsageReportBuilder.build(
            Period.of(PeriodKind.DAY, today), today,
            current = listOf(DailySummary(today, ReportItem.SEAT_HEAT, 1800, 1), DailySummary(today, ReportItem.DRIVING, 1800, 1)),
            previous = emptyList(),
            supported = null,
            recordingRate = rate,
            hourly = hourly,
        )

        assertEquals(24, report.buckets.size)
        // Driving is context, not a feature used, so it is not in the bars.
        assertEquals(1800L, report.buckets[8].seconds)
        assertTrue(report.buckets[9].isNow)
        assertTrue(report.buckets[10].isFuture)
        assertEquals("오늘", report.label)
    }
}
