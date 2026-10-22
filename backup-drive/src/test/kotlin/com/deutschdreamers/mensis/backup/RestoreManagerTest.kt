package com.deutschdreamers.mensis.backup

import com.deutschdreamers.mensis.backup.Fixtures.EMAIL
import com.deutschdreamers.mensis.backup.Fixtures.PASSWORD
import com.deutschdreamers.mensis.backup.drive.DriveAppDataClient
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.export.ExportMigration
import com.deutschdreamers.mensis.data.export.ExportMigrations
import com.deutschdreamers.mensis.data.importer.ParsedImport
import com.deutschdreamers.mensis.data.importer.RestoreResult
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.data.settings.ThemeScheme
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.time.LocalDate

class RestoreManagerTest {
    @TempDir
    lateinit var dir: File

    private val drive = FakeDriveServer()
    private val server = MockWebServer().apply { dispatcher = drive }
    private val tokens = FakeTokens()
    private val keys = InMemoryKeyStore()
    private val scheduler = FakeScheduler()
    private var replaced: ParsedImport? = null
    private lateinit var store: BackupStatusStore
    private lateinit var uiPrefs: UiPrefsStore

    /** A synthetic backup written by format version 1, kept frozen so later formats must still read it. */
    private val fixture: String =
        requireNotNull(javaClass.getResource("/fixtures/backup-format-v1.json")).readText()

    @BeforeEach
    fun setUp() {
        server.start()
        store = Fixtures.statusStore(dir)
        uiPrefs = Fixtures.uiPrefsStore(dir)
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun restorer(migrations: ExportMigrations = ExportMigrations.Default) =
        RestoreManager(
            drives = driveFactory(server, tokens),
            snapshots = { Fixtures.export() },
            replaceAll = { parsed ->
                replaced = parsed
                RestoreResult(
                    parsed.days.size,
                    0,
                    parsed.days.firstOrNull()?.date,
                    parsed.days.lastOrNull()?.date,
                    true,
                )
            },
            uiPrefsStore = uiPrefs,
            keys = keys,
            store = store,
            scheduler = scheduler,
            clock = Fixtures.clock,
            migrations = migrations,
        )

    private fun upload(
        text: String = fixture,
        password: String = PASSWORD,
    ): FakeDriveServer.StoredFile {
        val sealed = EncryptedContainer.seal(text.toByteArray(), password.toCharArray(), Fixtures.FAST_KDF)
        return drive.put(BackupPayload.FILE_NAME, sealed, mapOf(BackupPayload.PROP_DEVICE to "Pixel 9"))
    }

    private suspend fun RestoreManager.openLatest(password: String = PASSWORD): OpenedBackup {
        val file = requireNotNull(latestBackup(EMAIL))
        return open(file, download(EMAIL, file), password.toCharArray())
    }

    private fun failure(block: suspend () -> Unit): RestoreError =
        try {
            runBlocking { block() }
            error("expected a RestoreException")
        } catch (e: RestoreException) {
            e.error
        }

    @Test
    fun `the newest Mensis backup is offered, with its size, date and phone`() =
        runBlocking {
            upload()
            val newest = upload()
            drive.put("other.bin", byteArrayOf(1), emptyMap())

            val file = requireNotNull(restorer().latestBackup(EMAIL))

            assertEquals(newest.id, file.id)
            assertEquals(newest.bytes.size.toLong(), file.sizeBytes)
            assertEquals(newest.createdTime, file.createdAt)
            assertEquals("Pixel 9", file.device)
        }

    @Test
    fun `an empty app folder offers nothing`() =
        runBlocking {
            assertNull(restorer().latestBackup(EMAIL))
        }

    @Test
    fun `a format 1 backup restores its rows, preferences and backup settings`() =
        runBlocking {
            val stored = upload()
            val restorer = restorer()

            val opened = restorer.openLatest()
            assertEquals(6, opened.summary.entries)
            assertEquals(LocalDate.of(2026, 8, 3), opened.summary.firstDate)
            restorer.restore(EMAIL, opened)

            val parsed = requireNotNull(replaced)
            val lossless = requireNotNull(parsed.lossless)
            assertEquals(6, lossless.dayEntries.size)
            assertEquals(listOf("fixture-d4"), lossless.tombstones.map { it.id })
            assertEquals("COPPER_IUD", lossless.contraception.single().method)
            assertEquals("FULL", lossless.settings?.trackingLevel)
            assertEquals("synthetic note", lossless.dayEntries.getValue("2026-08-05").notes)

            val prefs = uiPrefs.current()
            assertEquals(ThemeScheme.SAGE, prefs.theme)
            assertEquals(AppearanceMode.DARK, prefs.appearance)
            assertFalse(prefs.hideInRecents)
            assertEquals(5, prefs.relockTimeoutMinutes)
            assertTrue(prefs.onboardingCompleted)

            val config = store.current()
            assertTrue(config.enabled)
            assertEquals(EMAIL, config.accountEmail)
            assertEquals(stored.createdTime, config.lastSuccessAt)
            assertEquals(config, scheduler.applied.last())

            // The kept key seals new backups the same password opens.
            val next = EncryptedContainer.seal("next".toByteArray(), requireNotNull(keys.load()))
            assertArrayEquals("next".toByteArray(), EncryptedContainer.open(next, PASSWORD.toCharArray()))
        }

    @Test
    fun `older backups go through the format migration chain`() =
        runBlocking {
            upload()
            // A test-only format 2 that moved notes; the chain must upgrade the stored version 1 file.
            val rewriteNotes =
                ExportMigration(from = 1) { doc ->
                    val entries =
                        doc.getValue("dayEntries").jsonArray.map { entry ->
                            val fields = entry.jsonObject.toMutableMap()
                            fields["notes"]?.let { fields["notes"] = JsonPrimitive("${it.jsonPrimitive.content} (v2)") }
                            JsonObject(fields)
                        }
                    JsonObject(doc + ("dayEntries" to JsonArray(entries)))
                }

            val opened = restorer(ExportMigrations(currentVersion = 2, steps = listOf(rewriteNotes))).openLatest()

            assertEquals(
                "synthetic note (v2)",
                opened.parsed.lossless
                    ?.dayEntries
                    ?.getValue("2026-08-05")
                    ?.notes,
            )
        }

    @Test
    fun `a backup from a newer Mensis is refused`() {
        upload(fixture.replace("\"formatVersion\": 1", "\"formatVersion\": 99"))
        assertEquals(RestoreError.NEWER_VERSION, failure { restorer().openLatest() })
    }

    @Test
    fun `a wrong password is reported and nothing is written`() {
        upload()
        assertEquals(RestoreError.WRONG_PASSWORD, failure { restorer().openLatest("not the password") })
        assertNull(replaced)
        assertNull(keys.key)
    }

    @Test
    fun `a download cut short is damaged, not a wrong password`() {
        val stored = upload()
        val restorer = restorer()
        val file = runBlocking { requireNotNull(restorer.latestBackup(EMAIL)) }
        drive.files[stored.id] =
            FakeDriveServer.StoredFile(
                stored.id,
                stored.name,
                stored.bytes.copyOf(stored.bytes.size - 10),
                stored.createdTime,
                stored.appProperties,
            )
        assertEquals(RestoreError.DAMAGED, failure { restorer.download(EMAIL, file) })
    }

    @Test
    fun `a download altered on the way is damaged`() {
        val stored = upload()
        val restorer = restorer()
        val file = runBlocking { requireNotNull(restorer.latestBackup(EMAIL)) }
        val altered = stored.bytes.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }
        drive.files[stored.id] =
            FakeDriveServer.StoredFile(stored.id, stored.name, altered, stored.createdTime, stored.appProperties)
        assertEquals(RestoreError.DAMAGED, failure { restorer.download(EMAIL, file) })
    }

