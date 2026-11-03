package com.garam.hackathon.demo

import com.garam.hackathon.data.ClosedBy
import com.garam.hackathon.data.CounterEvent
import com.garam.hackathon.data.Interval
import com.garam.hackathon.data.SnapshotReason
import com.garam.hackathon.data.Trip
import com.garam.hackathon.data.VehicleMetrics
import com.garam.hackathon.data.VehicleSnapshot
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.Levels
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Synthetic driving for the contest demo, in the same rows the collector writes.
 *
 * - Deterministic: each day draws from its own random stream (seed + date), so a day
 *   always produces the same rows whatever range it is generated in.
 * - Seasonal: the outdoor temperature follows a yearly curve (coldest in mid-January),
 *   so seat and steering heat rise in winter and air conditioning and ventilation in summer.
 * - Honest about gaps: some drives happen while the app is "not running" and only move
 *   the odometer, so the recording rate comes out below 100%.
 * - Only features the vehicle supports are written, so the bill shows the same items
 *   live data would. Zones are the area IDs the emulator reports.
 */
class SyntheticDataGenerator(private val seed: Long = DEFAULT_SEED) {
    data class Output(
        val intervals: List<Interval>,
        val counters: List<CounterEvent>,
        val trips: List<Trip>,
        val snapshots: List<VehicleSnapshot>,
        /** Drives that only moved the odometer, as if the app had not been running. */
        val missedTrips: Int,
    )

    private data class Plan(val start: LocalTime, val minutes: Int, val highway: Boolean)

    private class Day(val date: LocalDate, val celsius: Double, val rainy: Boolean, val random: Random)

    /**
     * Generates [from]..[to] (inclusive), dropping anything that would end after [now].
     * When [endOdometerKm] is given, odometer readings are shifted to end there, so the
     * collector's next live reading continues the series instead of jumping.
     */
    fun generate(
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
        now: Long,
        supported: Set<FeatureKey>,
        endOdometerKm: Double? = null,
    ): Output {
        val rows = Rows(supported)
        var odometer = START_ODOMETER_KM
        var soc = START_SOC
        var missed = 0
        val trips = mutableListOf<Trip>()
        val snapshots = mutableListOf(
            VehicleSnapshot(from.atStartOfDay(zone).toInstant().toEpochMilli(), SnapshotReason.COLLECTOR_START, metrics(odometer, soc)),
        )

        var date = from
        while (date <= to) {
            val random = Random(seed xor (date.toEpochDay() * DAY_MIX))
            val day = Day(date, outdoorCelsius(date, random), random.nextDouble() < RAIN_PROBABILITY, random)
            rows.day = date
            for (plan in plans(day)) {
                val start = date.atTime(plan.start).atZone(zone).toInstant().toEpochMilli()
                val end = start + plan.minutes * MINUTE
                if (end > now) continue
                val km = plan.minutes * if (plan.highway) HIGHWAY_KM_PER_MIN else CITY_KM_PER_MIN
                val startMetrics = metrics(odometer, soc)
                odometer += km
                soc = if (soc - km * SOC_PER_KM < MIN_SOC) CHARGED_SOC else soc - km * SOC_PER_KM
                if (random.nextDouble() < MISSED_TRIP_PROBABILITY) {
                    missed++
                    continue
                }
                val endMetrics = metrics(odometer, soc)
                trips += Trip(id = start, startTs = start, endTs = end, start = startMetrics, end = endMetrics)
                snapshots += VehicleSnapshot(start, SnapshotReason.TRIP_START, startMetrics)
                snapshots += VehicleSnapshot(end, SnapshotReason.TRIP_END, endMetrics)
                drive(rows, day, plan, start, end, tripId = start)
            }
            date = date.plusDays(1)
        }

        val shift = endOdometerKm?.let { it - odometer } ?: 0.0
        return Output(
            intervals = rows.intervals,
            counters = rows.counters,
            trips = trips.map { it.copy(start = it.start.shifted(shift), end = it.end.shifted(shift)) },
            snapshots = snapshots.map { it.copy(metrics = it.metrics.shifted(shift)) },
            missedTrips = missed,
        )
    }

