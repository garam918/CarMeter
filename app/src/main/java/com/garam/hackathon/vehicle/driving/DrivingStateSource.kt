package com.garam.hackathon.vehicle.driving

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import com.garam.hackathon.vehicle.CapabilityResult
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.FeatureListener
import com.garam.hackathon.vehicle.FeatureSource
import com.garam.hackathon.vehicle.Levels
import com.garam.hackathon.vehicle.Zone
import com.garam.hackathon.vehicle.ZoneLevel
import com.garam.hackathon.vehicle.ZoneRange

/**
 * Whether the car requires distraction-optimized UI, from the AAOS `CarUxRestrictions`
 * service rather than the Vehicle SDK. On the emulator this is true in D or R even at
 * a standstill, so a red light still counts as driving (docs/sdk-notes.md 3.14).
 */
class DrivingStateSource(context: Context) : FeatureSource {
    override val featureKey = FeatureKey.DRIVING

    private val appContext = context.applicationContext
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    override suspend fun checkCapability(): CapabilityResult {
        if (manager == null) {
            try {
                val connected = Car.createCar(appContext)
                car = connected
                manager = connected?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            } catch (e: Exception) {
                Log.w(TAG, "Car service unavailable: ${e.javaClass.simpleName}")
            }
        }
        val supported = manager != null
        return CapabilityResult(
            supported = supported,
            zones = if (supported) listOf(ZoneRange(Zone.GLOBAL)) else emptyList(),
            error = if (supported) null else "CAR_UX_RESTRICTION_SERVICE_UNAVAILABLE",
        )
    }

    override suspend fun readInitialState(): List<ZoneLevel> {
        val restrictions = manager?.currentCarUxRestrictions ?: return emptyList()
        return listOf(ZoneLevel(Zone.GLOBAL, levelOf(restrictions)))
    }

    override fun start(listener: FeatureListener) {
        manager?.registerListener { restrictions -> listener.onLevel(featureKey, ZoneLevel(Zone.GLOBAL, levelOf(restrictions))) }
    }

    override fun stop() {
        manager?.unregisterListener()
        manager = null
        car?.disconnect()
        car = null
    }

    private fun levelOf(restrictions: CarUxRestrictions) = if (restrictions.isRequiresDistractionOptimization) Levels.ON else 0

    private companion object {
        const val TAG = "DrivingStateSource"
    }
}
