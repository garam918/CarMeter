package com.example.hyundaihackathon.vehicle

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** The connection part of the SDK the collector needs, so tests can replace it. */
interface VehicleSession {
    fun connect()

    /** Releases the SDK after pending callbacks arrive, or after a bounded wait. */
    suspend fun release()
}

/**
 * Owns the app's single [Vehicle] instance. It counts SDK calls whose callbacks have not
 * arrived yet, because the SDK drops pending callbacks when [Vehicle.release] runs.
 */
class VehicleGateway(context: Context) : VehicleSession {
    val vehicle = Vehicle(context.applicationContext)

    private val pendingCallbacks = MutableStateFlow(0)

    @Volatile
    private var connected = false

    @Synchronized
    override fun connect() {
        if (connected) return
        vehicle.initialize()
        connected = true
    }

    override suspend fun release() {
        if (!connected) return
        withTimeoutOrNull(RELEASE_WAIT_MS) { pendingCallbacks.first { it == 0 } }
        synchronized(this) {
            vehicle.release()
            connected = false
        }
    }

    /**
     * Runs one callback-style SDK call and delivers its answer once to [onResult], on
     * whatever thread the SDK answers. Use it inside listeners, where suspending is not possible.
     */
    fun <T> request(
        call: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
        onResult: (Result<T>) -> Unit,
    ) {
        val answered = AtomicBoolean(false)
        fun answer(result: Result<T>) {
            if (!answered.compareAndSet(false, true)) return
            pendingCallbacks.update { it - 1 }
            onResult(result)
        }

        pendingCallbacks.update { it + 1 }
        try {
            call({ value -> answer(Result.success(value)) }, { e -> answer(Result.failure(SdkError.from(e))) })
        } catch (e: Exception) {
            answer(Result.failure(SdkError.from(e)))
        }
    }

    /**
     * Suspends until one SDK call answers. A call that times out still counts as pending
     * until the SDK finally answers it.
     */
    suspend fun <T> await(
        timeoutMs: Long = CALL_TIMEOUT_MS,
        call: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    ): T = try {
        withTimeout(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                request(call) { result -> continuation.resumeWith(result) }
            }
        }
    } catch (e: TimeoutCancellationException) {
        throw SdkError.Timeout()
    }

    private companion object {
        // The SDK queues calls until the service binds, retrying the bind for up to 15 s.
        const val CALL_TIMEOUT_MS = 20_000L
        const val RELEASE_WAIT_MS = 3_000L
    }
}
