package com.garam.hackathon.vehicle.sources

import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.capability.HvacTemperatureConfigArray
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacAirCleanerStateListener
import ai.pleos.playground.vehicle.listener.HvacAutoStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacDualStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacMaxAcStateListener
import ai.pleos.playground.vehicle.listener.HvacMaxDefrostStateListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.Levels
import com.garam.hackathon.vehicle.VehicleGateway
import com.garam.hackathon.vehicle.Zone

private typealias Unregister = () -> Unit

/**
 * An on/off HVAC feature per seat zone. Most HVAC switches share this shape and differ
 * only in which SDK methods they call, so each is a factory function below.
 */
class HvacSwitchSource private constructor(
    gateway: VehicleGateway,
    override val featureKey: FeatureKey,
    private val check: HVAC.(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    private val read: HVAC.(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    private val subscribe: HVAC.(area: SeatArea, report: (Boolean?) -> Unit, fail: (Exception) -> Unit) -> Unregister,
) : ZonedSource<SeatArea>(gateway) {
    private val hvac get() = gateway.vehicle.getHVAC()

    override fun zoneOf(area: SeatArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<SeatArea> = gateway.await { onComplete, onFailed ->
        hvac.check({ config, areas -> onComplete(ZonedCapability(config.accessType, areas.associateWith { null })) }, onFailed)
    }

    override suspend fun readLevel(area: SeatArea): Int? = gateway.await { onComplete, onFailed ->
        hvac.read(area, { state -> onComplete(Levels.of(state)) }, onFailed)
    }

    override fun register(area: SeatArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): Unregister =
        hvac.subscribe(area, { state -> report(Levels.of(state)) }, fail)

    companion object {
        fun power(gateway: VehicleGateway) = HvacSwitchSource(
            gateway, FeatureKey.HVAC_POWER,
            check = { ok, fail -> checkHvacPowerCapabilities(onComplete = ok, onFailed = fail) },
            read = { area, ok, fail -> getHvacPowerState(area, onComplete = ok, onFailed = fail) },
            subscribe = { area, report, fail ->
                val listener = object : HvacPowerStateListener {
                    override fun onHvacPowerStateUpdated(state: Boolean?) = report(state)
                    override fun onFailed(e: Exception) = fail(e)
                }
                registerHvacPowerState(area, listener)
                ({ unregisterHvacPowerState(area, listener) })
            },
        )

        fun ac(gateway: VehicleGateway) = HvacSwitchSource(
            gateway, FeatureKey.HVAC_AC,
            check = { ok, fail -> checkHvacAcCapabilities(onComplete = ok, onFailed = fail) },
            read = { area, ok, fail -> getHvacAcState(area, onComplete = ok, onFailed = fail) },
            subscribe = { area, report, fail ->
                val listener = object : HvacAcStateListener {
                    override fun onHvacAcStateUpdated(state: Boolean?) = report(state)
                    override fun onFailed(e: Exception) = fail(e)
                }
                registerHvacAcState(area, listener)
                ({ unregisterHvacAcState(area, listener) })
            },
        )

        fun recirculation(gateway: VehicleGateway) = HvacSwitchSource(
            gateway, FeatureKey.HVAC_RECIRCULATION,
            check = { ok, fail -> checkHvacRecirculationCapabilities(onComplete = ok, onFailed = fail) },
            read = { area, ok, fail -> getHvacRecirculationState(area, onComplete = ok, onFailed = fail) },
            subscribe = { area, report, fail ->
                // The SDK reuses this listener type, hence the "Auto" in the callback name.
                val listener = object : HvacRecirculationStateListener {
                    override fun onHvacAutoRecirculationStateUpdated(state: Boolean?) = report(state)
                    override fun onFailed(e: Exception) = fail(e)
                }
                registerHvacRecirculationState(area, listener)
                ({ unregisterHvacRecirculationState(area, listener) })
            },
        )

        fun auto(gateway: VehicleGateway) = HvacSwitchSource(
            gateway, FeatureKey.HVAC_AUTO,
            check = { ok, fail -> checkHvacAutoCapabilities(onComplete = ok, onFailed = fail) },
            read = { area, ok, fail -> getHvacAutoState(area, onComplete = ok, onFailed = fail) },
            subscribe = { area, report, fail ->
                val listener = object : HvacAutoStateListener {
                    override fun onHvacAutoStateUpdated(state: Boolean?) = report(state)
                    override fun onFailed(e: Exception) = fail(e)
                }
                registerHvacAutoState(area, listener)
                ({ unregisterHvacAutoState(area, listener) })
            },
        )

        fun maxAc(gateway: VehicleGateway) = HvacSwitchSource(
            gateway, FeatureKey.HVAC_MAX_AC,
            check = { ok, fail -> checkHvacMaxAcCapabilities(onComplete = ok, onFailed = fail) },
            read = { area, ok, fail -> getHvacMaxAcState(area, onComplete = ok, onFailed = fail) },
            subscribe = { area, report, fail ->
                val listener = object : HvacMaxAcStateListener {
                    override fun onHvacMaxAcStateUpdated(state: Boolean?) = report(state)
                    override fun onFailed(e: Exception) = fail(e)
                }
                registerHvacMaxAcState(area, listener)
                ({ unregisterHvacMaxAcState(area, listener) })
            },
        )

        fun maxDefrost(gateway: VehicleGateway) = HvacSwitchSource(
            gateway, FeatureKey.HVAC_MAX_DEFROST,
            check = { ok, fail -> checkHvacMaxDefrostCapabilities(onComplete = ok, onFailed = fail) },
            read = { area, ok, fail -> getHvacMaxDefrostState(area, onComplete = ok, onFailed = fail) },
            subscribe = { area, report, fail ->
                val listener = object : HvacMaxDefrostStateListener {
                    override fun onHvacMaxDefrostStateUpdated(state: Boolean?) = report(state)
                    override fun onFailed(e: Exception) = fail(e)
                }
                registerHvacMaxDefrostState(area, listener)
                ({ unregisterHvacMaxDefrostState(area, listener) })
            },
        )

        fun dual(gateway: VehicleGateway) = HvacSwitchSource(
            gateway, FeatureKey.HVAC_DUAL,
            check = { ok, fail -> checkHvacDualCapabilities(onComplete = ok, onFailed = fail) },
            read = { area, ok, fail -> getHvacDualState(area, onComplete = ok, onFailed = fail) },
            subscribe = { area, report, fail ->
                val listener = object : HvacDualStateListener {
                    override fun onHvacDualStateUpdated(state: Boolean?) = report(state)
                    override fun onFailed(e: Exception) = fail(e)
                }
                registerHvacDualState(area, listener)
                ({ unregisterHvacDualState(area, listener) })
            },
        )

        fun airCleaner(gateway: VehicleGateway) = HvacSwitchSource(
            gateway, FeatureKey.HVAC_AIR_CLEANER,
            check = { ok, fail -> checkHvacAirCleanerCapabilities(onComplete = ok, onFailed = fail) },
            read = { area, ok, fail -> getHvacAirCleanerState(area, onComplete = ok, onFailed = fail) },
            subscribe = { area, report, fail ->
                val listener = object : HvacAirCleanerStateListener {
                    override fun onHvacAirCleanerStateUpdated(state: Boolean?) = report(state)
                    override fun onFailed(e: Exception) = fail(e)
                }
                registerHvacAirCleanerState(area, listener)
                ({ unregisterHvacAirCleanerState(area, listener) })
            },
        )
    }
}

/** Target temperature per zone, stored in tenths of a degree Celsius. */
class HvacTemperatureSource(gateway: VehicleGateway) : ZonedSource<SeatArea>(gateway) {
    override val featureKey = FeatureKey.HVAC_TEMPERATURE

    private val hvac get() = gateway.vehicle.getHVAC()

    override fun zoneOf(area: SeatArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<SeatArea> = gateway.await { onComplete, onFailed ->
        hvac.checkHvacTemperatureCapabilities(
            onComplete = { config: CapabilityConfig, bounds: Map<SeatArea, AreaBounds<Float>>, _: HvacTemperatureConfigArray? ->
                onComplete(
                    ZonedCapability(
                        config.accessType,
                        bounds.mapValues { (_, b) ->
                            val min = Levels.celsiusTenths(b.minValue)!!
                            val max = Levels.celsiusTenths(b.maxValue)!!
                            min..max
                        },
                    ),
                )
            },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: SeatArea): Int? = gateway.await { onComplete, onFailed ->
        hvac.getHvacTemperature(
            area,
            TemperatureUnit.CELSIUS,
            onComplete = { celsius: Float? -> onComplete(Levels.celsiusTenths(celsius)) },
            onFailed = onFailed,
        )
    }

    override fun register(area: SeatArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): Unregister {
        val listener = object : HvacTemperatureListener {
            override fun onHvacTemperatureUpdated(temperature: Float?) = report(Levels.celsiusTenths(temperature))
            override fun onFailed(e: Exception) = fail(e)
        }
        hvac.registerHvacTemperature(area, listener)
        return { hvac.unregisterHvacTemperature(area, listener) }
    }
}

class HvacFanSpeedSource(gateway: VehicleGateway) : ZonedSource<SeatArea>(gateway) {
    override val featureKey = FeatureKey.HVAC_FAN_SPEED

    private val hvac get() = gateway.vehicle.getHVAC()

    override fun zoneOf(area: SeatArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<SeatArea> = gateway.await { onComplete, onFailed ->
        hvac.checkHvacFanSpeedCapabilities(
            onComplete = { config: CapabilityConfig, bounds: Map<SeatArea, AreaBounds<Int>> ->
                onComplete(ZonedCapability(config.accessType, bounds.mapValues { (_, b) -> b.minValue..b.maxValue }))
            },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: SeatArea): Int? = gateway.await { onComplete, onFailed ->
        hvac.getHvacFanSpeed(area, onComplete = onComplete, onFailed = onFailed)
    }

    override fun register(area: SeatArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): Unregister {
        val listener = object : HvacFanSpeedListener {
            override fun onHvacFanSpeedUpdated(speed: Int?) = report(speed)
            override fun onFailed(e: Exception) = fail(e)
        }
        hvac.registerHvacFanSpeed(area, listener)
        return { hvac.unregisterHvacFanSpeed(area, listener) }
    }
}

/** Defrosting per windshield. Its areas are windows, unlike the other HVAC features. */
class HvacDefrosterSource(gateway: VehicleGateway) : ZonedSource<WindowArea>(gateway) {
    override val featureKey = FeatureKey.HVAC_DEFROST

    private val hvac get() = gateway.vehicle.getHVAC()

    override fun zoneOf(area: WindowArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<WindowArea> = gateway.await { onComplete, onFailed ->
        hvac.checkHvacDefrosterCapabilities(
            onComplete = { config: CapabilityConfig, areas: List<WindowArea> ->
                onComplete(ZonedCapability(config.accessType, areas.associateWith { null }))
            },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: WindowArea): Int? = gateway.await { onComplete, onFailed ->
        hvac.getHvacDefrosterState(area, onComplete = { state: Boolean? -> onComplete(Levels.of(state)) }, onFailed = onFailed)
    }

    override fun register(area: WindowArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): Unregister {
        val listener = object : HvacDefrosterStateListener {
            override fun onHvacDefrosterStateUpdated(state: Boolean?) = report(Levels.of(state))
            override fun onFailed(e: Exception) = fail(e)
        }
        hvac.registerHvacDefrosterState(area, listener)
        return { hvac.unregisterHvacDefrosterState(area, listener) }
    }
}
