package com.garam.hackathon.vehicle.sources

import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.Levels
import com.garam.hackathon.vehicle.VehicleGateway
import com.garam.hackathon.vehicle.Zone

/** Time spent in each drive mode (normal, eco, sport). */
class DrivingModeSource(gateway: VehicleGateway) : ZonedSource<Unit>(gateway) {
    override val featureKey = FeatureKey.DRIVING_MODE

    private val drivingMode get() = gateway.vehicle.getDrivingMode()

    override fun zoneOf(area: Unit) = Zone.GLOBAL

    override suspend fun queryCapability(): ZonedCapability<Unit> = gateway.await { onComplete, onFailed ->
        drivingMode.checkDrivingModeStateCapabilities(
            onComplete = { config: CapabilityConfig -> onComplete(ZonedCapability(config.accessType, mapOf(Unit to null))) },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: Unit): Int? = gateway.await { onComplete, onFailed ->
        drivingMode.getCurrentDrivingModeState(onComplete = { mode: DrivingModeType? -> onComplete(Levels.of(mode)) }, onFailed = onFailed)
    }

    override fun register(area: Unit, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : DrivingModeStateListener {
            override fun onDrivingModeStateUpdated(state: DrivingModeType?) = report(Levels.of(state))
            override fun onFailed(e: Exception) = fail(e)
        }
        drivingMode.registerDrivingModeState(listener)
        return { drivingMode.unregisterDrivingModeState(listener) }
    }
}

/** Time cruise control is actively controlling speed (`CruiseControl.ACTIVATED`). */
class CruiseSource(gateway: VehicleGateway) : ZonedSource<Unit>(gateway) {
    override val featureKey = FeatureKey.CRUISE

    private val drivingMode get() = gateway.vehicle.getDrivingMode()

    override fun zoneOf(area: Unit) = Zone.GLOBAL

    override suspend fun queryCapability(): ZonedCapability<Unit> = gateway.await { onComplete, onFailed ->
        drivingMode.checkCruiseControlStateCapabilities(
            onComplete = { config: CapabilityConfig -> onComplete(ZonedCapability(config.accessType, mapOf(Unit to null))) },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: Unit): Int? = gateway.await { onComplete, onFailed ->
        drivingMode.getCruiseControlState(onComplete = { state: CruiseControl? -> onComplete(Levels.of(state)) }, onFailed = onFailed)
    }

    override fun register(area: Unit, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : CruiseControlStateListener {
            override fun onCruiseControlStateUpdated(state: CruiseControl?) = report(Levels.of(state))
            override fun onFailed(e: Exception) = fail(e)
        }
        drivingMode.registerCruiseControlState(listener)
        return { drivingMode.unregisterCruiseControlState(listener) }
    }
}
