package com.example.hyundaihackathon.demo

import android.content.Context
import com.example.hyundaihackathon.appContainer
import com.example.hyundaihackathon.collector.CollectorController
import java.time.Instant
import java.time.ZoneId

/** Demo flavor only: replaces the usage records with synthetic months for the presentation. */
object Demo {
    const val IS_AVAILABLE = true

    /**
     * Writes from the 1st of two months ago up to now, so last month's bill is complete and
     * has a full month before it to compare with. Returns a one-line summary for the screen.
     */
    suspend fun loadSyntheticMonths(context: Context): String {
        val container = context.appContainer
        val zone = ZoneId.systemDefault()
        val now = container.clock.now()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val from = today.minusMonths(2).withDayOfMonth(1)
        val supported = container.capabilityRepository.load().filterValues { it.supported }.keys
        // Continue from the vehicle's real odometer so the next live reading does not jump.
        val odometer = container.tripRepository.snapshotsWithBaseline(now - LOOKBACK_MS, now + 1)
            .lastOrNull { it.metrics.odometerKm != null }?.metrics?.odometerKm
        val data = SyntheticDataGenerator().generate(from, today, zone, now, supported, endOdometerKm = odometer)

        CollectorController.replaceUsageData(context) {
            container.usageRepository.save(data.intervals, data.counters)
            data.trips.forEach { container.tripRepository.saveTrip(it) }
            data.snapshots.forEach { container.tripRepository.saveSnapshot(it) }
        }
        return "합성 데이터를 불러왔어요: ${from.monthValue}월 ${from.dayOfMonth}일 ~ ${today.monthValue}월 ${today.dayOfMonth}일, " +
            "기록된 트립 ${data.trips.size}건, 앱이 꺼져 있던 주행 ${data.missedTrips}건"
    }

    private const val LOOKBACK_MS = 400L * 24 * 60 * 60_000
}
