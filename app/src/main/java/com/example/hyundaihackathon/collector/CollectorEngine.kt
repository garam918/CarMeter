package com.example.hyundaihackathon.collector

import android.util.Log
import com.example.hyundaihackathon.data.ClosedBy
import com.example.hyundaihackathon.data.Interval
import com.example.hyundaihackathon.data.SnapshotReason
import com.example.hyundaihackathon.data.Trip
import com.example.hyundaihackathon.data.VehicleMetrics
import com.example.hyundaihackathon.data.VehicleSnapshot
import com.example.hyundaihackathon.data.repo.AppStateRepository
import com.example.hyundaihackathon.data.repo.CapabilityRepository
import com.example.hyundaihackathon.data.repo.SettingsRepository
import com.example.hyundaihackathon.data.repo.TripRepository
import com.example.hyundaihackathon.data.repo.UsageRepository
import com.example.hyundaihackathon.vehicle.CapabilityResult
import com.example.hyundaihackathon.vehicle.FeatureCategory
import com.example.hyundaihackathon.vehicle.FeatureKey
import com.example.hyundaihackathon.vehicle.FeatureKind
import com.example.hyundaihackathon.vehicle.FeatureListener
import com.example.hyundaihackathon.vehicle.FeatureSource
import com.example.hyundaihackathon.vehicle.SdkError
import com.example.hyundaihackathon.vehicle.VehicleMetric
import com.example.hyundaihackathon.vehicle.VehicleMetricsReader
import com.example.hyundaihackathon.vehicle.VehicleSession
import com.example.hyundaihackathon.vehicle.ZoneLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface CollectorStatus {
    data object Stopped : CollectorStatus

    data object Starting : CollectorStatus

    data class Running(
        val capabilities: Map<FeatureKey, CapabilityResult>,
        val metrics: Set<VehicleMetric>,
    ) : CollectorStatus

    /** The vehicle service could not be reached for any feature. */
    data class Failed(val capabilities: Map<FeatureKey, CapabilityResult>) : CollectorStatus
}

/**
 * Runs the collection: closes what a dead process left open, checks each source's
 * capability, records its initial state, then follows its listeners. Driving intervals
 * also drive trips, whose start and end take one vehicle reading each. SDK callbacks
 * arrive on Binder threads; every event, the heartbeat and start/stop run on one serial
 * dispatcher, so the trackers and buffer need no locking.
 */
