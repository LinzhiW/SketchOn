package com.example.gemtronicring.p4e7a91c3.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.gemtronicring.p4e7a91c3.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
