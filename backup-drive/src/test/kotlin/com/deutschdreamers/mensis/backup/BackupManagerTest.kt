package com.deutschdreamers.mensis.backup

import com.deutschdreamers.mensis.backup.Fixtures.EMAIL
import com.deutschdreamers.mensis.backup.drive.DriveAppDataClient
import com.deutschdreamers.mensis.data.backup.BackupError
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.export.MensisExport
import com.deutschdreamers.mensis.data.export.MensisJson
import com.deutschdreamers.mensis.domain.model.Flow
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

class BackupManagerTest {
    @TempDir
    lateinit var dir: File

    private val drive = FakeDriveServer()
    private val server = MockWebServer().apply { dispatcher = drive }
    private val tokens = FakeTokens()
    private val keys = InMemoryKeyStore(Fixtures.key())
    private val notifier = FakeNotifier()
    private var export: MensisExport = Fixtures.export("2026-10-01" to Flow.HEAVY, "2026-10-02" to Flow.MEDIUM)
    private lateinit var store: BackupStatusStore
    private lateinit var manager: BackupManager

    @BeforeEach
    fun setUp() =
        runBlocking {
            server.start()
            store = Fixtures.statusStore(dir)
            store.update { it.copy(enabled = true, accountEmail = EMAIL) }
            manager =
                BackupManager(
                    { export },
                    keys,
                    driveFactory(server, tokens),
                    store,
                    notifier,
                    Fixtures.clock,
                    "Pixel 9",
                )
        }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun backups() = drive.files.values.filter { it.name == BackupPayload.FILE_NAME }

    @Test
    fun `a run uploads the sealed export, verifies it and records the success`() =
        runBlocking {
            val phases = mutableListOf<BackupPhase>()

            val result = manager.run { phases += it }

            assertTrue(result is BackupRunResult.Uploaded)
            val stored = backups().single()
            val opened = EncryptedContainer.open(stored.bytes, Fixtures.PASSWORD.toCharArray())
            assertEquals(export, MensisJson.decode(String(opened)))
            assertEquals("Pixel 9", stored.appProperties[BackupPayload.PROP_DEVICE])
            assertEquals("1", stored.appProperties[BackupPayload.PROP_FORMAT_VERSION])
            assertEquals(BackupPayload.keyId(keys.load()!!), stored.appProperties[BackupPayload.PROP_KEY_ID])
            assertEquals(BackupPhase.entries, phases)
            val config = store.current()
            assertEquals(stored.createdTime, config.lastSuccessAt)
            assertEquals(stored.bytes.size.toLong(), config.lastSuccessBytes)
            assertEquals("Pixel 9", config.lastSuccessDevice)
            assertNull(config.lastError)
        }

    @Test
    fun `nothing leaves the phone in clear`() =
        runBlocking {
            manager.run()
            val upload = drive.requests.single { it.method == "POST" }
            val body = String(upload.body!!.toByteArray(), Charsets.ISO_8859_1)
            assertFalse(body.contains("2026-10-01"))
            assertFalse(body.contains("heavy"))
        }

    @Test
    fun `unchanged data is not uploaded again, only checked`() =
        runBlocking {
            manager.run()
            // Only the export timestamp differs.
            export = export.copy(exportedAt = "2026-10-22T08:00:00Z")

            val result = manager.run()

            assertEquals(BackupRunResult.Unchanged, result)
            assertEquals(1, backups().size)
            assertEquals(1, drive.requests.count { it.method == "POST" })
            assertEquals(Fixtures.clock.instant(), store.current().lastCheckedAt)
        }

    @Test
    fun `changed data or a forced run uploads`() =
        runBlocking {
            manager.run()
            export = Fixtures.export("2026-10-01" to Flow.HEAVY, "2026-10-02" to Flow.LIGHT)
            assertTrue(manager.run() is BackupRunResult.Uploaded)
            assertTrue(manager.run(force = true) is BackupRunResult.Uploaded)
            assertEquals(3, drive.requests.count { it.method == "POST" })
        }

    @Test
    fun `only the two newest backups are kept`() =
        runBlocking {
            repeat(4) { manager.run(force = true) }

            val kept = backups().sortedByDescending { it.createdTime }
            assertEquals(BackupManager.KEEP_BACKUPS, kept.size)
            assertEquals(Instant.parse("2026-10-20T21:17:00Z"), kept.first().createdTime)
        }

