package com.garam.hackathon.fakes

import com.garam.hackathon.aggregate.DailySummary
import com.garam.hackathon.collector.Clock
import com.garam.hackathon.data.CounterEvent
import com.garam.hackathon.data.Interval
import com.garam.hackathon.data.Trip
import com.garam.hackathon.data.VehicleMetrics
import com.garam.hackathon.data.VehicleSnapshot
import com.garam.hackathon.data.repo.AppStateRepository
import com.garam.hackathon.data.repo.CapabilityRepository
import com.garam.hackathon.data.repo.CollectorSettings
import com.garam.hackathon.data.repo.SettingsRepository
import com.garam.hackathon.data.repo.SummaryRepository
import com.garam.hackathon.data.repo.TripRepository
import com.garam.hackathon.data.repo.UsageRepository
import com.garam.hackathon.vehicle.CapabilityResult
import com.garam.hackathon.vehicle.FeatureCategory
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.FeatureListener
import com.garam.hackathon.vehicle.FeatureSource
import com.garam.hackathon.vehicle.SdkError
import com.garam.hackathon.vehicle.VehicleMetric
import com.garam.hackathon.vehicle.VehicleMetricsReader
import com.garam.hackathon.vehicle.VehicleSession
import com.garam.hackathon.vehicle.ZoneLevel
import com.garam.hackathon.vehicle.ZoneRange
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeClock(var time: Long = 0L) : Clock {
    override fun now() = time
}

/** A source whose capability and readings the test sets, and whose listener the test drives. */
class FakeFeatureSource(
    override val featureKey: FeatureKey,
    private val capability: CapabilityResult = CapabilityResult(supported = true, zones = listOf(ZoneRange(1))),
    var initialState: List<ZoneLevel> = emptyList(),
    private val startError: SdkError? = null,
) : FeatureSource {
    private var listener: FeatureListener? = null
    var stopCount = 0
        private set
    var checkCount = 0
        private set

    val isStarted: Boolean get() = listener != null

    override suspend fun checkCapability(): CapabilityResult {
        checkCount++
        return capability
    }

    override suspend fun readInitialState() = initialState

    override fun start(listener: FeatureListener) {
        startError?.let { throw it }
        this.listener = listener
    }

    override fun stop() {
        listener = null
        stopCount++
    }

    fun emit(zone: Int, level: Int?) {
        checkNotNull(listener) { "$featureKey is not started" }.onLevel(featureKey, ZoneLevel(zone, level))
    }
}

class FakeVehicleSession : VehicleSession {
    var connectCount = 0
        private set
    var releaseCount = 0
        private set

    override fun connect() {
        connectCount++
    }

    override suspend fun release() {
        releaseCount++
    }
}

/** Returns [metrics] on every read; the test changes it between readings. */
class FakeMetricsReader(
    private val supported: Set<VehicleMetric> = emptySet(),
    var metrics: VehicleMetrics = VehicleMetrics(),
) : VehicleMetricsReader {
    var readCount = 0
        private set

    override suspend fun prepare() = supported

    override suspend fun read(): VehicleMetrics {
        readCount++
        return metrics
    }
}

class InMemoryUsageRepository(initial: List<Interval> = emptyList()) : UsageRepository {
    private val rows = MutableStateFlow(initial.associateBy { it.id })
    private val counterRows = MutableStateFlow<List<CounterEvent>>(emptyList())
    var saveCount = 0
        private set

    /** Makes the next save throw, to test failure handling. */
    var failNextSave = false

    val all: List<Interval> get() = rows.value.values.toList()
    val counters: List<CounterEvent> get() = counterRows.value

    override suspend fun save(intervals: List<Interval>, counters: List<CounterEvent>) {
        if (failNextSave) {
            failNextSave = false
            error("disk full")
        }
        saveCount++
        rows.value = rows.value + intervals.associateBy { it.id }
        counterRows.value = counterRows.value + counters
    }

    override suspend fun openIntervals() = all.filter { it.isOpen }

    override fun recentIntervals(limit: Int): Flow<List<Interval>> =
        rows.map { map -> map.values.sortedByDescending { it.startTs }.take(limit) }

    override fun recentCounters(limit: Int): Flow<List<CounterEvent>> =
        counterRows.map { list -> list.sortedByDescending { it.ts }.take(limit) }