    /** Commutes on weekdays, longer outings at weekends. */
    private fun plans(day: Day): List<Plan> {
        val r = day.random
        val weekend = day.date.dayOfWeek == DayOfWeek.SATURDAY || day.date.dayOfWeek == DayOfWeek.SUNDAY
        val plans = mutableListOf<Plan>()
        if (!weekend) {
            plans += Plan(LocalTime.of(7, 40).plusMinutes(r.nextLong(0, 40)), 25 + r.nextInt(0, 20), highway = false)
            if (r.nextDouble() < 0.25) plans += Plan(LocalTime.of(12, 10).plusMinutes(r.nextLong(0, 30)), 12 + r.nextInt(0, 10), highway = false)
            plans += Plan(LocalTime.of(18, 0).plusMinutes(r.nextLong(0, 60)), 30 + r.nextInt(0, 25), highway = false)
        } else {
            if (r.nextDouble() < 0.85) {
                val minutes = 45 + r.nextInt(0, 90)
                plans += Plan(LocalTime.of(10, 0).plusMinutes(r.nextLong(0, 240)), minutes, highway = minutes > 60)
            }
            if (r.nextDouble() < 0.4) plans += Plan(LocalTime.of(17, 0).plusMinutes(r.nextLong(0, 120)), 20 + r.nextInt(0, 30), highway = false)
        }
        return plans
    }

