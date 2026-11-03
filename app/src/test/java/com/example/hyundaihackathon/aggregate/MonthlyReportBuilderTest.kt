package com.example.hyundaihackathon.aggregate

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyReportBuilderTest {
    private val november = YearMonth.of(2026, 11)
    private val rate = RecordingRate(RecordingRate.Basis.DISTANCE, recorded = 95.0, reference = 100.0)

    private fun day(month: YearMonth, day: Int, item: ReportItem, onSec: Long = 0, count: Int = 0, avg: Double? = null) =
        DailySummary(month.atDay(day), item, onSec, count, avg)

    @Test
    fun `the current month is compared with the same days of last month`() {
        val today = LocalDate.of(2026, 11, 10)

        assertEquals(november.atDay(1)..today, MonthlyReportBuilder.period(november, today))
        assertEquals(LocalDate.of(2026, 10, 1)..LocalDate.of(2026, 10, 10), MonthlyReportBuilder.comparedPeriod(november, today))
    }

    @Test
    fun `a past month covers all of it, and a short previous month is cut at its end`() {
        val march = YearMonth.of(2026, 3)
        val today = LocalDate.of(2026, 11, 3)

        assertEquals(march.atDay(1)..march.atDay(31), MonthlyReportBuilder.period(march, today))
        assertEquals(LocalDate.of(2026, 2, 1)..LocalDate.of(2026, 2, 28), MonthlyReportBuilder.comparedPeriod(march, today))
    }

    @Test
    fun `days add up and the change is against last month`() {
        val report = MonthlyReportBuilder.build(
            month = november,
            today = LocalDate.of(2026, 11, 10),
            current = listOf(
                day(november, 1, ReportItem.SEAT_HEAT, onSec = 8 * 3600, avg = 2.0),
                day(november, 2, ReportItem.SEAT_HEAT, onSec = 8 * 3600, avg = 1.0),
            ),
            previous = listOf(day(november.minusMonths(1), 5, ReportItem.SEAT_HEAT, onSec = 10 * 3600)),
            supported = null,
            recordingRate = rate,
        )

        val heat = report.top.single()
        assertEquals(16 * 3600L, heat.onSec)
        assertEquals(60, heat.changePercent)
        assertEquals(1.5, heat.avgLevel!!, 0.001)
        assertEquals("시트 열선 16시간 (지난달 대비 +60%)", ReportFormat.headline(heat))
    }

    @Test
    fun `a feature unused last month is new rather than an infinite change`() {
        val report = MonthlyReportBuilder.build(
            november, LocalDate.of(2026, 11, 3),
            current = listOf(day(november, 1, ReportItem.AIR_CONDITIONING, onSec = 600)),
            previous = emptyList(), supported = null, recordingRate = rate,
        )

        val ac = report.usages.single()
        assertTrue(ac.isNew)
        assertNull(ac.changePercent)
        assertEquals(listOf(ac), report.changes)
    }

    @Test
    fun `only supported usage items with no use are listed as unused`() {
        val report = MonthlyReportBuilder.build(
            november, LocalDate.of(2026, 11, 3),
            current = listOf(day(november, 1, ReportItem.SEAT_HEAT, onSec = 600)),
            previous = listOf(day(november.minusMonths(1), 1, ReportItem.SEAT_VENTILATION, onSec = 600)),
            supported = setOf(ReportItem.SEAT_HEAT, ReportItem.SEAT_VENTILATION, ReportItem.DRIVING),
            recordingRate = rate,
        )

        assertEquals(listOf(ReportItem.SEAT_VENTILATION), report.unused)
        assertEquals("시트 통풍: 이번 달 0회", ReportFormat.unused(report.unused.single()))
    }

    @Test
    fun `a month the app recorded nothing in lists nothing as unused`() {
        val report = MonthlyReportBuilder.build(
            month = november.minusMonths(1),
            today = LocalDate.of(2026, 11, 3),
            current = emptyList(),
            previous = emptyList(),
            supported = setOf(ReportItem.SEAT_HEAT, ReportItem.WIPER),
            recordingRate = rate,
        )

        assertTrue(report.unused.isEmpty())
        assertEquals("10월", report.monthLabel)
        assertEquals("10월에는 기록이 없어요.", report.tip)
    }

    @Test
    fun `without a capability snapshot nothing is claimed unused`() {
        val report = MonthlyReportBuilder.build(november, LocalDate.of(2026, 11, 3), emptyList(), emptyList(), supported = null, recordingRate = rate)

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

        val report = MonthlyReportBuilder.build(november, LocalDate.of(2026, 11, 3), current, emptyList(), null, rate)

        assertEquals(listOf(ReportItem.CRUISE, ReportItem.WIPER, ReportItem.LOW_BEAM, ReportItem.FAN, ReportItem.AIR_CONDITIONING), report.top.map { it.item })
        assertEquals(99_999L, report.drivingSec)
        assertEquals(22.5, report.avgTargetCelsius!!, 0.001)
        assertTrue(report.usages.none { it.item == ReportItem.DRIVING || it.item == ReportItem.TARGET_TEMPERATURE })
    }

    @Test
    fun `rows for an unsupported feature never reach the bill`() {
        val report = MonthlyReportBuilder.build(
            november, LocalDate.of(2026, 11, 3),
            current = listOf(day(november, 1, ReportItem.ECO_MODE, onSec = 3600), day(november, 1, ReportItem.SEAT_HEAT, onSec = 600)),
            previous = emptyList(),
            supported = setOf(ReportItem.SEAT_HEAT),
            recordingRate = rate,
        )

        assertEquals(listOf(ReportItem.SEAT_HEAT), report.usages.map { it.item })
    }
}

