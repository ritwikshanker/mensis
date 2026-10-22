package com.deutschdreamers.mensis.backup

import com.deutschdreamers.mensis.backup.Fixtures.EMAIL
import com.deutschdreamers.mensis.data.crypto.AuthenticationFailedException
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.domain.model.Flow
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BackupControllerTest {
    @TempDir
    lateinit var dir: File

    private val drive = FakeDriveServer()
    private val server = MockWebServer().apply { dispatcher = drive }
    private val tokens = FakeTokens()
    private val keys = InMemoryKeyStore()
    private val scheduler = FakeScheduler()
    private val gateway = FakeGateway()
    private val notifier = FakeNotifier()
    private lateinit var store: BackupStatusStore
    private lateinit var controller: BackupController

    @BeforeEach
    fun setUp() {
        server.start()
        store = Fixtures.statusStore(dir)
        val drives = driveFactory(server, tokens)
        val export = Fixtures.export("2026-10-01" to Flow.HEAVY)
        val manager = BackupManager({ export }, keys, drives, store, notifier, Fixtures.clock, "Pixel 9")
        controller =
            BackupController(
                store,
                keys,
                scheduler,
                manager,
                drives,
                gateway,
                notifier,
                Fixtures.clock,
                Fixtures.FAST_KDF,
            )
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun backups() = drive.files.values.filter { it.name == BackupPayload.FILE_NAME }

    @Test
    fun `turning on keeps a key for the password, schedules and starts a backup`() =
        runBlocking {
            controller.turnOn(EMAIL, Fixtures.PASSWORD.toCharArray())

            val config = store.current()
            assertTrue(config.enabled)
            assertEquals(EMAIL, config.accountEmail)
            assertEquals(BackupFrequency.WEEKLY, config.frequency)
            assertTrue(config.wifiOnly)
            assertEquals(Fixtures.clock.instant(), config.passwordSetAt)
            assertEquals(config, scheduler.applied.last())
            assertEquals(1, scheduler.manualRequests)
            val sealed = EncryptedContainer.seal("probe".toByteArray(), requireNotNull(keys.load()))
            assertEquals("probe", String(EncryptedContainer.open(sealed, Fixtures.PASSWORD.toCharArray())))
        }

    @Test
    fun `frequency and Wi-Fi changes reschedule`() =
        runBlocking {
            controller.turnOn(EMAIL, Fixtures.PASSWORD.toCharArray())
            controller.setFrequency(BackupFrequency.MANUAL)
            assertEquals(BackupFrequency.MANUAL, scheduler.applied.last().frequency)
            controller.setWifiOnly(false)
            assertFalse(scheduler.applied.last().wifiOnly)
        }

    @Test
    fun `a new password uploads a fresh backup and removes the old password's backups`() =
        runBlocking {
            controller.turnOn(EMAIL, Fixtures.PASSWORD.toCharArray())
            BackupManager(
                { Fixtures.export() },
                keys,
                driveFactory(server, tokens),
                store,
                notifier,
                Fixtures.clock,
                "x",
            ).run(force = true)
            assertEquals(1, backups().size)

            val result = controller.changePassword("a completely new password".toCharArray())

            assertTrue(result is BackupRunResult.Uploaded)
            val remaining = backups().single()
            assertEquals("Pixel 9", remaining.appProperties[BackupPayload.PROP_DEVICE])
            EncryptedContainer.open(remaining.bytes, "a completely new password".toCharArray())
            assertThrows<AuthenticationFailedException> {
                EncryptedContainer.open(remaining.bytes, Fixtures.PASSWORD.toCharArray())
            }
            assertTrue(store.current().retiredKeyIds.isEmpty())
        }

    @Test
    fun `turning off forgets key and account, revokes access and keeps the Drive backups`() =
        runBlocking {
            controller.turnOn(EMAIL, Fixtures.PASSWORD.toCharArray())
            controller.setFrequency(BackupFrequency.DAILY)
            BackupManager(
                { Fixtures.export() },
                keys,
                driveFactory(server, tokens),
                store,
                notifier,
                Fixtures.clock,
                "x",
            ).run(force = true)

            controller.turnOff()

            assertNull(keys.key)
            val config = store.current()
            assertFalse(config.enabled)
            assertNull(config.accountEmail)
            assertEquals(BackupFrequency.DAILY, config.frequency)
            assertEquals(listOf(EMAIL), gateway.disconnected)
            assertEquals(1, scheduler.cancelled)
            assertEquals(1, backups().size)
        }

    @Test
    fun `deleting Drive backups removes only Mensis backups`() =
        runBlocking {
            controller.turnOn(EMAIL, Fixtures.PASSWORD.toCharArray())
            drive.put(BackupPayload.FILE_NAME, byteArrayOf(1), emptyMap())
            drive.put(BackupPayload.FILE_NAME, byteArrayOf(2), emptyMap())
            drive.put("other.bin", byteArrayOf(3), emptyMap())

            assertEquals(2, controller.deleteDriveBackups())

            assertTrue(backups().isEmpty())
            assertEquals(1, drive.files.size)
            assertNull(store.current().lastSuccessAt)
        }

    @Test
    fun `reconnecting clears the flag and the notification and backs up`() =
        runBlocking {
            store.update { it.copy(enabled = true, accountEmail = EMAIL, needsReconnect = true) }
            controller.reconnected("other@example.com")
            val config = store.current()
            assertFalse(config.needsReconnect)
            assertEquals("other@example.com", config.accountEmail)
            assertEquals(1, notifier.cancelled)
            assertEquals(1, scheduler.manualRequests)
            assertNotNull(config)
        }
}
