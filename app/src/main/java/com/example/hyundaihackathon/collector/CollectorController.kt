package com.example.hyundaihackathon.collector

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.hyundaihackathon.appContainer
import kotlinx.coroutines.launch

/** The one place that starts and stops collection. */
object CollectorController {
    /**
     * Call while the app is visible: Android only lets a visible app start a foreground
     * service. The engine itself refuses to run without consent or with recording off.
     */
    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, CollectorService::class.java))
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.appContainer.appScope.launch { stopNow(appContext) }
    }

    /** Stops collection, deletes every usage record, then resumes if recording is still allowed. */
    suspend fun eraseAllData(context: Context) = replaceUsageData(context) {}

    /**
     * Stops collection, deletes every usage record, runs [write] (the demo build writes
     * synthetic months here), then resumes if recording is still allowed.
     */
    suspend fun replaceUsageData(context: Context, write: suspend () -> Unit) {
        val appContext = context.applicationContext
        val container = appContext.appContainer
        stopNow(appContext)
        container.eraseUsageData()
        write()
        if (container.settingsRepository.current().canRecord) start(appContext)
    }

    private suspend fun stopNow(appContext: Context) {
        appContext.appContainer.collectorEngine.stop()
        appContext.stopService(Intent(appContext, CollectorService::class.java))
    }
}
