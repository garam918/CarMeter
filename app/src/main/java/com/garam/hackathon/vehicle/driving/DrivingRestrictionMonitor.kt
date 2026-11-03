package com.garam.hackathon.vehicle.driving

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tells the UI whether the car requires distraction-optimized screens. Listens only while
 * its lifecycle is started. If the car service is unavailable it reports false, so the
 * app stays usable on non-car devices.
 */
class DrivingRestrictionMonitor(context: Context) : DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    private val _requiresDistractionOptimization = MutableStateFlow(false)
    val requiresDistractionOptimization: StateFlow<Boolean> = _requiresDistractionOptimization.asStateFlow()

    override fun onStart(owner: LifecycleOwner) {
        try {
            val connected = Car.createCar(appContext) ?: return
            car = connected
            val uxManager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            manager = uxManager
            _requiresDistractionOptimization.value = uxManager.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
            uxManager.registerListener { restrictions ->
                _requiresDistractionOptimization.value = restrictions.isRequiresDistractionOptimization
            }
        } catch (e: Exception) {
            Log.w(TAG, "Car service unavailable: ${e.javaClass.simpleName}")
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        manager?.unregisterListener()
        manager = null
        car?.disconnect()
        car = null
    }

    private companion object {
        const val TAG = "DrivingRestriction"
    }
}