    @Test
    fun `bytes that are not a Mensis container, or too short to be one, are damaged`() {
        val file = BackupFileInfo("x", BackupPayload.FILE_NAME, 0, Fixtures.clock.instant(), null)
        val restorer = restorer()
        assertEquals(
            RestoreError.DAMAGED,
            failure { restorer.open(file, fixture.toByteArray(), PASSWORD.toCharArray()) },
        )
        val truncated =
            EncryptedContainer
                .seal(
                    fixture.toByteArray(),
                    PASSWORD.toCharArray(),
                    Fixtures.FAST_KDF,
                ).copyOf(40)
        assertEquals(RestoreError.DAMAGED, failure { restorer.open(file, truncated, PASSWORD.toCharArray()) })
    }

    @Test
    fun `a revoked grant during restore asks to sign in again`() {
        upload()
        tokens.reconnectRequired = true
        assertEquals(RestoreError.RECONNECT, failure { restorer().latestBackup(EMAIL) })
    }

    @Test
    fun `no connection is a network error`() {
        val offline =
            RestoreManager(
                drives = { DriveAppDataClient(OkHttpClient(), tokens, "http://127.0.0.1:1/".toHttpUrl()) },
                snapshots = { Fixtures.export() },
                replaceAll = { error("not reached") },
                uiPrefsStore = uiPrefs,
                keys = keys,
                store = store,
                scheduler = scheduler,
                clock = Fixtures.clock,
            )
        assertEquals(RestoreError.NETWORK, failure { offline.latestBackup(EMAIL) })
    }
}
