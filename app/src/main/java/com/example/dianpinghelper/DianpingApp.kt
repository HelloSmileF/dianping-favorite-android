package com.example.dianpinghelper

import android.app.Application
import com.example.dianpinghelper.data.SettingsRepository

class DianpingApp : Application() {

    /** 全局单例：收藏记录存储 */
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsRepository = SettingsRepository(this)
    }

    companion object {
        lateinit var instance: DianpingApp
            private set
    }
}
