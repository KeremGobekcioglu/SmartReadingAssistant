package com.gobex.smartreadingassistant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartReadingApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}