    /** One recorded drive: the features used during it, door openings and turn signals. */
    private fun drive(rows: Rows, day: Day, plan: Plan, start: Long, end: Long, tripId: Long) {
        val r = day.random
        val t = day.celsius
        val length = end - start
        fun at(fraction: Double) = start + (length * fraction).toLong()
        rows.tripId = tripId

        rows.interval(FeatureKey.DRIVING, GLOBAL, Levels.ON, start, end)

        val climateOn = t < 17 || t > 22 || day.rainy || r.nextDouble() < 0.5
        if (climateOn) {
            rows.interval(FeatureKey.HVAC_POWER, ALL_SEATS, Levels.ON, start, end)
            val target = (if (t < 12) 230 else if (t > 24) 215 else 225) + 5 * r.nextInt(-1, 2)
            rows.interval(FeatureKey.HVAC_TEMPERATURE, DRIVER, target, start, end)
            rows.interval(FeatureKey.HVAC_TEMPERATURE, PASSENGER, target, start, end)
            val fan = if (t < 5 || t > 27) 5 else 3 + r.nextInt(0, 2)
            rows.interval(FeatureKey.HVAC_FAN_SPEED, FRONT_SEATS, fan, start, at(0.4))
            rows.interval(FeatureKey.HVAC_FAN_SPEED, FRONT_SEATS, (fan - 1).coerceAtLeast(1), at(0.4), end)
            if (t > 20 || (day.rainy && r.nextDouble() < 0.6) || r.nextDouble() < 0.1) {
                rows.interval(FeatureKey.HVAC_AC, ALL_SEATS, Levels.ON, start, end)
            }
            if (r.nextDouble() < 0.5) rows.interval(FeatureKey.HVAC_AUTO, FRONT_SEATS, Levels.ON, start, end)
            if (r.nextDouble() < 0.35) rows.interval(FeatureKey.HVAC_RECIRCULATION, ALL_SEATS, Levels.ON, at(0.33), at(0.66))
        }
        if ((t < 5 || day.rainy) && r.nextDouble() < 0.7) {
            rows.interval(FeatureKey.HVAC_DEFROST, FRONT_WINDSHIELD, Levels.ON, start, minOf(end, start + 8 * MINUTE))
        }
        if (t < 0 && r.nextDouble() < 0.4) {
            rows.interval(FeatureKey.HVAC_MAX_DEFROST, ALL_SEATS, Levels.ON, start, minOf(end, start + 5 * MINUTE))
        }

        // Seat heating rises as it gets colder; ventilation is a negative level, as on the emulator.
        val heatChance = ((14 - t) / 12).coerceIn(0.0, 0.95)
        val driverHeat = if (r.nextDouble() < heatChance) (if (t < 3) 3 else if (t < 8) 2 else 1) else 0
        if (driverHeat > 0) {
            val heatEnd = at(0.6 + 0.4 * r.nextDouble())
            if (driverHeat > 1 && plan.minutes > 20 && r.nextDouble() < 0.5) {
                rows.interval(FeatureKey.SEAT_HEAT, DRIVER, driverHeat, start, at(0.4))
                rows.interval(FeatureKey.SEAT_HEAT, DRIVER, driverHeat - 1, at(0.4), heatEnd)
            } else {
                rows.interval(FeatureKey.SEAT_HEAT, DRIVER, driverHeat, start, heatEnd)
            }
        } else if (t > 25 && r.nextDouble() < 0.7) {
            rows.interval(FeatureKey.SEAT_HEAT, DRIVER, -(1 + r.nextInt(0, 3)), start, end)
        }
        val passenger = r.nextDouble() < if (plan.highway) 0.6 else 0.3
        if (passenger && driverHeat > 0 && r.nextDouble() < heatChance) {
            rows.interval(FeatureKey.SEAT_HEAT, PASSENGER, (driverHeat - 1).coerceAtLeast(1), start, at(0.8))
        }
        if (t < 4 && r.nextDouble() < 0.6) {
            rows.interval(FeatureKey.STEERING_WHEEL_HEAT, GLOBAL, if (t < 0) 3 else 2, start, minOf(end, start + (15 + r.nextInt(0, 10)) * MINUTE))
        }
        if (day.rainy && t < 6 && r.nextDouble() < 0.5) {
            rows.interval(FeatureKey.SIDE_MIRROR_HEAT, MIRRORS, Levels.ON, start, minOf(end, start + 10 * MINUTE))
        }

        // Lights follow darkness: before sunrise or after sunset on that date.
        darkPart(day.date, plan, start)?.let { (darkStart, darkEnd) ->
            rows.interval(FeatureKey.LOW_BEAM, GLOBAL, Levels.ON, darkStart, darkEnd)
            if (r.nextDouble() < 0.7) rows.interval(FeatureKey.AMBIENT_LIGHT, DRIVER, Levels.ON, darkStart, darkEnd)
            if (darkEnd - darkStart > 10 * MINUTE && r.nextDouble() < 0.2) {
                val middle = (darkStart + darkEnd) / 2
                rows.interval(FeatureKey.HIGH_BEAM, GLOBAL, Levels.ON, middle, middle + 3 * MINUTE)
            }
        }
        if (day.rainy && r.nextDouble() < 0.85) {
            val level = if (r.nextDouble() < 0.5) Levels.WIPER_CONTINUOUS_1 else Levels.WIPER_INTERMITTENT_1 + 1
            rows.interval(FeatureKey.WIPER, FRONT_WINDSHIELD, level, at(0.2 * r.nextDouble()), at(0.6 + 0.3 * r.nextDouble()))
        }
        if (plan.highway && plan.minutes > 50 && r.nextDouble() < 0.75) {
            rows.interval(FeatureKey.CRUISE, GLOBAL, Levels.ON, at(0.25), at(0.75))
        }
        if (t in 15.0..24.0 && r.nextDouble() < 0.3) {
            val open = at(0.2 + 0.5 * r.nextDouble())
            rows.interval(FeatureKey.WINDOW_OPEN, DRIVER_WINDOW, 3 + r.nextInt(0, 6), open, minOf(end, open + (5 + r.nextInt(0, 10)) * MINUTE))
        }
        val mode = when {
            plan.highway && r.nextDouble() < 0.2 -> Levels.MODE_SPORT
            !plan.highway && r.nextDouble() < 0.3 -> Levels.MODE_ECO
            else -> Levels.MODE_NORMAL
        }
        rows.interval(FeatureKey.DRIVING_MODE, GLOBAL, mode, start, end)

        rows.counter(FeatureKey.DOOR_OPEN, DRIVER, start - 40_000)
        rows.counter(FeatureKey.DOOR_OPEN, DRIVER, end + 15_000)
        if (passenger) {
            rows.counter(FeatureKey.DOOR_OPEN, PASSENGER, start - 30_000)
            rows.counter(FeatureKey.DOOR_OPEN, PASSENGER, end + 20_000)
        }
        val signals = (plan.minutes / 2.5).roundToInt() + r.nextInt(-2, 3)
        repeat(signals.coerceAtLeast(2)) { i ->
            val ts = at((i + 0.5 + 0.4 * (r.nextDouble() - 0.5)) / signals.coerceAtLeast(2))
            rows.counter(FeatureKey.TURN_SIGNAL, if (r.nextBoolean()) TURN_LEFT else TURN_RIGHT, ts)
        }
    }

