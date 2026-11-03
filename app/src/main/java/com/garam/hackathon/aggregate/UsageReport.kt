package com.garam.hackathon.aggregate

import com.garam.hackathon.vehicle.FeatureCategory
import java.time.LocalDate
import kotlin.math.roundToInt

/** One item over the report period, against the period it is compared with. */
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

    /** Percent change from the compared period, or null when it had no use. */
    val changePercent: Int?
        get() = if (previousAmount == 0L) null else ((amount - previousAmount) * 100.0 / previousAmount).roundToInt()

    val isNew: Boolean get() = previousAmount == 0L && amount > 0
}

/** One bar of the distribution chart: an hour of a day, or a day of a week or month. */
data class UsageBucket(
    val label: String,
    val seconds: Long,
    /** The current hour or today. */
    val isNow: Boolean,
    /** Not reached yet, so empty rather than unused. */
    val isFuture: Boolean,
)

data class UsageReport(
    val period: Period,
    val firstDay: LocalDate,
    /** Today for the current period, otherwise the period's last day. */
    val lastDay: LocalDate,
    /** The days compared with, or null when nothing is covered yet. */
    val compared: ClosedRange<LocalDate>?,
    /** Usage items with any use in either period, most used first. */
    val usages: List<ItemUsage>,
    /** Up to five most used time items. */
    val top: List<ItemUsage>,
    /**
     * Supported items not used this month. Monthly reports only; empty while support is
     * unknown, and for a month the app recorded nothing in: no record is not the same as no use.
     */
    val unused: List<ReportItem>,
    /** Time items added up. Features used at the same time each count, like lines of a bill. */
    val totalSec: Long,
    val previousTotalSec: Long,
    val drivingSec: Long,
    /** Drives that started in the period. */
    val drivingCount: Int,
    /** Average target temperature while the climate system was on, in °C. */
    val avgTargetCelsius: Double?,
    /** Hours of a day, days of a week or month: time items added up per bar. */
    val buckets: List<UsageBucket>,
    val recordingRate: RecordingRate,
    /** Monthly reports only; empty otherwise. */
    val tip: String,
    val isCurrent: Boolean,
) {
    val hasData: Boolean get() = usages.any { it.amount > 0 }

    /** Change of [totalSec] against the compared period, or null when it had no use. */
    val totalChangePercent: Int?
        get() = if (previousTotalSec == 0L) null else ((totalSec - previousTotalSec) * 100.0 / previousTotalSec).roundToInt()

    /** Whether the whole period is covered, so it is compared with a whole period too. */
    val isComplete: Boolean get() = lastDay == period.endInclusive

    /** "이번 달" or "10월", "이번 주", "오늘". */
    val label: String get() = ReportFormat.periodLabel(period, isCurrent)
}

object UsageReportBuilder {
    const val TOP_SIZE = 5

    /**
     * [current] and [previous] are daily rows of the covered and compared days. [hourly], for
     * day reports, holds the rows of each hour, with null for hours not reached yet.
     */
    fun build(
        period: Period,
        today: LocalDate,
        current: List<DailySummary>,
        previous: List<DailySummary>,
        supported: Set<ReportItem>?,
        recordingRate: RecordingRate,
        disabledCategories: Set<FeatureCategory> = emptySet(),
        hourly: List<List<DailySummary>?>? = null,
    ): UsageReport {
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
        val timed = usages.filter { it.item.kind == ReportItem.Kind.TIME }
        val top = timed.filter { it.onSec > 0 }.take(TOP_SIZE)
        val used = usages.filter { it.amount > 0 }.mapTo(mutableSetOf()) { it.item }
        val recorded = used.isNotEmpty() || (now[ReportItem.DRIVING]?.onSec ?: 0) > 0
        // A category the driver switched off is not "unused".
        val unused = if (period.kind == PeriodKind.MONTH && recorded) {
            supported.orEmpty().filter { it.isUsage && it !in used && it.category !in disabledCategories }.sortedBy { it.ordinal }
        } else {
            emptyList()
        }
        val covered = period.covered(today)
        val report = UsageReport(
            period = period,
            firstDay = period.start,
            lastDay = covered?.endInclusive ?: period.endInclusive,
            compared = period.compared(today),
            usages = usages,
            top = top,
            unused = unused,
            totalSec = timed.sumOf { it.onSec },
            previousTotalSec = timed.sumOf { it.previousOnSec },
            drivingSec = now[ReportItem.DRIVING]?.onSec ?: 0,
            drivingCount = now[ReportItem.DRIVING]?.count ?: 0,
            avgTargetCelsius = now[ReportItem.TARGET_TEMPERATURE]?.avgLevel?.div(10),
            buckets = buckets(period, today, current, hourly, timed.mapTo(mutableSetOf()) { it.item }),
            recordingRate = recordingRate,
            tip = "",
            isCurrent = today in period,
        )
        if (period.kind != PeriodKind.MONTH) return report
        return report.copy(tip = TipGenerator.tip(report, supported?.filterTo(mutableSetOf()) { it.category !in disabledCategories }))
    }

    private fun buckets(
        period: Period,
        today: LocalDate,
        current: List<DailySummary>,
        hourly: List<List<DailySummary>?>?,
        items: Set<ReportItem>,
    ): List<UsageBucket> {
        fun seconds(rows: List<DailySummary>) = rows.filter { it.item in items }.sumOf { it.onSec }
        if (period.kind == PeriodKind.DAY) {
            val hours = hourly.orEmpty()
            val nowHour = if (period.start == today) hours.indexOfLast { it != null } else -1
            return hours.mapIndexed { hour, rows -> UsageBucket("$hour", rows?.let(::seconds) ?: 0, hour == nowHour, rows == null) }
        }
        val byDate = current.groupBy { it.date }
        return period.days.map { date ->
            val label = if (period.kind == PeriodKind.WEEK) ReportFormat.weekday(date) else "${date.dayOfMonth}"
            UsageBucket(label, seconds(byDate[date].orEmpty()), date == today, date > today)
        }
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
