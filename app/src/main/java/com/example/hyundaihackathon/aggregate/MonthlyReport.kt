package com.example.hyundaihackathon.aggregate

import com.example.hyundaihackathon.vehicle.FeatureCategory
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/** One item over the report period, against the same days of the previous month. */
data class ItemUsage(
    val item: ReportItem,
    val onSec: Long,
    val count: Int,
    val avgLevel: Double?,
    val previousOnSec: Long,
    val previousCount: Int,
) {
    /** Seconds for time items, uses for counted items. */
    val amount: Long get() = if (item.kind == ReportItem.Kind.COUNT) count.toLong() else onSec

    val previousAmount: Long get() = if (item.kind == ReportItem.Kind.COUNT) previousCount.toLong() else previousOnSec

    /** Percent change from the previous month, or null when it had no use. */
    val changePercent: Int?
        get() = if (previousAmount == 0L) null else ((amount - previousAmount) * 100.0 / previousAmount).roundToInt()

    val isNew: Boolean get() = previousAmount == 0L && amount > 0
}

data class MonthlyReport(
    val month: YearMonth,
    val firstDay: LocalDate,
    /** Today for the current month, otherwise the month's last day. */
    val lastDay: LocalDate,
    /** The previous month is compared over its days 1 to this one. */
    val comparedUntil: LocalDate,
    /** Usage items with any use in either month, most used first. */
    val usages: List<ItemUsage>,
    /** Up to five most used time items. */
    val top: List<ItemUsage>,
    /** Up to three items whose use changed most against the previous month. */
    val changes: List<ItemUsage>,
    /**
     * Supported items not used this month. Empty while support is unknown, and for a month
     * the app recorded nothing in: no record is not the same as no use.
     */
    val unused: List<ReportItem>,
    val drivingSec: Long,
    /** Average target temperature while the climate system was on, in °C. */
    val avgTargetCelsius: Double?,
    val recordingRate: RecordingRate,
    val tip: String,
    val isCurrentMonth: Boolean,
) {
    val hasData: Boolean get() = usages.any { it.amount > 0 }

    /** "이번 달" for the current month, otherwise "10월". */
    val monthLabel: String get() = if (isCurrentMonth) "이번 달" else "${month.monthValue}월"
}

object MonthlyReportBuilder {
    const val TOP_SIZE = 5
    const val CHANGES_SIZE = 3

    /** The days a report for [month] covers when viewed on [today]. */
    fun period(month: YearMonth, today: LocalDate): ClosedRange<LocalDate> {
        val last = if (month == YearMonth.from(today)) today else month.atEndOfMonth()
        return month.atDay(1)..last
    }

    /** The same days of the previous month, cut at its last day. */
    fun comparedPeriod(month: YearMonth, today: LocalDate): ClosedRange<LocalDate> {
        val previous = month.minusMonths(1)
        val lastDay = minOf(period(month, today).endInclusive.dayOfMonth, previous.lengthOfMonth())
        return previous.atDay(1)..previous.atDay(lastDay)
    }

    fun build(
        month: YearMonth,
        today: LocalDate,
        current: List<DailySummary>,
        previous: List<DailySummary>,
        supported: Set<ReportItem>?,
        recordingRate: RecordingRate,
        disabledCategories: Set<FeatureCategory> = emptySet(),
    ): MonthlyReport {
        val now = total(current)
        val before = total(previous)
        val usages = (now.keys + before.keys)
            // Unsupported features never appear, even if rows for them exist.
            .filter { it.isUsage && (supported == null || it in supported) }
            .map { item ->
                val a = now[item]
                val b = before[item]
                ItemUsage(item, a?.onSec ?: 0, a?.count ?: 0, a?.avgLevel, b?.onSec ?: 0, b?.count ?: 0)
            }
            .sortedWith(compareByDescending<ItemUsage> { it.item.kind == ReportItem.Kind.TIME }.thenByDescending { it.amount })
        val top = usages.filter { it.item.kind == ReportItem.Kind.TIME && it.onSec > 0 }.take(TOP_SIZE)
        val changes = usages
            .filter { it.isNew || (it.changePercent ?: 0) != 0 }
            .sortedByDescending { if (it.isNew) Int.MAX_VALUE else kotlin.math.abs(it.changePercent!!) }
            .take(CHANGES_SIZE)
        val used = usages.filter { it.amount > 0 }.mapTo(mutableSetOf()) { it.item }
        val recorded = used.isNotEmpty() || (now[ReportItem.DRIVING]?.onSec ?: 0) > 0
        // A category the driver switched off is not "unused".
        val unused = if (recorded) {
            supported.orEmpty().filter { it.isUsage && it !in used && it.category !in disabledCategories }.sortedBy { it.ordinal }
        } else {
            emptyList()
        }
        val partial = MonthlyReport(
            month = month,
            firstDay = period(month, today).start,
            lastDay = period(month, today).endInclusive,
            comparedUntil = comparedPeriod(month, today).endInclusive,
            usages = usages,
            top = top,
            changes = changes,
            unused = unused,
            drivingSec = now[ReportItem.DRIVING]?.onSec ?: 0,
            avgTargetCelsius = now[ReportItem.TARGET_TEMPERATURE]?.avgLevel?.div(10),
            recordingRate = recordingRate,
            tip = "",
            isCurrentMonth = month == YearMonth.from(today),
        )
        return partial.copy(tip = TipGenerator.tip(partial, supported?.filterTo(mutableSetOf()) { it.category !in disabledCategories }))
    }

    private data class Total(val onSec: Long, val count: Int, val avgLevel: Double?)

    /** Sums days per item; levels average over time used. */
    private fun total(days: List<DailySummary>): Map<ReportItem, Total> = days.groupBy { it.item }.mapValues { (_, rows) ->
        val onSec = rows.sumOf { it.onSec }
        val weighted = rows.filter { it.avgLevel != null && it.onSec > 0 }
        val weight = weighted.sumOf { it.onSec }
        Total(
            onSec = onSec,
            count = rows.sumOf { it.count },
            avgLevel = if (weight > 0) weighted.sumOf { it.avgLevel!! * it.onSec } / weight else null,
        )
    }
}
