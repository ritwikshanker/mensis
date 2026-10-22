package com.deutschdreamers.mensis

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.deutschdreamers.mensis.files.DocumentFiles
import com.deutschdreamers.mensis.lock.AppLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MensisApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var documentFiles: DocumentFiles

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** The backup worker gets its dependencies from Hilt. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appLockManager.start()
        // Copies made for "Share file" live only until the next start.
        documentFiles.clearShared()
    }
}
