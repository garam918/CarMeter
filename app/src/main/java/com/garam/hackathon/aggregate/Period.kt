package com.garam.hackathon.aggregate

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class PeriodKind { DAY, WEEK, MONTH }

/**
 * One day, one Monday-to-Sunday week or one calendar month, identified by its first day.
 * Build it with [of], which moves any date to the start of its period.
 */
data class Period(val kind: PeriodKind, val start: LocalDate) {
    init {
        require(start == startOf(kind, start)) { "$start does not start a $kind" }
    }

    val endInclusive: LocalDate
        get() = when (kind) {
            PeriodKind.DAY -> start
            PeriodKind.WEEK -> start.plusDays(6)
            PeriodKind.MONTH -> start.withDayOfMonth(start.lengthOfMonth())
        }

    val days: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= endInclusive }.toList()

    operator fun contains(date: LocalDate): Boolean = date in start..endInclusive

    fun previous(): Period = shifted(-1)

    fun next(): Period = shifted(1)

    /** The days shown when viewed on [today]: up to today for the current period, none for a future one. */
    fun covered(today: LocalDate): ClosedRange<LocalDate>? = if (start > today) null else start..minOf(endInclusive, today)

    /**
     * What this period is compared with. A day: the same weekday a week earlier. A week or
     * a month: as many days from the start of the previous one as are covered here, cut at
     * its end, so a month in progress is not compared with a whole month.
     */
    fun compared(today: LocalDate): ClosedRange<LocalDate>? {
        val covered = covered(today) ?: return null
        if (kind == PeriodKind.DAY) return start.minusWeeks(1).let { it..it }
        val previous = previous()
        val elapsed = covered.endInclusive.toEpochDay() - start.toEpochDay()
        return previous.start..minOf(previous.start.plusDays(elapsed), previous.endInclusive)
    }

    /** For saved state: "MONTH:2026-11-01". */
    fun serialize(): String = "${kind.name}:$start"

    private fun shifted(steps: Long): Period = when (kind) {
        PeriodKind.DAY -> of(kind, start.plusDays(steps))
        PeriodKind.WEEK -> of(kind, start.plusWeeks(steps))
        PeriodKind.MONTH -> of(kind, start.plusMonths(steps))
    }

    companion object {
        fun of(kind: PeriodKind, date: LocalDate): Period = Period(kind, startOf(kind, date))

        fun parse(text: String): Period? {
            val (kind, date) = text.split(":").takeIf { it.size == 2 } ?: return null
            val parsedKind = PeriodKind.entries.firstOrNull { it.name == kind } ?: return null
            return runCatching { of(parsedKind, LocalDate.parse(date)) }.getOrNull()
        }

        private fun startOf(kind: PeriodKind, date: LocalDate): LocalDate = when (kind) {
            PeriodKind.DAY -> date
            PeriodKind.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            PeriodKind.MONTH -> date.withDayOfMonth(1)
        }
    }
}
