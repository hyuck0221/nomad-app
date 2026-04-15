package com.nomad.travel

import android.app.Application
import com.nomad.travel.data.AppContainer
import com.nomad.travel.data.DefaultAppContainer

class NomadApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
