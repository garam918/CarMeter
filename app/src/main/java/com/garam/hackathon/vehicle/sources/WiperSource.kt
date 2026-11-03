package com.garam.hackathon.vehicle.sources

import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.WiperStateListener
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.Levels
import com.garam.hackathon.vehicle.SdkError
import com.garam.hackathon.vehicle.VehicleGateway
import com.garam.hackathon.vehicle.Zone

/**
 * Wiper use per windshield. The SDK has a listener for the on/off state only, so each
 * time the state turns on, the switch position is read once to learn the speed. A
 * speed change while the wiper keeps running may go unseen.
 */
class WiperSource(gateway: VehicleGateway) : ZonedSource<WindowArea>(gateway) {
    override val featureKey = FeatureKey.WIPER

    private val wiper get() = gateway.vehicle.getWiper()

    override fun zoneOf(area: WindowArea) = Zone.of(area)

    override suspend fun queryCapability(): ZonedCapability<WindowArea> = gateway.await { onComplete, onFailed ->
        wiper.checkWiperStateCapabilities(
            onComplete = { config: CapabilityConfig, states: Map<WindowArea, List<WiperState>> ->
                onComplete(ZonedCapability(config.accessType, states.mapValues { null }))
            },
            onFailed = onFailed,
        )
    }

    override suspend fun readLevel(area: WindowArea): Int? {
        val state: WiperState? = gateway.await { onComplete, onFailed ->
            wiper.getCurrentWiperState(area, onComplete = onComplete, onFailed = onFailed)
        }
        if (state != WiperState.ON) return state?.let { 0 }
        val switch = try {
            gateway.await<WiperSwitch?> { onComplete, onFailed ->
                wiper.getCurrentWiperSwitch(area, onComplete = onComplete, onFailed = onFailed)
            }
        } catch (e: SdkError) {
            null
        }
        return Levels.wiperSpeed(switch)
    }

    override fun register(area: WindowArea, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit {
        val listener = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                if (state != WiperState.ON) {
                    report(state?.let { 0 })
                    return
                }
                gateway.request<WiperSwitch?>(
                    call = { onComplete, onFailed -> wiper.getCurrentWiperSwitch(area, onComplete = onComplete, onFailed = onFailed) },
                    onResult = { result -> report(Levels.wiperSpeed(result.getOrNull())) },
                )
            }

            override fun onFailed(e: Exception) = fail(e)
        }
        wiper.registerWiperState(area, listener)
        return { wiper.unregisterWiperState(area, listener) }
    }
}
