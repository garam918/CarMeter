package com.garam.hackathon.aggregate

import com.garam.hackathon.data.CounterEvent
import com.garam.hackathon.data.Interval
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.Levels
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyAggregatorTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val day = LocalDate.of(2026, 11, 3)
    private val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L
    private val minute = 60_000L
    private val endOfDay = dayStart + 24 * hour
    private var nextId = 0

    private fun interval(key: FeatureKey, zone: Int, level: Int, start: Long, end: Long?) =
        Interval("i${nextId++}", key, zone, level, start, end)

    private fun aggregate(intervals: List<Interval>, counters: List<CounterEvent> = emptyList(), now: Long = endOfDay + hour) =
        DailyAggregator.aggregate(day, zone, intervals, counters, now).associateBy { it.item }

    @Test
    fun `an interval across midnight is split, and only the day that saw it start counts a use`() {
        val crossing = interval(FeatureKey.HVAC_AC, 1, 1, dayStart - hour, dayStart + 2 * hour)

        val today = aggregate(listOf(crossing)).getValue(ReportItem.AIR_CONDITIONING)
        val yesterday = DailyAggregator.aggregate(day.minusDays(1), zone, listOf(crossing), emptyList(), endOfDay)
            .single { it.item == ReportItem.AIR_CONDITIONING }

        assertEquals(2 * 3600L, today.onSec)
        assertEquals(0, today.count)
        assertEquals(3600L, yesterday.onSec)
        assertEquals(1, yesterday.count)
    }

    @Test
    fun `seats heated at the same time count once, as wall-clock time`() {
        val rows = aggregate(
            listOf(
                interval(FeatureKey.SEAT_HEAT, 1, 2, dayStart + hour, dayStart + 3 * hour),
                interval(FeatureKey.SEAT_HEAT, 4, 1, dayStart + 2 * hour, dayStart + 4 * hour),
            ),
        )

        val heat = rows.getValue(ReportItem.SEAT_HEAT)
        assertEquals(3 * 3600L, heat.onSec)
        assertEquals(1, heat.count)
        assertEquals(1.5, heat.avgLevel!!, 0.001)
    }

    @Test
    fun `a level change is the same use`() {
        val rows = aggregate(
            listOf(
                interval(FeatureKey.SEAT_HEAT, 1, 1, dayStart + hour, dayStart + 2 * hour),
                interval(FeatureKey.SEAT_HEAT, 1, 3, dayStart + 2 * hour, dayStart + 3 * hour),
            ),
        )

        assertEquals(1, rows.getValue(ReportItem.SEAT_HEAT).count)
        assertEquals(2.0, rows.getValue(ReportItem.SEAT_HEAT).avgLevel!!, 0.001)
    }

    @Test
    fun `negative seat heat levels are ventilation`() {
        val rows = aggregate(listOf(interval(FeatureKey.SEAT_HEAT, 1, -2, dayStart + hour, dayStart + 2 * hour)))

        assertNull(rows[ReportItem.SEAT_HEAT])
        assertEquals(3600L, rows.getValue(ReportItem.SEAT_VENTILATION).onSec)
        assertEquals(2.0, rows.getValue(ReportItem.SEAT_VENTILATION).avgLevel!!, 0.001)
    }

    @Test
    fun `a ventilation property for the same zone wins over negative seat heat`() {
        val rows = aggregate(
            listOf(
                interval(FeatureKey.SEAT_VENTILATION, 1, 3, dayStart + hour, dayStart + 2 * hour),
                interval(FeatureKey.SEAT_HEAT, 1, -3, dayStart + hour, dayStart + 2 * hour),
            ),
        )

        val ventilation = rows.getValue(ReportItem.SEAT_VENTILATION)
        assertEquals(3600L, ventilation.onSec)
        assertEquals(3.0, ventilation.avgLevel!!, 0.001)
    }

    @Test
    fun `window open while driving is the overlap of both`() {
        val rows = aggregate(
            listOf(
                interval(FeatureKey.WINDOW_OPEN, 16, 6, dayStart + hour, dayStart + 3 * hour),
                interval(FeatureKey.DRIVING, 0, 1, dayStart + 2 * hour, dayStart + 5 * hour),
            ),
        )

        assertEquals(3600L, rows.getValue(ReportItem.WINDOW_OPEN_DRIVING).onSec)
        assertEquals(3 * 3600L, rows.getValue(ReportItem.DRIVING).onSec)
    }

    @Test
    fun `the target temperature averages only while the climate system is on`() {
        val rows = aggregate(
            listOf(
                interval(FeatureKey.HVAC_TEMPERATURE, 1, 200, dayStart, dayStart + 2 * hour),
                interval(FeatureKey.HVAC_TEMPERATURE, 1, 260, dayStart + 2 * hour, dayStart + 4 * hour),
                interval(FeatureKey.HVAC_POWER, 117, 1, dayStart + hour, dayStart + 3 * hour),
            ),
        )

        val temperature = rows.getValue(ReportItem.TARGET_TEMPERATURE)
        assertEquals(230.0, temperature.avgLevel!!, 0.001)
        assertEquals(2 * 3600L, temperature.onSec)
    }

    @Test
    fun `drive modes split into eco and sport items`() {
        val rows = aggregate(
            listOf(
                interval(FeatureKey.DRIVING_MODE, 0, Levels.MODE_ECO, dayStart, dayStart + hour),
                interval(FeatureKey.DRIVING_MODE, 0, Levels.MODE_NORMAL, dayStart + hour, dayStart + 2 * hour),
                interval(FeatureKey.DRIVING_MODE, 0, Levels.MODE_SPORT, dayStart + 2 * hour, dayStart + 2 * hour + 30 * minute),
            ),
        )

        assertEquals(3600L, rows.getValue(ReportItem.ECO_MODE).onSec)
        assertEquals(1800L, rows.getValue(ReportItem.SPORT_MODE).onSec)
    }

    @Test
    fun `open intervals run until now`() {
        val now = dayStart + 10 * hour
        val rows = aggregate(listOf(interval(FeatureKey.WIPER, 1, 21, dayStart + 9 * hour, null)), now = now)

        assertEquals(3600L, rows.getValue(ReportItem.WIPER).onSec)
    }

    @Test
    fun `counted features sum their events for the day`() {
        val counters = listOf(
            CounterEvent("a", FeatureKey.DOOR_OPEN, 1, dayStart + hour),
            CounterEvent("b", FeatureKey.DOOR_OPEN, 4, dayStart + 2 * hour),
            CounterEvent("c", FeatureKey.DOOR_OPEN, 1, dayStart - hour),
            CounterEvent("d", FeatureKey.TURN_SIGNAL, 1, dayStart + 3 * hour),
        )

        val rows = aggregate(emptyList(), counters)

        assertEquals(2, rows.getValue(ReportItem.DOOR_OPEN).count)
        assertEquals(1, rows.getValue(ReportItem.TURN_SIGNAL).count)
    }

    @Test
    fun `hours split a use, which counts only in the hour it started`() {
        val use = interval(FeatureKey.SEAT_HEAT, 1, 2, dayStart + 8 * hour + 30 * minute, dayStart + 9 * hour + 15 * minute)
        fun hour(h: Int) = DailyAggregator.aggregateWindow(day, dayStart + h * hour, dayStart + (h + 1) * hour, listOf(use), emptyList(), endOfDay)
            .single { it.item == ReportItem.SEAT_HEAT }

        assertEquals(30 * 60L, hour(8).onSec)
        assertEquals(1, hour(8).count)
        assertEquals(15 * 60L, hour(9).onSec)
        assertEquals(0, hour(9).count)
        assertEquals(day, hour(9).date)
    }

    @Test
    fun `a day in the future has nothing`() {
        assertTrue(DailyAggregator.aggregate(day, zone, listOf(interval(FeatureKey.HVAC_AC, 1, 1, dayStart, null)), emptyList(), dayStart - 1).isEmpty())
    }
}
