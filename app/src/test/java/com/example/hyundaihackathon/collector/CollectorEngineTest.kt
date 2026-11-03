package com.example.hyundaihackathon.collector

import com.example.hyundaihackathon.data.ClosedBy
import com.example.hyundaihackathon.data.Interval
import com.example.hyundaihackathon.data.SnapshotReason
import com.example.hyundaihackathon.data.VehicleMetrics
import com.example.hyundaihackathon.fakes.FakeClock
import com.example.hyundaihackathon.fakes.FakeFeatureSource
import com.example.hyundaihackathon.fakes.FakeMetricsReader
import com.example.hyundaihackathon.fakes.FakeVehicleSession
import com.example.hyundaihackathon.fakes.InMemoryAppStateRepository
import com.example.hyundaihackathon.fakes.InMemoryCapabilityRepository
import com.example.hyundaihackathon.fakes.InMemorySettingsRepository
import com.example.hyundaihackathon.fakes.InMemoryTripRepository
import com.example.hyundaihackathon.fakes.InMemoryUsageRepository
import com.example.hyundaihackathon.vehicle.CapabilityResult
import com.example.hyundaihackathon.data.repo.CollectorSettings
import com.example.hyundaihackathon.vehicle.FeatureCategory
import com.example.hyundaihackathon.vehicle.FeatureKey.DOOR_OPEN
import com.example.hyundaihackathon.vehicle.FeatureKey.DRIVING
import com.example.hyundaihackathon.vehicle.FeatureKey.HVAC_AC
import com.example.hyundaihackathon.vehicle.FeatureKey.SEAT_HEAT
import com.example.hyundaihackathon.vehicle.FeatureSource
import com.example.hyundaihackathon.vehicle.SdkError
import com.example.hyundaihackathon.vehicle.VehicleMetric
import com.example.hyundaihackathon.vehicle.ZoneLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectorEngineTest {
    private val clock = FakeClock(1_000)
    private val session = FakeVehicleSession()
    private val usage = InMemoryUsageRepository()
    private val trips = InMemoryTripRepository()
    private val capabilities = InMemoryCapabilityRepository()
    private val appState = InMemoryAppStateRepository()
    private val metrics = FakeMetricsReader()
    private val settings = InMemorySettingsRepository()

    private fun TestScope.engine(
        sources: List<FeatureSource>,
        usage: InMemoryUsageRepository = this@CollectorEngineTest.usage,
        appState: InMemoryAppStateRepository = this@CollectorEngineTest.appState,
        metrics: FakeMetricsReader = this@CollectorEngineTest.metrics,
    ) = CollectorEngine(
        session = session,
        sources = sources,
        metrics = metrics,
        usage = usage,
        trips = trips,
        capabilitySnapshot = capabilities,
        appState = appState,
        settings = settings,
        appVersionCode = 2,
        clock = clock,
        scope = backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler),
        heartbeatMs = 60_000,
    )

    @Test
    fun `start records the initial state and then follows the listener`() = runTest {
        val seat = FakeFeatureSource(SEAT_HEAT, initialState = listOf(ZoneLevel(1, 2)))
        val engine = engine(listOf(seat))

        assertTrue(engine.start() is CollectorStatus.Running)
        assertEquals(1, session.connectCount)
        assertTrue(seat.isStarted)

        clock.time = 5_000
        seat.emit(zone = 1, level = 0)
        runCurrent()
        engine.flush()

        val row = usage.all.single()
        assertEquals(1_000L, row.startTs)
        assertEquals(5_000L, row.endTs)
        assertEquals(ClosedBy.EVENT, row.closedBy)
    }

    @Test
    fun `unsupported sources are reported and never started`() = runTest {
        val seat = FakeFeatureSource(SEAT_HEAT)
        val ac = FakeFeatureSource(
            HVAC_AC,
            capability = CapabilityResult(supported = false, zones = emptyList(), error = "ERROR_NOT_SUPPORTED_PROPERTY"),
        )

        val status = engine(listOf(seat, ac)).start() as CollectorStatus.Running

        assertTrue(seat.isStarted)
        assertFalse(ac.isStarted)
        assertEquals("ERROR_NOT_SUPPORTED_PROPERTY", status.capabilities.getValue(HVAC_AC).error)
    }

    @Test
    fun `a source whose registration fails is stopped and reported unsupported`() = runTest {
        val seat = FakeFeatureSource(SEAT_HEAT, startError = SdkError.PermissionDenied("ERROR_SECURITY_PERMISSION", null))

        val status = engine(listOf(seat)).start() as CollectorStatus.Running

        val result = status.capabilities.getValue(SEAT_HEAT)
        assertFalse(result.supported)
        assertEquals("ERROR_SECURITY_PERMISSION", result.error)
        assertEquals(1, seat.stopCount)
    }

    @Test
    fun `an unreachable vehicle service fails the start and releases the SDK`() = runTest {
        val seat = FakeFeatureSource(SEAT_HEAT, capability = CapabilityResult.failed(SdkError.Timeout()))

        val status = engine(listOf(seat)).start()

        assertTrue(status is CollectorStatus.Failed)
        assertEquals(1, session.releaseCount)
        assertNull("an incomplete check must not be saved", capabilities.saved)
    }

    @Test
    fun `stop closes open intervals, saves them and releases the SDK`() = runTest {
        val seat = FakeFeatureSource(SEAT_HEAT, initialState = listOf(ZoneLevel(1, 2)))
        val engine = engine(listOf(seat))
        engine.start()

        clock.time = 9_000
        engine.stop()

        val row = usage.all.single()
        assertEquals(9_000L, row.endTs)
        assertEquals(ClosedBy.STOP, row.closedBy)
        assertEquals(1, seat.stopCount)
        assertEquals(1, session.releaseCount)
        assertEquals(9_000L, appState.lastAlive())
        assertEquals(CollectorStatus.Stopped, engine.status.value)
    }

    @Test
    fun `after a process restart, intervals left open close at lastAlive`() = runTest {
        val leftOpen = Interval("old", SEAT_HEAT, 1, 2, startTs = 1_000)
        val usage = InMemoryUsageRepository(listOf(leftOpen))
        val seat = FakeFeatureSource(SEAT_HEAT, initialState = listOf(ZoneLevel(1, 2)))
        val engine = engine(listOf(seat), usage, InMemoryAppStateRepository(lastAlive = 61_000))

        clock.time = 300_000
        engine.start()
        engine.flush()

        val old = usage.all.single { it.id == "old" }
        assertEquals(61_000L, old.endTs)
        assertEquals(ClosedBy.HEARTBEAT, old.closedBy)
        val reopened = usage.all.single { it.id != "old" }
        assertEquals(300_000L, reopened.startTs)
        assertNull(reopened.endTs)
    }

    @Test
    fun `the heartbeat saves buffered rows, then lastAlive`() = runTest {
        val seat = FakeFeatureSource(SEAT_HEAT, initialState = listOf(ZoneLevel(1, 2)))
        engine(listOf(seat)).start()
        assertTrue(usage.all.isEmpty())

        clock.time = 61_000
        advanceTimeBy(60_001)

        assertEquals(1, usage.all.size)
        assertEquals(61_000L, appState.lastAlive())
    }

    @Test
    fun `driving starts and ends a trip with one vehicle reading at each end`() = runTest {
        val metrics = FakeMetricsReader(setOf(VehicleMetric.ODOMETER), VehicleMetrics(odometerKm = 100.0))
        val driving = FakeFeatureSource(DRIVING, initialState = listOf(ZoneLevel(0, 0)))
        val seat = FakeFeatureSource(SEAT_HEAT)
        val engine = engine(listOf(driving, seat), metrics = metrics)
        engine.start()

        clock.time = 10_000
        driving.emit(zone = 0, level = 1)
        runCurrent()
        clock.time = 12_000
        seat.emit(zone = 1, level = 3)
        runCurrent()
        metrics.metrics = VehicleMetrics(odometerKm = 112.5)
        clock.time = 70_000
        driving.emit(zone = 0, level = 0)
        runCurrent()
        engine.flush()

        val trip = trips.trips.single()
        assertEquals(10_000L, trip.startTs)
        assertEquals(70_000L, trip.endTs)
        assertEquals(100.0, trip.start.odometerKm!!, 0.0)
        assertEquals(112.5, trip.end.odometerKm!!, 0.0)
        assertEquals(trip.id, usage.all.single { it.featureKey == SEAT_HEAT }.tripId)
        assertEquals(
            listOf(SnapshotReason.COLLECTOR_START, SnapshotReason.TRIP_START, SnapshotReason.TRIP_END),
            trips.snapshots.map { it.reason },
        )
    }

    @Test
    fun `stopping while driving ends the trip`() = runTest {
        val metrics = FakeMetricsReader(setOf(VehicleMetric.ODOMETER), VehicleMetrics(odometerKm = 5.0))
        val driving = FakeFeatureSource(DRIVING, initialState = listOf(ZoneLevel(0, 1)))
        val engine = engine(listOf(driving), metrics = metrics)
        engine.start()

        clock.time = 40_000
        engine.stop()

        assertEquals(40_000L, trips.trips.single().endTs)
        assertEquals(SnapshotReason.COLLECTOR_STOP, trips.snapshots.last().reason)
    }

    @Test
    fun `door openings are counted from listener updates only`() = runTest {
        val door = FakeFeatureSource(DOOR_OPEN, initialState = listOf(ZoneLevel(1, 1)))
        val engine = engine(listOf(door))
        engine.start()

        door.emit(zone = 1, level = 0)
        door.emit(zone = 1, level = 1)
        door.emit(zone = 1, level = 0)
        door.emit(zone = 1, level = 1)
        runCurrent()
        engine.flush()

        assertEquals(2, usage.counters.size)
        assertTrue("counted features make no intervals", usage.all.isEmpty())
    }

    @Test
    fun `a permission error is not saved as a vehicle capability`() = runTest {
        val seat = FakeFeatureSource(SEAT_HEAT)
        val ac = FakeFeatureSource(HVAC_AC, capability = CapabilityResult.failed(SdkError.PermissionDenied(SdkError.PERMISSION_DENIED, null)))

        engine(listOf(seat, ac)).start()

        assertNull(capabilities.saved)
    }

    @Test
    fun `capabilities are saved once per app version`() = runTest {
        val first = engine(listOf(FakeFeatureSource(SEAT_HEAT)))
        first.start()
        first.stop()
        engine(listOf(FakeFeatureSource(SEAT_HEAT))).start()

        assertEquals(1, capabilities.replaceCount)
        assertEquals(2L, capabilities.version)
        assertTrue(capabilities.saved!!.getValue(SEAT_HEAT).supported)
    }

    @Test
    fun `without consent the engine does not start`() = runTest {
        val noConsent = InMemorySettingsRepository(CollectorSettings(consentAt = null))
        val seat = FakeFeatureSource(SEAT_HEAT)
        val engine = CollectorEngine(
            session, listOf(seat), metrics, usage, trips, capabilities, appState, noConsent, 2, clock, backgroundScope,
            StandardTestDispatcher(testScheduler),
        )

        assertEquals(CollectorStatus.Stopped, engine.start())
        assertEquals(0, session.connectCount)
        assertFalse(seat.isStarted)
    }

    @Test
    fun `with recording switched off the engine does not start`() = runTest {
        settings.setRecordingEnabled(false)

        assertEquals(CollectorStatus.Stopped, engine(listOf(FakeFeatureSource(SEAT_HEAT))).start())
        assertEquals(0, session.connectCount)
    }

    @Test
    fun `a switched-off category is checked but not started`() = runTest {
        settings.setCategoryEnabled(FeatureCategory.SEAT, false)
        val seat = FakeFeatureSource(SEAT_HEAT, initialState = listOf(ZoneLevel(1, 2)))
        val ac = FakeFeatureSource(HVAC_AC)

        val status = engine(listOf(seat, ac)).start() as CollectorStatus.Running

        assertFalse(seat.isStarted)
        assertTrue(ac.isStarted)
        assertTrue("its capability is still known", status.capabilities.getValue(SEAT_HEAT).supported)
    }

    @Test
    fun `switching a category off stops its sources and closes their intervals, and on restarts them`() = runTest {
        val seat = FakeFeatureSource(SEAT_HEAT, initialState = listOf(ZoneLevel(1, 2)))
        val ac = FakeFeatureSource(HVAC_AC, initialState = listOf(ZoneLevel(1, 1)))
        val engine = engine(listOf(seat, ac))
        engine.start()
        runCurrent()

        clock.time = 20_000
        settings.setCategoryEnabled(FeatureCategory.SEAT, false)
        runCurrent()
        engine.flush()

        assertFalse(seat.isStarted)
        assertTrue(ac.isStarted)
        val closed = usage.all.single { it.featureKey == SEAT_HEAT }
        assertEquals(20_000L, closed.endTs)
        assertEquals(ClosedBy.STOP, closed.closedBy)
        assertTrue("other features keep running", usage.all.single { it.featureKey == HVAC_AC }.isOpen)

        clock.time = 30_000
        settings.setCategoryEnabled(FeatureCategory.SEAT, true)
        runCurrent()
        engine.flush()

        assertTrue(seat.isStarted)
        assertEquals(30_000L, usage.all.single { it.featureKey == SEAT_HEAT && it.isOpen }.startTs)
    }

    @Test
    fun `changing one category leaves sources of other categories alone`() = runTest {
        val seat = FakeFeatureSource(SEAT_HEAT)
        val unsupportedAc = FakeFeatureSource(HVAC_AC, capability = CapabilityResult(false, emptyList(), "ERROR_NOT_SUPPORTED_PROPERTY"))
        engine(listOf(seat, unsupportedAc)).start()
        runCurrent()

        settings.setCategoryEnabled(FeatureCategory.SEAT, false)
        runCurrent()
        settings.setCategoryEnabled(FeatureCategory.SEAT, true)
        runCurrent()

        assertEquals("checked once at start only", 1, unsupportedAc.checkCount)
        assertEquals(2, seat.checkCount)
    }

    @Test
    fun `an SDK error that is not "not supported" keeps the snapshot from being saved`() = runTest {
        val seat = FakeFeatureSource(SEAT_HEAT)
        val ac = FakeFeatureSource(HVAC_AC, capability = CapabilityResult.failed(SdkError.Other("ERROR_UNDEFINED", null)))

        engine(listOf(seat, ac)).start()

        assertNull(capabilities.saved)
    }

    @Test
    fun `a complete check that differs from the stored snapshot replaces it`() = runTest {
        val wrong = InMemoryCapabilityRepository(mapOf(SEAT_HEAT to CapabilityResult(false, emptyList(), "ERROR_NOT_SUPPORTED_PROPERTY")))
        wrong.version = 2
        val engine = CollectorEngine(
            session, listOf(FakeFeatureSource(SEAT_HEAT)), metrics, usage, trips, wrong, appState, settings, 2, clock,
            backgroundScope, StandardTestDispatcher(testScheduler),
        )

        engine.start()

        assertTrue(wrong.saved!!.getValue(SEAT_HEAT).supported)
        assertEquals(1, wrong.replaceCount)
    }
}

