package com.garam.hackathon

import android.app.Application
import android.content.Context
import com.garam.hackathon.di.AppContainer
import kotlinx.coroutines.launch

class CarMeterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Old records are deleted when any part of the app starts, recording on or off.
        container.appScope.launch { container.dataRetention.purgeIfDue() }
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as CarMeterApp).container
