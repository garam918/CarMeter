package com.garam.hackathon.aggregate

import com.garam.hackathon.data.CounterEvent
import com.garam.hackathon.data.Interval
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.Levels
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Turns one local day (or part of it) of raw intervals and counter events into [DailySummary] rows.
 *
 * - Intervals are clipped to the day, and open ones run until now.
 * - Time used is the union across zones: two seats heated at once count once.
 * - A level change is not a new use, so touching intervals merge into one use.
 * - Seat ventilation comes from its own property when the vehicle reported it for that
 *   zone that day, otherwise from negative seat heat levels (how the emulator reports it).
 */
object DailyAggregator {
    private data class Segment(val featureKey: FeatureKey, val zone: Int, val level: Int, val start: Long, val end: Long, val startsHere: Boolean) {
        val duration: Long get() = end - start
    }

    fun aggregate(
        date: LocalDate,
        timeZone: ZoneId,
        intervals: List<Interval>,
        counters: List<CounterEvent>,
        now: Long,
    ): List<DailySummary> = aggregateWindow(
        date = date,
        windowStart = date.atStartOfDay(timeZone).toInstant().toEpochMilli(),
        windowEnd = date.plusDays(1).atStartOfDay(timeZone).toInstant().toEpochMilli(),
        intervals = intervals,
        counters = counters,
        now = now,
    )

    /**
     * The same rules over part of [date], such as one hour for the day chart. Rows still
     * carry [date]; a use counts only in the window it started in.
     */
    fun aggregateWindow(
        date: LocalDate,
        windowStart: Long,
        windowEnd: Long,
        intervals: List<Interval>,
        counters: List<CounterEvent>,
        now: Long,
    ): List<DailySummary> {
        val until = minOf(windowEnd, now)
        if (until <= windowStart) return emptyList()

        val byFeature = intervals.mapNotNull { clip(it, windowStart, until) }.groupBy { it.featureKey }
        fun of(key: FeatureKey) = byFeature[key].orEmpty()

        val rows = mutableListOf<DailySummary>()
        fun add(item: ReportItem, segments: List<Segment>) {
            if (segments.isNotEmpty()) rows += summarize(date, item, segments)
        }

        add(ReportItem.SEAT_HEAT, of(FeatureKey.SEAT_HEAT).filter { it.level > 0 })
        val ventilation = of(FeatureKey.SEAT_VENTILATION).filter { it.level > 0 }
        val ventilationZones = ventilation.mapTo(mutableSetOf()) { it.zone }
        add(
            ReportItem.SEAT_VENTILATION,
            ventilation + of(FeatureKey.SEAT_HEAT)
                .filter { it.level < 0 && it.zone !in ventilationZones }
                .map { it.copy(level = -it.level) },
        )
        add(ReportItem.STEERING_WHEEL_HEAT, of(FeatureKey.STEERING_WHEEL_HEAT))
        add(ReportItem.SIDE_MIRROR_HEAT, of(FeatureKey.SIDE_MIRROR_HEAT))
        add(ReportItem.AIR_CONDITIONING, of(FeatureKey.HVAC_AC))
        add(ReportItem.CLIMATE_POWER, of(FeatureKey.HVAC_POWER))
        add(ReportItem.FAN, of(FeatureKey.HVAC_FAN_SPEED))
        add(ReportItem.CLIMATE_AUTO, of(FeatureKey.HVAC_AUTO))
        add(ReportItem.RECIRCULATION, of(FeatureKey.HVAC_RECIRCULATION))
        add(ReportItem.DEFROST, of(FeatureKey.HVAC_DEFROST))
        add(ReportItem.MAX_DEFROST, of(FeatureKey.HVAC_MAX_DEFROST))
        add(ReportItem.MAX_AC, of(FeatureKey.HVAC_MAX_AC))
        add(ReportItem.DUAL_CLIMATE, of(FeatureKey.HVAC_DUAL))
        add(ReportItem.AIR_CLEANER, of(FeatureKey.HVAC_AIR_CLEANER))
        add(ReportItem.LOW_BEAM, of(FeatureKey.LOW_BEAM))
        add(ReportItem.HIGH_BEAM, of(FeatureKey.HIGH_BEAM))
        add(ReportItem.AMBIENT_LIGHT, of(FeatureKey.AMBIENT_LIGHT))
        add(ReportItem.WIPER, of(FeatureKey.WIPER))
        add(ReportItem.CRUISE, of(FeatureKey.CRUISE))
        add(ReportItem.ECO_MODE, of(FeatureKey.DRIVING_MODE).filter { it.level == Levels.MODE_ECO })
        add(ReportItem.SPORT_MODE, of(FeatureKey.DRIVING_MODE).filter { it.level == Levels.MODE_SPORT })
        add(ReportItem.DRIVING, of(FeatureKey.DRIVING))
        add(
            ReportItem.WINDOW_OPEN_DRIVING,
            intersect(of(FeatureKey.WINDOW_OPEN), of(FeatureKey.DRIVING), windowStart).map { it.copy(featureKey = FeatureKey.WINDOW_OPEN) },
        )
        add(ReportItem.TARGET_TEMPERATURE, whileOn(of(FeatureKey.HVAC_TEMPERATURE), of(FeatureKey.HVAC_POWER)))

        val countsByFeature = counters.filter { it.ts in windowStart until until }.groupingBy { it.featureKey }.eachCount()
        countsByFeature[FeatureKey.DOOR_OPEN]?.let { rows += DailySummary(date, ReportItem.DOOR_OPEN, 0, it) }
        countsByFeature[FeatureKey.TURN_SIGNAL]?.let { rows += DailySummary(date, ReportItem.TURN_SIGNAL, 0, it) }
        return rows
    }

