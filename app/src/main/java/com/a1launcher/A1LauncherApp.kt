package com.a1launcher

import android.app.Application
import com.a1launcher.data.SettingsRepository
import com.a1launcher.linux.LinuxRuntime
import com.a1launcher.linux.LlamaServerRuntime

class A1LauncherApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    val linuxRuntime: LinuxRuntime by lazy { LinuxRuntime(this) }
    val llamaServerRuntime: LlamaServerRuntime by lazy { LlamaServerRuntime(linuxRuntime.proot) }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }
}