    @Test
    fun `backups under another password and foreign files are left alone`() =
        runBlocking {
            drive.put(BackupPayload.FILE_NAME, byteArrayOf(1), mapOf(BackupPayload.PROP_KEY_ID to "another-phone"))
            drive.put("notes.txt", byteArrayOf(2), emptyMap())

            repeat(3) { manager.run(force = true) }

            assertEquals(1, backups().count { it.appProperties[BackupPayload.PROP_KEY_ID] == "another-phone" })
            assertTrue(drive.files.values.any { it.name == "notes.txt" })
        }

    @Test
    fun `backups under a retired password are deleted after a good upload`() =
        runBlocking {
            drive.put(BackupPayload.FILE_NAME, byteArrayOf(1), mapOf(BackupPayload.PROP_KEY_ID to "old-password"))
            store.update { it.copy(retiredKeyIds = setOf("old-password")) }

            manager.run(force = true)

            assertTrue(backups().none { it.appProperties[BackupPayload.PROP_KEY_ID] == "old-password" })
            assertTrue(store.current().retiredKeyIds.isEmpty())
        }

    @Test
    fun `an upload that does not match is deleted and reported`() =
        runBlocking {
            drive.corruptNextUpload = true

            val result = manager.run()

            assertEquals(BackupRunResult.Failed(BackupError.VERIFY_FAILED, retryable = true), result)
            assertTrue(backups().isEmpty())
            assertEquals(BackupError.VERIFY_FAILED, store.current().lastError)
        }

    @Test
    fun `a revoked grant marks the account for reconnecting and posts the notification`() =
        runBlocking {
            tokens.reconnectRequired = true

            val result = manager.run()

            assertEquals(BackupRunResult.NeedsReconnect, result)
            assertTrue(store.current().needsReconnect)
            assertEquals(1, notifier.posted)
        }

    @Test
    fun `a manual run that needs reconnecting shows it on screen, not as a notification`() =
        runBlocking {
            tokens.reconnectRequired = true
            manager.run(interactive = true)
            assertEquals(0, notifier.posted)
        }

    @Test
    fun `a success after reconnecting clears the flag and the notification`() =
        runBlocking {
            store.update { it.copy(needsReconnect = true, lastError = BackupError.NETWORK) }
            manager.run()
            assertFalse(store.current().needsReconnect)
            assertNull(store.current().lastError)
            assertEquals(1, notifier.cancelled)
        }

    @Test
    fun `no connection is a retryable network failure`() =
        runBlocking {
            val offline =
                DriveFactory {
                    DriveAppDataClient(OkHttpClient(), tokens, "http://127.0.0.1:1/".toHttpUrl())
                }
            manager = BackupManager({ export }, keys, offline, store, notifier, Fixtures.clock, "Pixel 9")
            val result = manager.run()
            assertEquals(BackupRunResult.Failed(BackupError.NETWORK, retryable = true), result)
            assertEquals(Fixtures.clock.instant(), store.current().lastErrorAt)
        }

    @Test
    fun `a full Drive is reported and not retried`() =
        runBlocking {
            drive.failNext = FakeDriveServer.json(403, """{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}""")
            assertEquals(BackupRunResult.Failed(BackupError.DRIVE_FULL, retryable = false), manager.run(force = true))
        }

    @Test
    fun `a missing key is reported without touching Drive`() =
        runBlocking {
            keys.delete()
            assertEquals(BackupRunResult.Failed(BackupError.NO_PASSWORD, retryable = false), manager.run())
            assertTrue(drive.requests.isEmpty())
        }

    @Test
    fun `nothing happens while backup is off`() =
        runBlocking {
            store.update { it.copy(enabled = false) }
            assertEquals(BackupRunResult.NotEnabled, manager.run())
            assertTrue(drive.requests.isEmpty())
        }

    @Test
    fun `the backup restores to the same export bytes`() =
        runBlocking {
            manager.run()
            val stored = backups().single()
            assertArrayEquals(MensisJson.encode(export), EncryptedContainer.open(stored.bytes, keys.load()!!))
        }
}
