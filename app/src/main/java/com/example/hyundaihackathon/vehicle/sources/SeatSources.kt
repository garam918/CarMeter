package com.example.hyundaihackathon.vehicle.sources

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacSeatVentilationListener
import com.example.hyundaihackathon.vehicle.FeatureKey
import com.example.hyundaihackathon.vehicle.VehicleGateway
import com.example.hyundaihackathon.vehicle.Zone

/**
 * Seat heating level per seat zone (`HvacSeatTemperature`). On the emulator the front
 * seats report -3..3 and ventilation arrives as negative levels, so the raw level is
 * stored and the report splits it by sign (docs/sdk-notes.md 3.2).
 */
class SeatHeatSource(gateway: VehicleGateway) : ZonedSource<SeatArea>(gateway) {
    override val featureKey = FeatureKey.SEAT_HEAT

    private val seat get() = gateway.vehicle.getSeat()

    override fun zoneOf(area: SeatArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<SeatArea> = gateway.await { onComplete, onFailed ->
        seat.checkSeatTemperatureCapabilities(
            onComplete = { config: CapabilityConfig, bounds: Map<SeatArea, AreaBounds<Int>> ->
                onComplete(ZonedCapability(config.accessType, bounds.mapValues { (_, b) -> b.minValue..b.maxValue }))
            },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: SeatArea): Int? = gateway.await { onComplete, onFailed ->
        seat.getHvacSeatTemperature(area, onComplete = onComplete, onFailed = onFailed)
    }

    override fun register(area: SeatArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : HvacSeatTemperatureListener {
            override fun onHvacSeatTemperatureUpdated(temperature: Int?) = report(temperature)
            override fun onFailed(e: Exception) = fail(e)
        }
        seat.registerHvacSeatTemperature(area, listener)
        return { seat.unregisterHvacSeatTemperature(area, listener) }
    }
}

/** Seat ventilation level per seat zone, for vehicles that report it as its own property. */
class SeatVentilationSource(gateway: VehicleGateway) : ZonedSource<SeatArea>(gateway) {
    override val featureKey = FeatureKey.SEAT_VENTILATION

    private val seat get() = gateway.vehicle.getSeat()

    override fun zoneOf(area: SeatArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<SeatArea> = gateway.await { onComplete, onFailed ->
        seat.checkSeatVentilationCapabilities(
            onComplete = { config: CapabilityConfig, bounds: Map<SeatArea, AreaBounds<Int>> ->
                onComplete(ZonedCapability(config.accessType, bounds.mapValues { (_, b) -> b.minValue..b.maxValue }))
            },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: SeatArea): Int? = gateway.await { onComplete, onFailed ->
        seat.getHvacSeatVentilationState(area, onComplete = onComplete, onFailed = onFailed)
    }

    override fun register(area: SeatArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : HvacSeatVentilationListener {
            override fun onHvacSeatVentilationUpdated(ventilation: Int?) = report(ventilation)
            override fun onFailed(e: Exception) = fail(e)
        }
        seat.registerHvacSeatVentilation(area, listener)
        return { seat.unregisterHvacSeatVentilation(area, listener) }
    }
}
