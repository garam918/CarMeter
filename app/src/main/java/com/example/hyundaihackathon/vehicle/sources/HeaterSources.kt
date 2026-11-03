package com.example.hyundaihackathon.vehicle.sources

import ai.pleos.playground.vehicle.api.model.area.MirrorArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.HvacSideMirrorHeatStateListener
import ai.pleos.playground.vehicle.listener.HvacSteeringWheelHeatStateListener
import com.example.hyundaihackathon.vehicle.FeatureKey
import com.example.hyundaihackathon.vehicle.VehicleGateway
import com.example.hyundaihackathon.vehicle.Zone

/** Steering wheel heating level. The wheel has no area, so the zone is [Zone.GLOBAL]. */
class SteeringWheelHeatSource(gateway: VehicleGateway) : ZonedSource<Unit>(gateway) {
    override val featureKey = FeatureKey.STEERING_WHEEL_HEAT

    private val wheel get() = gateway.vehicle.getSteeringWheel()

    override fun zoneOf(area: Unit) = Zone.GLOBAL

    override suspend fun queryCapability(): ZonedCapability<Unit> = gateway.await { onComplete, onFailed ->
        wheel.checkHvacSteeringWheelHeatCapabilities(
            onComplete = { config: CapabilityConfig, bounds: AreaBounds<Int> ->
                onComplete(ZonedCapability(config.accessType, mapOf(Unit to bounds.minValue..bounds.maxValue)))
            },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: Unit): Int? = gateway.await { onComplete, onFailed ->
        wheel.getHvacSteeringWheelHeatState(onComplete = onComplete, onFailed = onFailed)
    }

    override fun register(area: Unit, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : HvacSteeringWheelHeatStateListener {
            override fun onHvacSteeringWheelHeatStateUpdated(state: Int?) = report(state)
            override fun onFailed(e: Exception) = fail(e)
        }
        wheel.registerHvacSteeringWheelHeatState(listener)
        return { wheel.unregisterHvacSteeringWheelHeatState(listener) }
    }
}

class SideMirrorHeatSource(gateway: VehicleGateway) : ZonedSource<MirrorArea>(gateway) {
    override val featureKey = FeatureKey.SIDE_MIRROR_HEAT

    private val mirror get() = gateway.vehicle.getSideMirror()

    override fun zoneOf(area: MirrorArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<MirrorArea> = gateway.await { onComplete, onFailed ->
        mirror.checkHvacSideMirrorHeatCapabilities(
            onComplete = { config: CapabilityConfig, bounds: Map<MirrorArea, AreaBounds<Int>> ->
                onComplete(ZonedCapability(config.accessType, bounds.mapValues { (_, b) -> b.minValue..b.maxValue }))
            },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: MirrorArea): Int? = gateway.await { onComplete, onFailed ->
        mirror.getHvacSideMirrorHeatState(area, onComplete = onComplete, onFailed = onFailed)
    }

    override fun register(area: MirrorArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : HvacSideMirrorHeatStateListener {
            override fun onHvacSideMirrorHeatStateUpdated(state: Int?) = report(state)
            override fun onFailed(e: Exception) = fail(e)
        }
        mirror.registerHvacSideMirrorHeatState(area, listener)
        return { mirror.unregisterHvacSideMirrorHeatState(area, listener) }
    }
}
