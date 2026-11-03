package com.example.hyundaihackathon

import android.app.Application
import android.content.Context
import com.example.hyundaihackathon.di.AppContainer

class CarMeterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as CarMeterApp).container
