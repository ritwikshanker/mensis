package com.deutschdreamers.mensis.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WorkManagerBackupSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scheduler = WorkManagerBackupScheduler(context)
    private val on = BackupConfig(enabled = true, accountEmail = "someone@example.com")

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun periodic(): WorkInfo? =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork("mensis-backup-periodic")
            .get()
            .lastOrNull { !it.state.isFinished }

    @Test
    fun weeklyOnWifi_isPeriodicOnUnmeteredNetworksAndBattery() {
        scheduler.apply(on)
        val info = requireNotNull(periodic())
        assertEquals(NetworkType.UNMETERED, info.constraints.requiredNetworkType)
        assertTrue(info.constraints.requiresBatteryNotLow())
        assertEquals(TimeUnit.DAYS.toMillis(7), info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun changingFrequencyAndNetworkUpdatesTheSameWork() {
        scheduler.apply(on)
        val id = requireNotNull(periodic()).id
        scheduler.apply(on.copy(frequency = BackupFrequency.MONTHLY, wifiOnly = false))
        val info = requireNotNull(periodic())
        assertEquals(id, info.id)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(TimeUnit.DAYS.toMillis(30), info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun manualOrOff_cancelsThePeriodicWork() {
        scheduler.apply(on)
        scheduler.apply(on.copy(frequency = BackupFrequency.MANUAL))
        assertEquals(null, periodic())
        scheduler.apply(on)
        scheduler.apply(on.copy(enabled = false))
        assertEquals(null, periodic())
    }

    @Test
    fun backUpNow_queuesOneManualRunAtATime() {
        scheduler.backUpNow()
        scheduler.backUpNow()
        val runs =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWork("mensis-backup-now")
                .get()
        assertEquals(1, runs.size)
        assertEquals(NetworkType.CONNECTED, runs.single().constraints.requiredNetworkType)
    }
}