class CollectorEngine(
    private val session: VehicleSession,
    private val sources: List<FeatureSource>,
    private val metrics: VehicleMetricsReader,
    private val usage: UsageRepository,
    private val trips: TripRepository,
    private val capabilitySnapshot: CapabilityRepository,
    private val appState: AppStateRepository,
    private val settings: SettingsRepository,
    private val appVersionCode: Long,
    private val clock: Clock,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    heartbeatMs: Long = HEARTBEAT_MS,
) {
    private val work = CoroutineScope(scope.coroutineContext + dispatcher)
    private val intervals = IntervalTracker()
    private val counters = CounterTracker(mergeOffMs = { key -> if (key == FeatureKey.TURN_SIGNAL) TURN_SIGNAL_MERGE_MS else 0L })
    private val tripTracker = TripTracker()
    private val buffer = EventBuffer(usage)
    private val recovery = RecoveryManager(usage, trips, appState)
    private val heartbeat = Heartbeat(heartbeatMs) { beat() }
    private val lifecycle = Mutex()

    // Trip changes read the vehicle, which suspends; the lock keeps them in event order.
    private val tripLock = Mutex()
    private val startedSources = mutableListOf<FeatureSource>()
    private var capabilities = linkedMapOf<FeatureKey, CapabilityResult>()
    private var disabledCategories: Set<FeatureCategory> = emptySet()
    private var settingsJob: Job? = null
    private var supportedMetrics: Set<VehicleMetric> = emptySet()
    private var accepting = false

    private val _status = MutableStateFlow<CollectorStatus>(CollectorStatus.Stopped)
    val status: StateFlow<CollectorStatus> = _status.asStateFlow()

    private val listener = object : FeatureListener {
        override fun onLevel(featureKey: FeatureKey, reading: ZoneLevel) {
            val timestamp = clock.now()
            work.launch { record(featureKey, reading, timestamp, live = true) }
        }

        override fun onError(featureKey: FeatureKey, error: SdkError) {
            Log.w(TAG, "$featureKey listener error: ${error.code}")
        }
    }

    /**
     * Starts collecting unless already running, and returns the resulting status. Without
     * consent, or with the master switch off, it stays [CollectorStatus.Stopped].
     */
    suspend fun start(): CollectorStatus = lifecycle.withLock {
        withContext(dispatcher) {
            val current = _status.value
            if (current is CollectorStatus.Starting || current is CollectorStatus.Running) return@withContext current
            val currentSettings = settings.current()
            if (!currentSettings.canRecord) {
                Log.i(TAG, "Not starting: consent=${currentSettings.hasConsent} enabled=${currentSettings.recordingEnabled}")
                return@withContext CollectorStatus.Stopped
            }
            disabledCategories = currentSettings.disabledCategories
            _status.value = CollectorStatus.Starting
            val capabilities = linkedMapOf<FeatureKey, CapabilityResult>()
            this@CollectorEngine.capabilities = capabilities
            try {
                val recovered = recovery.closeStale()
                if (recovered > 0) Log.i(TAG, "Closed $recovered intervals left open by a previous process")
                appState.setLastAlive(clock.now())
                session.connect()
                accepting = true
                tripTracker.restore(trips.lastTrip())
                supportedMetrics = try {
                    metrics.prepare()
                } catch (e: SdkError) {
                    emptySet()
                }

                // Every source gets a capability check, so the snapshot is complete even for
                // categories the driver switched off; only enabled ones start.
                for (source in sources) capabilities[source.featureKey] = startSource(source, isEnabled(source))
                capabilities.forEach { (key, result) ->
                    Log.i(TAG, "$key supported=${result.supported} zones=${result.zones.size} error=${result.error}")
                }
                Log.i(TAG, "metrics=$supportedMetrics")
                saveCapabilitySnapshot(capabilities)
                snapshot(SnapshotReason.COLLECTOR_START)
            } catch (e: Exception) {
                Log.e(TAG, "Start failed", e)
                capabilities.clear()
            }

            if (capabilities.values.none { it.serviceReachable }) {
                shutDown()
                _status.value = CollectorStatus.Failed(capabilities)
            } else {
                heartbeat.start(work)
                _status.value = CollectorStatus.Running(capabilities.toMap(), supportedMetrics)
                settingsJob = work.launch {
                    settings.settings.map { it.disabledCategories }.distinctUntilChanged().collect { applyCategories(it) }
                }
            }
            _status.value
        }
    }

    /** Closes every open interval and trip with [ClosedBy.STOP], saves, and releases the SDK. */
    suspend fun stop() = lifecycle.withLock {
        withContext(dispatcher) {
            if (_status.value == CollectorStatus.Stopped) return@withContext
            settingsJob?.cancel()
            settingsJob = null
            heartbeat.stop()
            accepting = false
            try {
                val closed = intervals.closeAll(clock.now(), ClosedBy.STOP)
                buffer.add(closed)
                followTrip(closed.filter { it.featureKey == FeatureKey.DRIVING })
                snapshot(SnapshotReason.COLLECTOR_STOP)
                buffer.flush()
                appState.setLastAlive(clock.now())
            } catch (e: Exception) {
                Log.e(TAG, "Saving on stop failed", e)
            }
            shutDown()
            _status.value = CollectorStatus.Stopped
        }
    }

    /** Writes buffered rows now, for example when the app comes to the foreground. */
    suspend fun flush() = withContext(dispatcher) {
        try {
            buffer.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Flush failed", e)
        }
    }

    /**
     * Stops the sources of categories the driver switched off, closing their open intervals,
     * and starts the sources of categories switched back on.
     */
    private suspend fun applyCategories(disabled: Set<FeatureCategory>) = lifecycle.withLock {
        withContext(dispatcher) {
            if (disabled == disabledCategories || _status.value !is CollectorStatus.Running) return@withContext
            val previous = disabledCategories
            disabledCategories = disabled
            val now = clock.now()
            // Only sources whose category actually changed; unsupported ones stay untouched.
            for (source in sources) {
                val category = source.featureKey.category
                if (category == FeatureCategory.SYSTEM || (category in previous) == (category in disabled)) continue
                if (category in disabled) {
                    if (startedSources.remove(source)) {
                        source.stop()
                        buffer.add(intervals.closeFeature(source.featureKey, now, ClosedBy.STOP))
                        counters.forget(source.featureKey)
                        Log.i(TAG, "${source.featureKey} switched off")
                    }
                } else {
                    capabilities[source.featureKey] = startSource(source, enabled = true)
                    Log.i(TAG, "${source.featureKey} switched on: supported=${capabilities.getValue(source.featureKey).supported}")
                }
            }
            _status.value = CollectorStatus.Running(capabilities.toMap(), supportedMetrics)
        }
    }

    private fun isEnabled(source: FeatureSource) =
        source.featureKey.category == FeatureCategory.SYSTEM || source.featureKey.category !in disabledCategories

    private suspend fun startSource(source: FeatureSource, enabled: Boolean): CapabilityResult {
        val capability = try {
            source.checkCapability()
        } catch (e: SdkError) {
            CapabilityResult.failed(e)
        }
        if (!capability.supported || !enabled) return capability

        // Read before registering, so a listener update can't be overwritten by an older read.
        for (reading in source.readInitialState()) record(source.featureKey, reading, clock.now(), live = false)
        return try {
            source.start(listener)
            startedSources += source
            capability
        } catch (e: SdkError) {
            source.stop()
            CapabilityResult.failed(e)
        }
    }

    private suspend fun record(featureKey: FeatureKey, reading: ZoneLevel, timestamp: Long, live: Boolean) {
        if (!accepting) return
        try {
            when (featureKey.kind) {
                FeatureKind.INTERVAL -> {
                    val changed = intervals.onLevel(featureKey, reading.zone, reading.level, timestamp, tripTracker.current?.id)
                    buffer.add(changed)
                    if (featureKey == FeatureKey.DRIVING) followTrip(changed)
                }
                FeatureKind.COUNT -> counters.onLevel(featureKey, reading.zone, reading.level, timestamp, live)
                    ?.let { buffer.add(it.copy(tripId = tripTracker.current?.id)) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Saving $featureKey failed", e)
        }
    }

    private suspend fun followTrip(drivingChanges: List<Interval>) = tripLock.withLock {
        for (interval in drivingChanges) {
            val endTs = interval.endTs
            val change = if (endTs == null) tripTracker.onDrivingStarted(interval.startTs) else tripTracker.onDrivingStopped(endTs)
            when (change) {
                is TripTracker.Change.Started -> saveTrip(change.trip.copy(start = snapshot(SnapshotReason.TRIP_START)))
                is TripTracker.Change.Resumed -> saveTrip(change.trip)
                is TripTracker.Change.Ended -> saveTrip(change.trip.copy(end = snapshot(SnapshotReason.TRIP_END)))
                null -> Unit
            }
        }
    }

    private suspend fun saveTrip(trip: Trip) {
        tripTracker.update(trip)
        trips.saveTrip(trip)
    }

    /** Reads the vehicle once and stores the reading for the recording rate. */
    private suspend fun snapshot(reason: SnapshotReason): VehicleMetrics {
        if (supportedMetrics.isEmpty()) return VehicleMetrics()
        val reading = metrics.read()
        if (!reading.isEmpty) trips.saveSnapshot(VehicleSnapshot(clock.now(), reason, reading))
        return reading
    }

    /**
     * Saves capabilities on first run, after an app update, and whenever a complete check
     * finds something different from what is stored. Only definitive results are saved: a
     * check that failed for any other reason (no answer, a missing permission, an SDK
     * error) says nothing about the vehicle. Overwriting on difference also repairs a
     * snapshot saved wrongly by an earlier build.
     */
    private suspend fun saveCapabilitySnapshot(capabilities: Map<FeatureKey, CapabilityResult>) {
        if (capabilities.isEmpty()) return
        val failed = capabilities.filterValues { !it.isDefinitive }
        if (failed.isNotEmpty()) {
            Log.w(TAG, "Capability snapshot not saved: ${failed.map { (key, result) -> "$key=${result.error}" }}")
            return
        }
        val stored = capabilitySnapshot.load()
        val unchanged = stored.keys == capabilities.keys && capabilities.all { (key, result) -> stored[key]?.sameAs(result) == true }
        if (unchanged && capabilitySnapshot.storedVersion() == appVersionCode) return
        capabilitySnapshot.replace(capabilities, appVersionCode, clock.now())
    }

    /** Saves first, so lastAlive never claims rows that were still only in memory. */
    private suspend fun beat() {
        try {
            buffer.flush()
            appState.setLastAlive(clock.now())
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed", e)
        }
    }

    private suspend fun shutDown() {
        accepting = false
        startedSources.forEach { it.stop() }
        startedSources.clear()
        session.release()
    }

    private companion object {
        const val TAG = "CollectorEngine"
        const val HEARTBEAT_MS = 60_000L

        // A blinking turn signal may report off between blinks. TODO(SDK) 11: confirm.
        const val TURN_SIGNAL_MERGE_MS = 1_500L
    }
}
