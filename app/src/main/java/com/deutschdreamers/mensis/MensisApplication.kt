package com.deutschdreamers.mensis

import android.app.Application
import com.deutschdreamers.mensis.files.DocumentFiles
import com.deutschdreamers.mensis.lock.AppLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MensisApplication : Application() {
    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var documentFiles: DocumentFiles

    override fun onCreate() {
        super.onCreate()
        appLockManager.start()
        // Copies made for "Share file" live only until the next start.
        documentFiles.clearShared()
    }
}
