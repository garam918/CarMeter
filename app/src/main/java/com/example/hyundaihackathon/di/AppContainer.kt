package com.example.hyundaihackathon.di

import android.content.Context
import com.example.hyundaihackathon.BuildConfig
import com.example.hyundaihackathon.aggregate.RecordingRateQuery
import com.example.hyundaihackathon.aggregate.ReportLoader
import com.example.hyundaihackathon.collector.Clock
import com.example.hyundaihackathon.collector.CollectorEngine
import com.example.hyundaihackathon.data.db.CarMeterDatabase
import com.example.hyundaihackathon.data.repo.AppStateRepository
import com.example.hyundaihackathon.data.repo.CapabilityRepository
import com.example.hyundaihackathon.data.repo.RoomAppStateRepository
import com.example.hyundaihackathon.data.repo.RoomCapabilityRepository
import com.example.hyundaihackathon.data.repo.RoomSettingsRepository
import com.example.hyundaihackathon.data.repo.RoomSummaryRepository
import com.example.hyundaihackathon.data.repo.RoomTripRepository
import com.example.hyundaihackathon.data.repo.RoomUsageRepository
import com.example.hyundaihackathon.data.repo.TripRepository
import com.example.hyundaihackathon.data.repo.SettingsRepository
import com.example.hyundaihackathon.data.repo.UsageRepository
import com.example.hyundaihackathon.vehicle.SdkVehicleMetricsReader
import com.example.hyundaihackathon.vehicle.VehicleGateway
import com.example.hyundaihackathon.vehicle.driving.DrivingStateSource
import com.example.hyundaihackathon.vehicle.sources.AmbientLightSource
import com.example.hyundaihackathon.vehicle.sources.CruiseSource
import com.example.hyundaihackathon.vehicle.sources.DoorSource
import com.example.hyundaihackathon.vehicle.sources.DrivingModeSource
import com.example.hyundaihackathon.vehicle.sources.HeadlightSource
import com.example.hyundaihackathon.vehicle.sources.HvacDefrosterSource
import com.example.hyundaihackathon.vehicle.sources.HvacFanSpeedSource
import com.example.hyundaihackathon.vehicle.sources.HvacSwitchSource
import com.example.hyundaihackathon.vehicle.sources.HvacTemperatureSource
import com.example.hyundaihackathon.vehicle.sources.SeatHeatSource
import com.example.hyundaihackathon.vehicle.sources.SeatVentilationSource
import com.example.hyundaihackathon.vehicle.sources.SideMirrorHeatSource
import com.example.hyundaihackathon.vehicle.sources.SteeringWheelHeatSource
import com.example.hyundaihackathon.vehicle.sources.TurnSignalSource
import com.example.hyundaihackathon.vehicle.sources.WindowSource
import com.example.hyundaihackathon.vehicle.sources.WiperSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection. Everything is created on first use. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = Clock.SYSTEM

    /** Lives as long as the process. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { CarMeterDatabase.create(appContext) }

    val usageRepository: UsageRepository by lazy { RoomUsageRepository(database.intervalDao()) }

    val tripRepository: TripRepository by lazy { RoomTripRepository(database.tripDao()) }

    val capabilityRepository: CapabilityRepository by lazy { RoomCapabilityRepository(database.capabilityDao()) }

    private val appStateRepository: AppStateRepository by lazy { RoomAppStateRepository(database.appStateDao()) }

    val settingsRepository: SettingsRepository by lazy { RoomSettingsRepository(database.appStateDao()) }

    /** Deletes every usage record in one transaction; settings and the capability snapshot stay. */
    suspend fun eraseUsageData() = database.eraseDao().eraseUsageData()

    private val vehicleGateway by lazy { VehicleGateway(appContext) }

    val recordingRateQuery by lazy { RecordingRateQuery(usageRepository, tripRepository) }

    val reportLoader by lazy {
        ReportLoader(
            usage = usageRepository,
            summaries = RoomSummaryRepository(database.summaryDao()),
            capabilities = capabilityRepository,
            settings = settingsRepository,
            recordingRate = recordingRateQuery,
            clock = clock,
        )
    }

    val collectorEngine by lazy {
        val gateway = vehicleGateway
        CollectorEngine(
            session = gateway,
            sources = listOf(
                // Driving first: other features tag their intervals with the trip it starts.
                DrivingStateSource(appContext),
                HvacSwitchSource.power(gateway),
                HvacSwitchSource.ac(gateway),
                HvacTemperatureSource(gateway),
                HvacFanSpeedSource(gateway),
                HvacSwitchSource.recirculation(gateway),
                HvacDefrosterSource(gateway),
                HvacSwitchSource.auto(gateway),
                HvacSwitchSource.maxAc(gateway),
                HvacSwitchSource.maxDefrost(gateway),
                HvacSwitchSource.dual(gateway),
                HvacSwitchSource.airCleaner(gateway),
                SteeringWheelHeatSource(gateway),
                SideMirrorHeatSource(gateway),
                SeatHeatSource(gateway),
                SeatVentilationSource(gateway),
                HeadlightSource.lowBeam(gateway),
                HeadlightSource.highBeam(gateway),
                AmbientLightSource(gateway),
                TurnSignalSource(gateway),
                WiperSource(gateway),
                DrivingModeSource(gateway),
                CruiseSource(gateway),
                WindowSource(gateway),
                DoorSource(gateway),
            ),
            metrics = SdkVehicleMetricsReader(gateway),
            usage = usageRepository,
            trips = tripRepository,
            capabilitySnapshot = capabilityRepository,
            appState = appStateRepository,
            settings = settingsRepository,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            clock = clock,
            scope = appScope,
        )
    }
}
