package com.garam.hackathon.aggregate

import com.garam.hackathon.collector.Clock
import com.garam.hackathon.data.repo.CapabilityRepository
import com.garam.hackathon.data.repo.SettingsRepository
import com.garam.hackathon.data.repo.SummaryRepository
import com.garam.hackathon.data.repo.UsageRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds the day, week and month reports. Finished days come from the daily_summary cache;
 * a missing day is aggregated from raw data and cached once nothing can change it any more
 * (it is before today and no interval that started before its end is still open). Today,
 * the hours of a day and today's comparison day are always computed live. Nothing runs in
 * the background.
 */
class ReportLoader(
    private val usage: UsageRepository,
    private val summaries: SummaryRepository,
    private val capabilities: CapabilityRepository,
    private val settings: SettingsRepository,
    private val recordingRate: RecordingRateQuery,
    private val clock: Clock,
    private val timeZone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun today(): LocalDate = Instant.ofEpochMilli(clock.now()).atZone(timeZone()).toLocalDate()

    suspend fun load(period: Period): UsageReport {
        val today = today()
        val zone = timeZone()
        val now = clock.now()
        val covered = period.covered(today)
        val compared = period.compared(today)
        val previous = when {
            compared == null -> emptyList()
            // Today so far against the same weekday last week up to the same time.
            period.kind == PeriodKind.DAY && period.start == today ->
                live(compared.start, Instant.ofEpochMilli(now).atZone(zone).minusWeeks(1).toInstant().toEpochMilli())
            else -> days(compared, today)
        }
        val rate = if (covered == null) {
            RecordingRate(RecordingRate.Basis.NONE, 0.0, null)
        } else {
            val from = covered.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = minOf(covered.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
            recordingRate.recordingRate(from, to)
        }
        return UsageReportBuilder.build(
            period = period,
            today = today,
            current = covered?.let { days(it, today) }.orEmpty(),
            previous = previous,
            supported = ReportItem.supported(capabilities.load()),
            recordingRate = rate,
            disabledCategories = settings.current().disabledCategories,
            hourly = if (period.kind == PeriodKind.DAY && covered != null) hours(period.start, now) else null,
        )
    }

    private suspend fun days(range: ClosedRange<LocalDate>, today: LocalDate): List<DailySummary> {
        if (range.start > today) return emptyList()
        val end = minOf(range.endInclusive, today)
        // Read the cache before aggregating: aggregate() adds the days it finishes to it.
        val cached = summaries.between(range.start, end)
        val cachedDates = summaries.summarizedDates(range.start, end)
        val missing = generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= end }.filter { it !in cachedDates }.toList()
        val computed = if (missing.isEmpty()) emptyList() else aggregate(missing, today)
        return cached + computed
    }

    private suspend fun aggregate(dates: List<LocalDate>, today: LocalDate): List<DailySummary> {
        val zone = timeZone()
        val now = clock.now()
        val from = dates.first().atStartOfDay(zone).toInstant().toEpochMilli()
        val to = dates.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val intervals = usage.allIntervalsOverlapping(from, to)
        val counters = usage.countersBetween(from, to)
        val rows = dates.flatMap { DailyAggregator.aggregate(it, zone, intervals, counters, now) }

        val finished = dates.filter { date ->
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            date < today && !usage.hasOpenIntervalStartedBefore(dayEnd)
        }
        if (finished.isNotEmpty()) summaries.save(finished, rows.filter { it.date in finished })
        return rows
    }

    /** [date] counted only until [until], never cached. */
    private suspend fun live(date: LocalDate, until: Long): List<DailySummary> {
        val zone = timeZone()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return DailyAggregator.aggregate(date, zone, usage.allIntervalsOverlapping(from, to), usage.countersBetween(from, to), until)
    }

    /** The rows of each hour of [date], with null for hours that have not started yet. */
    private suspend fun hours(date: LocalDate, now: Long): List<List<DailySummary>?> {
        val zone = timeZone()
        val dayStart = date.atStartOfDay(zone)
        val from = dayStart.toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val intervals = usage.allIntervalsOverlapping(from, to)
        val counters = usage.countersBetween(from, to)
        return (0 until HOURS).map { hour ->
            val start = dayStart.plusHours(hour.toLong()).toInstant().toEpochMilli()
            val end = dayStart.plusHours(hour + 1L).toInstant().toEpochMilli()
            if (start >= now) null else DailyAggregator.aggregateWindow(date, start, end, intervals, counters, now)
        }
    }

    private companion object {
        const val HOURS = 24
    }
}
