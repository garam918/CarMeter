package com.example.hyundaihackathon.vehicle.sources

import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.listener.LightStateListener
import com.example.hyundaihackathon.vehicle.FeatureKey
import com.example.hyundaihackathon.vehicle.Levels
import com.example.hyundaihackathon.vehicle.VehicleGateway
import com.example.hyundaihackathon.vehicle.Zone

/**
 * An exterior light that is either on or off, for the whole vehicle. The low and high
 * beams share this shape; the factories bind their SDK methods.
 */
class HeadlightSource private constructor(
    gateway: VehicleGateway,
    override val featureKey: FeatureKey,
    private val check: Light.(onComplete: (CapabilityConfig, List<LightState>) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    private val read: Light.(onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    private val registerListener: Light.(LightStateListener) -> Unit,
    private val unregisterListener: Light.(LightStateListener) -> Unit,
) : ZonedSource<Unit>(gateway) {
    private val light get() = gateway.vehicle.getLight()

    override fun zoneOf(area: Unit) = Zone.GLOBAL

    override suspend fun queryCapability(): ZonedCapability<Unit> = gateway.await { onComplete, onFailed ->
        light.check({ config, _ -> onComplete(ZonedCapability(config.accessType, mapOf(Unit to null))) }, onFailed)
    }

    override suspend fun readLevel(area: Unit): Int? = gateway.await { onComplete, onFailed ->
        light.read({ state -> onComplete(Levels.of(state)) }, onFailed)
    }

    override fun register(area: Unit, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) = report(Levels.of(state))
            override fun onFailed(e: Exception) = fail(e)
        }
        light.registerListener(listener)
        return { light.unregisterListener(listener) }
    }

    companion object {
        fun lowBeam(gateway: VehicleGateway) = HeadlightSource(
            gateway, FeatureKey.LOW_BEAM,
            check = { ok, fail -> checkLowBeamLightStateCapabilities(onComplete = ok, onFailed = fail) },
            read = { ok, fail -> getCurrentLowBeamLightState(onComplete = ok, onFailed = fail) },
            registerListener = { registerLowBeamLightState(it) },
            unregisterListener = { unregisterLowBeamLightState(it) },
        )

        fun highBeam(gateway: VehicleGateway) = HeadlightSource(
            gateway, FeatureKey.HIGH_BEAM,
            check = { ok, fail -> checkHighBeamLightStateCapabilities(onComplete = ok, onFailed = fail) },
            read = { ok, fail -> getCurrentHighBeamLightState(onComplete = ok, onFailed = fail) },
            registerListener = { registerHighBeamLightState(it) },
            unregisterListener = { unregisterHighBeamLightState(it) },
        )
    }
}

class AmbientLightSource(gateway: VehicleGateway) : ZonedSource<SeatArea>(gateway) {
    override val featureKey = FeatureKey.AMBIENT_LIGHT

    private val light get() = gateway.vehicle.getLight()

    override fun zoneOf(area: SeatArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<SeatArea> = gateway.await { onComplete, onFailed ->
        light.checkAmbientLightStateCapabilities(
            onComplete = { config: CapabilityConfig, states: Map<SeatArea, List<LightState>> ->
                onComplete(ZonedCapability(config.accessType, states.mapValues { null }))
            },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: SeatArea): Int? = gateway.await { onComplete, onFailed ->
        light.getAmbientLightState(area, onComplete = { state: LightState? -> onComplete(Levels.of(state)) }, onFailed = onFailed)
    }

    override fun register(area: SeatArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) = report(Levels.of(state))
            override fun onFailed(e: Exception) = fail(e)
        }
        light.registerAmbientLightStateListener(area, listener)
        return { light.unregisterAmbientLightStateListener(area, listener) }
    }
}
