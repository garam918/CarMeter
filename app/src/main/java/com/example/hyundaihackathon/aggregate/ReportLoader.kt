package com.example.hyundaihackathon.aggregate

import com.example.hyundaihackathon.collector.Clock
import com.example.hyundaihackathon.data.repo.CapabilityRepository
import com.example.hyundaihackathon.data.repo.SettingsRepository
import com.example.hyundaihackathon.data.repo.SummaryRepository
import com.example.hyundaihackathon.data.repo.UsageRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Builds the monthly bill. Finished days come from the daily_summary cache; a missing
 * day is aggregated from raw data and cached once nothing can change it any more (it is
 * before today and no interval that started before its end is still open). Today is
 * always computed live. Nothing runs in the background.
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

    suspend fun load(month: YearMonth): MonthlyReport {
        val today = today()
        val period = MonthlyReportBuilder.period(month, today)
        val compared = MonthlyReportBuilder.comparedPeriod(month, today)
        val zone = timeZone()
        val from = period.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = minOf(period.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), clock.now())
        return MonthlyReportBuilder.build(
            month = month,
            today = today,
            current = days(period, today),
            previous = days(compared, today),
            supported = ReportItem.supported(capabilities.load()),
            recordingRate = recordingRate.recordingRate(from, to),
            disabledCategories = settings.current().disabledCategories,
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
}
