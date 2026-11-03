package com.example.hyundaihackathon.aggregate

import com.example.hyundaihackathon.data.Interval
import com.example.hyundaihackathon.data.SnapshotReason
import com.example.hyundaihackathon.data.Trip
import com.example.hyundaihackathon.data.VehicleMetrics
import com.example.hyundaihackathon.data.VehicleSnapshot
import com.example.hyundaihackathon.vehicle.FeatureKey.DRIVING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingRateCalculatorTest {
    private val minute = 60_000L

    private fun driving(start: Long, end: Long?) = Interval("d$start", DRIVING, 0, 1, start, end)

    private fun snapshot(ts: Long, minutes: Int? = null, km: Double? = null) =
        VehicleSnapshot(ts, SnapshotReason.TRIP_END, VehicleMetrics(odometerKm = km, drivingTimeMin = minutes))

    @Test
    fun `time basis compares recorded driving with the vehicle's driving-time counter`() {
        val rate = RecordingRateCalculator.calculate(
            drivingIntervals = listOf(driving(0, 30 * minute)),
            trips = emptyList(),
            snapshots = listOf(snapshot(0, minutes = 100), snapshot(90 * minute, minutes = 140)),
            from = 0,
            to = 120 * minute,
        )

        assertEquals(RecordingRate.Basis.DRIVING_TIME, rate.basis)
        assertEquals(30.0, rate.recorded, 0.001)
        assertEquals(40.0, rate.reference!!, 0.001)
        assertEquals(0.75, rate.ratio!!, 0.001)
    }

    @Test
    fun `driving is clipped to the period and open intervals run to its end`() {
        val minutes = RecordingRateCalculator.recordedMinutes(
            listOf(driving(-10 * minute, 10 * minute), driving(50 * minute, null)),
            from = 0,
            to = 60 * minute,
        )

        assertEquals(20.0, minutes, 0.001)
    }

    @Test
    fun `distance basis is used when the vehicle has no driving-time counter`() {
        val trip = Trip(
            id = 1, startTs = 1, endTs = 2,
            start = VehicleMetrics(odometerKm = 1_000.0), end = VehicleMetrics(odometerKm = 1_012.0),
        )

        val rate = RecordingRateCalculator.calculate(
            drivingIntervals = emptyList(),
            trips = listOf(trip),
            snapshots = listOf(snapshot(0, km = 990.0), snapshot(10, km = 1_012.0)),
            from = 0,
            to = 100,
        )

        assertEquals(RecordingRate.Basis.DISTANCE, rate.basis)
        assertEquals(12.0, rate.recorded, 0.001)
        assertEquals(22.0, rate.reference!!, 0.001)
    }

    @Test
    fun `without two readings the rate cannot be measured`() {
        val rate = RecordingRateCalculator.calculate(listOf(driving(0, minute)), emptyList(), listOf(snapshot(0, minutes = 5)), 0, 10 * minute)

        assertEquals(RecordingRate.Basis.NONE, rate.basis)
        assertNull(rate.ratio)
    }

    @Test
    fun `no reference driving gives no ratio instead of dividing by zero`() {
        val rate = RecordingRateCalculator.calculate(
            emptyList(), emptyList(), listOf(snapshot(0, minutes = 5), snapshot(10, minutes = 5)), 0, 100,
        )

        assertEquals(0.0, rate.reference!!, 0.0)
        assertNull(rate.ratio)
    }

    @Test
    fun `a counter reset counts the next reading from zero`() {
        assertEquals(15.0, RecordingRateCalculator.counterDelta(listOf(100.0, 110.0, 5.0))!!, 0.001)
    }

    @Test
    fun `the ratio never exceeds one`() {
        val rate = RecordingRate(RecordingRate.Basis.DRIVING_TIME, recorded = 50.0, reference = 40.0)

        assertEquals(1.0, rate.ratio!!, 0.0)
    }
}
