package com.a1launcher

import android.app.Application
import com.a1launcher.data.SettingsRepository

class A1LauncherApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }
}
