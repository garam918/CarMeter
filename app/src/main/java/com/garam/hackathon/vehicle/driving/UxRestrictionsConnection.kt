package com.garam.hackathon.vehicle.driving

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log

/**
 * One connection to the AAOS `CarUxRestrictions` service that survives car service restarts.
 *
 * `Car.createCar(context)` without a lifecycle listener kills the app process when car service
 * restarts (seen on the emulator about two minutes after boot, docs/sdk-notes.md 17). With the
 * listener, the same [Car] reports `ready = false` and later `ready = true`; the restriction
 * listener is then registered again on the new manager and the current restrictions are sent
 * once more, since they may have changed while car service was gone.
 *
 * Lifecycle callbacks arrive on the main thread while callers may connect from a worker thread,
 * so the shared state is guarded by [lock]. Use one instance per connect/disconnect cycle.
 */
internal class UxRestrictionsConnection(
    context: Context,
    private val onRestrictions: (CarUxRestrictions) -> Unit,
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var closed = false

    /** Whether the restrictions service is reachable right now. */
    val isReady: Boolean get() = synchronized(lock) { manager != null }

    /**
     * Connects, blocking up to [waitMs] for car service. Returns [isReady]. When car service
     * comes up later, the connection attaches then and reports through [onRestrictions].
     */
    fun connect(waitMs: Long): Boolean {
        val created = try {
            Car.createCar(appContext, null, waitMs) { connectedCar, ready ->
                if (ready) attach(connectedCar) else detach()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Car service unavailable: ${e.javaClass.simpleName}")
            return false
        }
        synchronized(lock) {
            if (closed) {
                created.disconnect()
                return false
            }
            car = created
        }
        // From a worker thread the ready callback is posted to the main thread, so attach now.
        if (created.isConnected) attach(created)
        return isReady
    }

    /** The current restrictions, or null while car service is unavailable. */
    fun current(): CarUxRestrictions? {
        val activeManager = synchronized(lock) { manager } ?: return null
        return try {
            activeManager.currentCarUxRestrictions
        } catch (e: Exception) {
            Log.w(TAG, "Reading restrictions failed: ${e.javaClass.simpleName}")
            null
        }
    }

    fun disconnect() {
        val (oldCar, oldManager) = synchronized(lock) {
            closed = true
            (car to manager).also {
                car = null
                manager = null
            }
        }
        try {
            oldManager?.unregisterListener()
        } catch (e: Exception) {
            Log.w(TAG, "Unregistering failed: ${e.javaClass.simpleName}")
        }
        try {
            oldCar?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Disconnecting failed: ${e.javaClass.simpleName}")
        }
    }

    private fun attach(connectedCar: Car) {
        val attached = synchronized(lock) {
            if (closed || manager != null) return
            try {
                val uxManager = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    ?: return
                uxManager.registerListener { restrictions -> onRestrictions(restrictions) }
                manager = uxManager
                uxManager
            } catch (e: Exception) {
                Log.w(TAG, "Restrictions service unavailable: ${e.javaClass.simpleName}")
                return
            }
        }
        try {
            attached.currentCarUxRestrictions?.let(onRestrictions)
        } catch (e: Exception) {
            Log.w(TAG, "Reading restrictions failed: ${e.javaClass.simpleName}")
        }
    }

    /** Car service died; its manager and listener went with it. The same Car reconnects. */
    private fun detach() {
        synchronized(lock) { manager = null }
        Log.i(TAG, "Car service restarting; waiting to reconnect")
    }

    private companion object {
        const val TAG = "UxRestrictions"
    }
}