    /** The dark part of a drive, before sunrise or after sunset on its date, if any. */
    private fun darkPart(date: LocalDate, plan: Plan, start: Long): Pair<Long, Long>? {
        val wave = cos(2 * PI * (date.dayOfYear - 172) / 365.25)
        val sunrise = (6 * 60 + 28 - 77 * wave).roundToInt()
        val sunset = (18 * 60 + 33 + 77 * wave).roundToInt()
        val from = plan.start.hour * 60 + plan.start.minute
        val to = from + plan.minutes
        val (darkFrom, darkTo) = when {
            from < sunrise -> from to minOf(to, sunrise)
            to > sunset -> maxOf(from, sunset) to to
            else -> return null
        }
        if (darkTo <= darkFrom) return null
        return (start + (darkFrom - from) * MINUTE) to (start + (darkTo - from) * MINUTE)
    }

    /** Collects rows for the features the vehicle supports. */
    private class Rows(private val supported: Set<FeatureKey>) {
        val intervals = mutableListOf<Interval>()
        val counters = mutableListOf<CounterEvent>()
        /** IDs restart each day, so a day's rows are the same whatever range is generated. */
        var day: LocalDate = LocalDate.MIN
            set(value) {
                field = value
                next = 0
            }
        var tripId: Long? = null
        private var next = 0

        private fun accepts(key: FeatureKey) = supported.isEmpty() || key in supported

        fun interval(key: FeatureKey, zone: Int, level: Int, start: Long, end: Long) {
            if (!accepts(key) || end <= start || level == 0) return
            intervals += Interval(id(), key, zone, level, start, end, ClosedBy.EVENT, tripId)
        }

        fun counter(key: FeatureKey, zone: Int, ts: Long) {
            if (accepts(key)) counters += CounterEvent(id(), key, zone, ts, tripId)
        }

        private fun id() = "demo-${day.toEpochDay()}-${next++}"
    }

    companion object {
        const val DEFAULT_SEED = 20_261_115L

        private const val DAY_MIX = 0x2545F4914F6CDD1DL
        private const val MINUTE = 60_000L
        private const val RAIN_PROBABILITY = 0.22
        private const val MISSED_TRIP_PROBABILITY = 0.06
        private const val CITY_KM_PER_MIN = 0.45
        private const val HIGHWAY_KM_PER_MIN = 1.3
        private const val START_ODOMETER_KM = 12_480.0
        private const val START_SOC = 82.0
        private const val SOC_PER_KM = 0.18
        private const val MIN_SOC = 25.0
        private const val CHARGED_SOC = 90.0

        // Zones as the emulator reports them (docs/sdk-notes.md 9-10).
        private const val GLOBAL = 0
        private const val DRIVER = 1
        private const val PASSENGER = 4
        private const val FRONT_SEATS = 5
        private const val ALL_SEATS = 117
        private const val FRONT_WINDSHIELD = 1
        private const val DRIVER_WINDOW = 16
        private const val MIRRORS = 3
        private const val TURN_LEFT = 1
        private const val TURN_RIGHT = 2

        /** Mid-January about -2°C, late July about 27°C, plus a few degrees of daily noise. */
        fun outdoorCelsius(date: LocalDate, random: Random): Double =
            12.5 - 14.5 * cos(2 * PI * (date.dayOfYear - 18) / 365.25) + (random.nextDouble() - 0.5) * 6

        private fun metrics(odometer: Double, soc: Double) = VehicleMetrics(odometerKm = odometer, socPercent = soc.roundToInt())

        private fun VehicleMetrics.shifted(km: Double) = copy(odometerKm = odometerKm?.plus(km))
    }
}
