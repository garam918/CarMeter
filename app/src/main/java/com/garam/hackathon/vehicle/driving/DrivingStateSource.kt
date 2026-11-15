package com.garam.hackathon.vehicle.driving

import android.car.drivingstate.CarUxRestrictions
import android.content.Context
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
 *
 * The connection survives car service restarts: after one, the current state is reported
 * again, and the interval tracker ignores it if nothing changed.
 */
class DrivingStateSource(context: Context) : FeatureSource {
    override val featureKey = FeatureKey.DRIVING

    private val appContext = context.applicationContext
    private var connection: UxRestrictionsConnection? = null

    @Volatile
    private var listener: FeatureListener? = null

    override suspend fun checkCapability(): CapabilityResult {
        val uxConnection = connection ?: UxRestrictionsConnection(appContext, ::report).also {
            connection = it
            // Called on the collector's worker thread, so waiting here does not block the UI.
            it.connect(COLLECTOR_WAIT_MS)
        }
        val supported = uxConnection.isReady
        if (!supported) {
            // The collector never starts an unsupported source, so let the next check retry.
            uxConnection.disconnect()
            connection = null
        }
        return CapabilityResult(
            supported = supported,
            zones = if (supported) listOf(ZoneRange(Zone.GLOBAL)) else emptyList(),
            error = if (supported) null else "CAR_UX_RESTRICTION_SERVICE_UNAVAILABLE",
        )
    }

    override suspend fun readInitialState(): List<ZoneLevel> {
        val restrictions = connection?.current() ?: return emptyList()
        return listOf(ZoneLevel(Zone.GLOBAL, levelOf(restrictions)))
    }

    override fun start(listener: FeatureListener) {
        this.listener = listener
    }

    override fun stop() {
        listener = null
        connection?.disconnect()
        connection = null
    }

    private fun report(restrictions: CarUxRestrictions) {
        listener?.onLevel(featureKey, ZoneLevel(Zone.GLOBAL, levelOf(restrictions)))
    }

    private fun levelOf(restrictions: CarUxRestrictions) = if (restrictions.isRequiresDistractionOptimization) Levels.ON else 0

    private companion object {
        /** About what the old `Car.createCar(context)` waited for car service. */
        const val COLLECTOR_WAIT_MS = 5_000L
    }
}
