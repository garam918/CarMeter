package com.garam.hackathon.aggregate

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodTest {
    private val today = LocalDate.of(2026, 11, 4) // Wednesday

    @Test
    fun `a week runs Monday to Sunday, whatever day it is built from`() {
        val week = Period.of(PeriodKind.WEEK, LocalDate.of(2026, 11, 8)) // Sunday

        assertEquals(LocalDate.of(2026, 11, 2), week.start)
        assertEquals(LocalDate.of(2026, 11, 8), week.endInclusive)
        assertEquals(7, week.days.size)
    }

    @Test
    fun `stepping crosses month and year boundaries`() {
        assertEquals(Period.of(PeriodKind.MONTH, LocalDate.of(2025, 12, 1)), Period.of(PeriodKind.MONTH, LocalDate.of(2026, 1, 20)).previous())
        assertEquals(LocalDate.of(2025, 12, 29), Period.of(PeriodKind.WEEK, LocalDate.of(2026, 1, 1)).start)
        assertEquals(LocalDate.of(2026, 3, 1), Period.of(PeriodKind.DAY, LocalDate.of(2026, 2, 28)).next().start)
    }

    @Test
    fun `the current period is covered up to today, a future one not at all`() {
        assertEquals(LocalDate.of(2026, 11, 2)..today, Period.of(PeriodKind.WEEK, today).covered(today))
        assertEquals(LocalDate.of(2026, 10, 1)..LocalDate.of(2026, 10, 31), Period.of(PeriodKind.MONTH, LocalDate.of(2026, 10, 5)).covered(today))
        assertNull(Period.of(PeriodKind.DAY, today.plusDays(1)).covered(today))
    }

    @Test
    fun `a day is compared with the same weekday a week before`() {
        assertEquals(LocalDate.of(2026, 10, 28).let { it..it }, Period.of(PeriodKind.DAY, today).compared(today))
    }

    @Test
    fun `a week or month in progress is compared with as many days of the previous one`() {
        assertEquals(LocalDate.of(2026, 10, 26)..LocalDate.of(2026, 10, 28), Period.of(PeriodKind.WEEK, today).compared(today))
        assertEquals(LocalDate.of(2026, 10, 1)..LocalDate.of(2026, 10, 4), Period.of(PeriodKind.MONTH, today).compared(today))
    }

    @Test
    fun `a whole month is compared with the whole previous month, cut at its end`() {
        val march = Period.of(PeriodKind.MONTH, LocalDate.of(2026, 3, 1))

        assertEquals(LocalDate.of(2026, 2, 1)..LocalDate.of(2026, 2, 28), march.compared(today))
    }

    @Test
    fun `it survives saving and restoring`() {
        val week = Period.of(PeriodKind.WEEK, today)

        assertEquals(week, Period.parse(week.serialize()))
        assertNull(Period.parse("YEAR:2026-01-01"))
        assertNull(Period.parse("garbage"))
    }

    @Test
    fun `titles read naturally`() {
        assertEquals("11월 고지서", ReportFormat.periodTitle(Period.of(PeriodKind.MONTH, today)))
        assertEquals("11월 2일 ~ 8일", ReportFormat.periodTitle(Period.of(PeriodKind.WEEK, today)))
        assertEquals("10월 26일 ~ 11월 1일", ReportFormat.periodTitle(Period.of(PeriodKind.WEEK, LocalDate.of(2026, 10, 30))))
        assertEquals("11월 4일 수요일", ReportFormat.periodTitle(Period.of(PeriodKind.DAY, today)))
        assertTrue(Period.of(PeriodKind.DAY, today).let { today in it })
    }
}
