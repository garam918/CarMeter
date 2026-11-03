package com.garam.hackathon.vehicle.sources

import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import com.garam.hackathon.vehicle.CapabilityResult
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.FeatureListener
import com.garam.hackathon.vehicle.FeatureSource
import com.garam.hackathon.vehicle.Levels
import com.garam.hackathon.vehicle.SdkError
import com.garam.hackathon.vehicle.VehicleGateway
import com.garam.hackathon.vehicle.Zone
import com.garam.hackathon.vehicle.ZoneLevel
import com.garam.hackathon.vehicle.ZoneRange

/**
 * Window opening per window. The level is how far the window is open above its closed
 * position (the capability minimum), so 0 means closed.
 */
class WindowSource(gateway: VehicleGateway) : ZonedSource<WindowArea>(gateway) {
    override val featureKey = FeatureKey.WINDOW_OPEN

    private val window get() = gateway.vehicle.getWindow()
    private var closedPosition: Map<Int, Int> = emptyMap()

    override fun zoneOf(area: WindowArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<WindowArea> {
        val capability: ZonedCapability<WindowArea> = gateway.await { onComplete, onFailed ->
            window.checkWindowPositionCapabilities(
                onComplete = { config: CapabilityConfig, bounds: Map<WindowArea, AreaBounds<Int>> ->
                    onComplete(ZonedCapability(config.accessType, bounds.mapValues { (_, b) -> b.minValue..b.maxValue }))
                },
                onFailed = onFailed,
            )
        }
        closedPosition = capability.areas.entries.associate { (area, bounds) -> zoneOf(area) to (bounds?.first ?: 0) }
        return capability
    }

    override suspend fun readLevel(area: WindowArea): Int? = gateway.await { onComplete, onFailed ->
        window.getCurrentWindowPosition(area, onComplete = { position: Int? -> onComplete(opening(area, position)) }, onFailed = onFailed)
    }

    override fun register(area: WindowArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : WindowPositionListener {
            override fun onWindowPositionUpdated(position: Int?) = report(opening(area, position))
            override fun onFailed(e: Exception) = fail(e)
        }
        window.registerWindowPosition(area, listener)
        return { window.unregisterWindowPosition(area, listener) }
    }

    private fun opening(area: WindowArea, position: Int?): Int? {
        val closed = closedPosition[zoneOf(area)] ?: 0
        return position?.let { (it - closed).coerceAtLeast(0) }
    }
}

/** Door state per door, as 1 (open) or 0 (closed). The engine counts each opening. */
class DoorSource(gateway: VehicleGateway) : ZonedSource<DoorArea>(gateway) {
    override val featureKey = FeatureKey.DOOR_OPEN

    private val door get() = gateway.vehicle.getDoor()
    private var closedPosition: Map<Int, Int> = emptyMap()

    override fun zoneOf(area: DoorArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<DoorArea> {
        val capability: ZonedCapability<DoorArea> = gateway.await { onComplete, onFailed ->
            door.checkDoorPositionCapabilities(
                onComplete = { config: CapabilityConfig, bounds: Map<DoorArea, AreaBounds<Int>> ->
                    onComplete(ZonedCapability(config.accessType, bounds.mapValues { (_, b) -> b.minValue..b.maxValue }))
                },
                onFailed = onFailed,
            )
        }
        closedPosition = capability.areas.entries.associate { (area, bounds) -> zoneOf(area) to (bounds?.first ?: 0) }
        return capability
    }

    override suspend fun readLevel(area: DoorArea): Int? = gateway.await { onComplete, onFailed ->
        door.getCurrentDoorPosition(area, onComplete = { position: Int? -> onComplete(openState(area, position)) }, onFailed = onFailed)
    }

    override fun register(area: DoorArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) = report(openState(area, pos))
            override fun onFailed(e: Exception) = fail(e)
        }
        door.registerDoorPosition(area, listener)
        return { door.unregisterDoorPosition(area, listener) }
    }

    private fun openState(area: DoorArea, position: Int?): Int? {
        val closed = closedPosition[zoneOf(area)] ?: 0
        return position?.let { if (it > closed) Levels.ON else 0 }
    }
}

/**
 * Turn signal use. The SDK reports one value (off, left or right), which this source
 * splits into a left zone and a right zone so each side is counted on its own.
 */
class TurnSignalSource(private val gateway: VehicleGateway) : FeatureSource {
    override val featureKey = FeatureKey.TURN_SIGNAL

    private val turnSignal get() = gateway.vehicle.getTurnSignal()
    private var listener: TurnSignalStateListener? = null

    override suspend fun checkCapability(): CapabilityResult {
        val accessType = try {
            gateway.await<AccessType> { onComplete, onFailed ->
                turnSignal.checkTurnSignalStateCapabilities(
                    onComplete = { config: CapabilityConfig -> onComplete(config.accessType) },
                    onFailed = onFailed,
                )
            }
        } catch (e: SdkError) {
            return CapabilityResult.failed(e)
        }
        val readable = accessType == AccessType.READ || accessType == AccessType.READ_WRITE
        return CapabilityResult(
            supported = readable,
            zones = if (readable) listOf(ZoneRange(Zone.TURN_LEFT), ZoneRange(Zone.TURN_RIGHT)) else emptyList(),
        )
    }

    override suspend fun readInitialState(): List<ZoneLevel> = try {
        split(gateway.await { onComplete, onFailed -> turnSignal.getCurrentTurnSignalState(onComplete = onComplete, onFailed = onFailed) })
    } catch (e: SdkError) {
        emptyList()
    }

    override fun start(listener: FeatureListener) {
        val sdkListener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                split(state).forEach { listener.onLevel(featureKey, it) }
            }

            override fun onFailed(e: Exception) = listener.onError(featureKey, SdkError.from(e))
        }
        try {
            turnSignal.registerTurnSignalState(sdkListener)
        } catch (e: Exception) {
            throw SdkError.from(e)
        }
        this.listener = sdkListener
    }

    override fun stop() {
        val registered = listener ?: return
        try {
            turnSignal.unregisterTurnSignalState(registered)
        } catch (e: Exception) {
            // Nothing to undo: the SDK drops every listener on release anyway.
        }
        listener = null
    }

    private fun split(state: TurnSignalValue?): List<ZoneLevel> {
        if (state == null) return listOf(ZoneLevel(Zone.TURN_LEFT, null), ZoneLevel(Zone.TURN_RIGHT, null))
        return listOf(
            ZoneLevel(Zone.TURN_LEFT, if (state == TurnSignalValue.LEFT) Levels.ON else 0),
            ZoneLevel(Zone.TURN_RIGHT, if (state == TurnSignalValue.RIGHT) Levels.ON else 0),
        )
    }
}