    private fun clip(interval: Interval, windowStart: Long, until: Long): Segment? {
        val start = maxOf(interval.startTs, windowStart)
        val end = minOf(interval.endTs ?: until, until)
        if (end <= start) return null
        return Segment(interval.featureKey, interval.zone, interval.level, start, end, startsHere = interval.startTs >= windowStart)
    }

    private fun summarize(date: LocalDate, item: ReportItem, segments: List<Segment>): DailySummary {
        val uses = union(segments)
        val totalMs = segments.sumOf { it.duration }
        val avgLevel = if (item.hasLevels && totalMs > 0) {
            segments.sumOf { abs(it.level).toDouble() * it.duration } / totalMs
        } else {
            null
        }
        return DailySummary(
            date = date,
            item = item,
            onSec = uses.sumOf { it.duration } / 1000,
            count = uses.count { it.startsHere },
            avgLevel = avgLevel,
        )
    }

    /** Merges overlapping or touching segments. A merged use starts here if its first piece did. */
    private fun union(segments: List<Segment>): List<Segment> {
        val merged = mutableListOf<Segment>()
        for (segment in segments.sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if (last != null && segment.start <= last.end) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, segment.end))
            } else {
                merged += segment
            }
        }
        return merged
    }

    /** Time covered by both [a] and [b]. */
    private fun intersect(a: List<Segment>, b: List<Segment>, windowStart: Long): List<Segment> {
        val left = union(a)
        val right = union(b)
        val result = mutableListOf<Segment>()
        for (x in left) for (y in right) {
            val start = maxOf(x.start, y.start)
            val end = minOf(x.end, y.end)
            if (end > start) result += x.copy(start = start, end = end, level = 1, startsHere = start > windowStart || x.startsHere && y.startsHere)
        }
        return result
    }

    /** The parts of [values] that fall while [power] was on, keeping each value's level. */
    private fun whileOn(values: List<Segment>, power: List<Segment>): List<Segment> {
        val on = union(power)
        return values.flatMap { value ->
            on.mapNotNull { window ->
                val start = maxOf(value.start, window.start)
                val end = minOf(value.end, window.end)
                if (end > start) value.copy(start = start, end = end) else null
            }
        }
    }
}
