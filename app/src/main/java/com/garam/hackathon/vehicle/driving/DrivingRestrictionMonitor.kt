package com.garam.hackathon.vehicle.driving

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tells the UI whether the car requires distraction-optimized screens. Listens only while
 * its lifecycle is started. If the car service is unavailable it reports false, so the
 * app stays usable on non-car devices. While car service restarts, the last value stays.
 */
class DrivingRestrictionMonitor(context: Context) : DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private var connection: UxRestrictionsConnection? = null

    private val _requiresDistractionOptimization = MutableStateFlow(false)
    val requiresDistractionOptimization: StateFlow<Boolean> = _requiresDistractionOptimization.asStateFlow()

    override fun onStart(owner: LifecycleOwner) {
        val uxConnection = UxRestrictionsConnection(appContext) { restrictions ->
            _requiresDistractionOptimization.value = restrictions.isRequiresDistractionOptimization
        }
        connection = uxConnection
        // On the main thread a ready car service reports inside this call, so the first frame
        // already knows whether the car is driving. A short wait keeps a slow start from an ANR.
        uxConnection.connect(UI_WAIT_MS)
    }

    override fun onStop(owner: LifecycleOwner) {
        connection?.disconnect()
        connection = null
    }

    private companion object {
        const val UI_WAIT_MS = 2_000L
    }
}
