package com.garam.hackathon.di

import android.content.Context
import com.garam.hackathon.BuildConfig
import com.garam.hackathon.aggregate.RecordingRateQuery
import com.garam.hackathon.aggregate.ReportLoader
import com.garam.hackathon.collector.Clock
import com.garam.hackathon.collector.CollectorEngine
import com.garam.hackathon.data.DataRetention
import com.garam.hackathon.data.db.CarMeterDatabase
import com.garam.hackathon.data.repo.AppStateRepository
import com.garam.hackathon.data.repo.CapabilityRepository
import com.garam.hackathon.data.repo.RoomAppStateRepository
import com.garam.hackathon.data.repo.RoomCapabilityRepository
import com.garam.hackathon.data.repo.RoomRetentionRepository
import com.garam.hackathon.data.repo.RoomSettingsRepository
import com.garam.hackathon.data.repo.RoomSummaryRepository
import com.garam.hackathon.data.repo.RoomTripRepository
import com.garam.hackathon.data.repo.RoomUsageRepository
import com.garam.hackathon.data.repo.TripRepository
import com.garam.hackathon.data.repo.SettingsRepository
import com.garam.hackathon.data.repo.UsageRepository
import com.garam.hackathon.vehicle.SdkVehicleMetricsReader
import com.garam.hackathon.vehicle.VehicleGateway
import com.garam.hackathon.vehicle.driving.DrivingStateSource
import com.garam.hackathon.vehicle.sources.AmbientLightSource
import com.garam.hackathon.vehicle.sources.CruiseSource
import com.garam.hackathon.vehicle.sources.DoorSource
import com.garam.hackathon.vehicle.sources.DrivingModeSource
import com.garam.hackathon.vehicle.sources.HeadlightSource
import com.garam.hackathon.vehicle.sources.HvacDefrosterSource
import com.garam.hackathon.vehicle.sources.HvacFanSpeedSource
import com.garam.hackathon.vehicle.sources.HvacSwitchSource
import com.garam.hackathon.vehicle.sources.HvacTemperatureSource
import com.garam.hackathon.vehicle.sources.SeatHeatSource
import com.garam.hackathon.vehicle.sources.SeatVentilationSource
import com.garam.hackathon.vehicle.sources.SideMirrorHeatSource
import com.garam.hackathon.vehicle.sources.SteeringWheelHeatSource
import com.garam.hackathon.vehicle.sources.TurnSignalSource
import com.garam.hackathon.vehicle.sources.WindowSource
import com.garam.hackathon.vehicle.sources.WiperSource
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

    val dataRetention by lazy { DataRetention(RoomRetentionRepository(database.eraseDao(), database.appStateDao()), clock) }

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
