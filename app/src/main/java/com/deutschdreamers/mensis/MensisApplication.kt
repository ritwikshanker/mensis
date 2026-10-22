package com.deutschdreamers.mensis

import android.app.Application
import com.deutschdreamers.mensis.lock.AppLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MensisApplication : Application() {
    @Inject
    lateinit var appLockManager: AppLockManager

    override fun onCreate() {
        super.onCreate()
        appLockManager.start()
    }
}
