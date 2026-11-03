package com.garam.hackathon.vehicle.sources

import ai.pleos.playground.vehicle.api.model.capability.AccessType
import android.util.Log
import com.garam.hackathon.vehicle.CapabilityResult
import com.garam.hackathon.vehicle.FeatureListener
import com.garam.hackathon.vehicle.FeatureSource
import com.garam.hackathon.vehicle.SdkError
import com.garam.hackathon.vehicle.VehicleGateway
import com.garam.hackathon.vehicle.ZoneLevel
import com.garam.hackathon.vehicle.ZoneRange

/** The supported areas a capability call returned, with level bounds where the SDK has them. */
class ZonedCapability<A>(val accessType: AccessType, val areas: Map<A, IntRange?>)

/**
 * A feature read per area with one SDK listener per area. Subclasses only bind the SDK
 * calls; this class keeps the capability's area objects and pairs every register call
 * with its unregister call.
 */
abstract class ZonedSource<A : Any>(protected val gateway: VehicleGateway) : FeatureSource {
    private var areas: Map<Int, A> = emptyMap()
    private val unregisterActions = mutableListOf<() -> Unit>()

    protected abstract fun zoneOf(area: A): Int

    protected abstract suspend fun queryCapability(): ZonedCapability<A>

    protected abstract suspend fun readLevel(area: A): Int?

    /** Registers a listener for [area] and returns the call that unregisters the same listener. */
    protected abstract fun register(area: A, report: (Int?) -> Unit, fail: (Exception) -> Unit): () -> Unit

    final override suspend fun checkCapability(): CapabilityResult {
        val capability = try {
            queryCapability()
        } catch (e: SdkError) {
            areas = emptyMap()
            return CapabilityResult.failed(e)
        }
        val readable = capability.accessType == AccessType.READ || capability.accessType == AccessType.READ_WRITE
        areas = if (readable) capability.areas.keys.associateBy(::zoneOf) else emptyMap()
        val zones = capability.areas.map { (area, bounds) -> ZoneRange(zoneOf(area), bounds?.first, bounds?.last) }
        return CapabilityResult(supported = areas.isNotEmpty(), zones = zones)
    }

    final override suspend fun readInitialState(): List<ZoneLevel> = areas.mapNotNull { (zone, area) ->
        try {
            ZoneLevel(zone, readLevel(area))
        } catch (e: SdkError) {
            Log.w(TAG, "$featureKey initial read failed for zone $zone: ${e.code}")
            null
        }
    }

    final override fun start(listener: FeatureListener) {
        for ((zone, area) in areas) {
            val unregister = try {
                register(
                    area,
                    report = { level -> listener.onLevel(featureKey, ZoneLevel(zone, level)) },
                    fail = { e -> listener.onError(featureKey, SdkError.from(e)) },
                )
            } catch (e: Exception) {
                throw SdkError.from(e)
            }
            unregisterActions += unregister
        }
    }

    final override fun stop() {
        for (unregister in unregisterActions) {
            try {
                unregister()
            } catch (e: Exception) {
                Log.w(TAG, "$featureKey unregister failed: ${SdkError.from(e).code}")
            }
        }
        unregisterActions.clear()
    }

    private companion object {
        const val TAG = "ZonedSource"
    }
}