    override suspend fun intervalsOverlapping(featureKey: FeatureKey, from: Long, to: Long) =
        all.filter { it.featureKey == featureKey && it.startTs < to && (it.endTs ?: Long.MAX_VALUE) > from }

    override suspend fun allIntervalsOverlapping(from: Long, to: Long) =
        all.filter { it.startTs < to && (it.endTs ?: Long.MAX_VALUE) > from }

    override suspend fun countersBetween(from: Long, to: Long) = counters.filter { it.ts in from until to }

    override suspend fun hasOpenIntervalStartedBefore(ts: Long) = all.any { it.isOpen && it.startTs < ts }
}

class InMemorySummaryRepository : SummaryRepository {
    private val rows = mutableMapOf<LocalDate, List<DailySummary>>()
    var saveCount = 0
        private set

    val savedDates: Set<LocalDate> get() = rows.keys

    override suspend fun between(from: LocalDate, to: LocalDate) =
        rows.filterKeys { it in from..to }.values.flatten()

    override suspend fun save(dates: Collection<LocalDate>, summaries: List<DailySummary>) {
        saveCount++
        for (date in dates) rows[date] = summaries.filter { it.date == date }
    }

    override suspend fun summarizedDates(from: LocalDate, to: LocalDate) = rows.keys.filterTo(mutableSetOf()) { it in from..to }
}

class InMemoryTripRepository(initial: List<Trip> = emptyList()) : TripRepository {
    private val tripRows = MutableStateFlow(initial.associateBy { it.id })
    val snapshots = mutableListOf<VehicleSnapshot>()

    val trips: List<Trip> get() = tripRows.value.values.sortedBy { it.startTs }

    override suspend fun saveTrip(trip: Trip) {
        tripRows.value = tripRows.value + (trip.id to trip)
    }

    override suspend fun openTrips() = trips.filter { it.isOpen }

    override suspend fun lastTrip() = trips.lastOrNull()

    override fun recentTrips(limit: Int): Flow<List<Trip>> =
        tripRows.map { map -> map.values.sortedByDescending { it.startTs }.take(limit) }

    override suspend fun tripsStartedBetween(from: Long, to: Long) = trips.filter { it.startTs in from until to }

    override suspend fun saveSnapshot(snapshot: VehicleSnapshot) {
        snapshots += snapshot
    }

    override suspend fun snapshotsWithBaseline(from: Long, to: Long): List<VehicleSnapshot> {
        val sorted = snapshots.sortedBy { it.ts }
        val baseline = sorted.lastOrNull { it.ts < from }
        return listOfNotNull(baseline) + sorted.filter { it.ts in from until to }
    }
}

class InMemoryCapabilityRepository(initial: Map<FeatureKey, CapabilityResult>? = null) : CapabilityRepository {
    var saved: Map<FeatureKey, CapabilityResult>? = initial
        private set
    var version: Long? = null
    var replaceCount = 0
        private set

    override suspend fun storedVersion() = version

    override suspend fun replace(results: Map<FeatureKey, CapabilityResult>, appVersionCode: Long, checkedAt: Long) {
        saved = results
        version = appVersionCode
        replaceCount++
    }

    override suspend fun load() = saved.orEmpty()
}

/** Starts consented with recording on, unless the test says otherwise. */
class InMemorySettingsRepository(initial: CollectorSettings = CollectorSettings(consentAt = 1L)) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<CollectorSettings> = state

    override suspend fun giveConsent(at: Long) = state.update { it.copy(consentAt = at) }

    override suspend fun withdrawConsent() = state.update { CollectorSettings() }

    override suspend fun setRecordingEnabled(enabled: Boolean) = state.update { it.copy(recordingEnabled = enabled) }

    override suspend fun setCategoryEnabled(category: FeatureCategory, enabled: Boolean) = state.update {
        it.copy(disabledCategories = if (enabled) it.disabledCategories - category else it.disabledCategories + category)
    }
}

class InMemoryAppStateRepository(private var lastAlive: Long? = null) : AppStateRepository {
    override suspend fun lastAlive() = lastAlive

    override suspend fun setLastAlive(timestamp: Long) {
        lastAlive = timestamp
    }
}
