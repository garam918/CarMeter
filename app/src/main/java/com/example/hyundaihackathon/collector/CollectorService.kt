package com.example.hyundaihackathon.collector

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.hyundaihackathon.appContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the process in the foreground so SDK listeners keep firing after the app leaves
 * the screen. All work lives in [CollectorEngine]; this class only follows the service
 * lifecycle. See docs/sdk-notes.md section 7 for why the type is specialUse.
 */
class CollectorService : Service() {
    private val engine get() = appContainer.collectorEngine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, CollectorNotification.ID, CollectorNotification.build(this), type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent means the system restarted the service after killing the process.
        if (intent == null) Log.i(TAG, "Restarted by the system")
        appContainer.appScope.launch {
            // Stopped means no consent or recording switched off; Failed means no vehicle service.
            val status = engine.start()
            if (status is CollectorStatus.Stopped || status is CollectorStatus.Failed) stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // CollectorController.stop() stops the engine first. This covers the system
        // stopping the service on its own, so open intervals still close with STOP.
        runBlocking { withTimeoutOrNull(STOP_TIMEOUT_MS) { engine.stop() } }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CollectorService"
        const val STOP_TIMEOUT_MS = 2_000L
    }
}
