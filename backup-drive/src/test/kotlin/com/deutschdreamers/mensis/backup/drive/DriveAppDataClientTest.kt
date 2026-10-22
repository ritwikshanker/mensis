package com.deutschdreamers.mensis.backup.drive

import com.deutschdreamers.mensis.backup.FakeDriveServer
import com.deutschdreamers.mensis.backup.FakeTokens
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class DriveAppDataClientTest {
    private val drive = FakeDriveServer()
    private val server = MockWebServer().apply { dispatcher = drive }
    private val tokens = FakeTokens()
    private lateinit var client: DriveAppDataClient

    @BeforeEach
    fun start() {
        server.start()
        client = DriveAppDataClient(OkHttpClient(), tokens, server.url("/"))
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    @Test
    fun `list asks only for the app folder and reads every page, newest first`() =
        runBlocking {
            repeat(5) { drive.put("mensis-backup.enc", byteArrayOf(it.toByte()), mapOf("n" to "$it")) }

            val files = client.list()

            assertEquals(listOf("4", "3", "2", "1", "0"), files.map { it.appProperties["n"] })
            assertEquals(3, drive.requests.size)
            val first = drive.requests.first().url
            assertEquals("appDataFolder", first.queryParameter("spaces"))
            assertEquals("createdTime desc", first.queryParameter("orderBy"))
            assertTrue(first.queryParameter("fields")!!.contains("md5Checksum"))
            assertEquals("2", drive.requests[1].url.queryParameter("pageToken"))
            assertEquals("Bearer token-1", drive.requests.first().headers["Authorization"])
        }

    @Test
    fun `upload sends metadata and bytes as multipart into the app folder`() =
        runBlocking {
            val bytes = ByteArray(3_000) { (it % 251).toByte() }

            val file = client.upload("mensis-backup.enc", bytes, mapOf("formatVersion" to "1"))

            val stored = drive.files.getValue(file.id)
            assertArrayEquals(bytes, stored.bytes)
            assertEquals(mapOf("formatVersion" to "1"), stored.appProperties)
            assertEquals(3_000L, file.sizeBytes)
            assertEquals(FakeDriveServer.md5(bytes), file.md5Checksum)
            assertEquals("mensis-backup.enc", file.name)
            assertTrue(file.createdTime.isAfter(Instant.EPOCH))
            val request = drive.requests.single()
            assertTrue(request.headers["Content-Type"]!!.startsWith("multipart/related"))
        }

    @Test
    fun `download returns the stored bytes`() =
        runBlocking {
            val stored = drive.put("mensis-backup.enc", byteArrayOf(1, 2, 3), emptyMap())
            assertArrayEquals(byteArrayOf(1, 2, 3), client.download(stored.id, maxBytes = 1_000))
            assertEquals(
                "media",
                drive.requests
                    .single()
                    .url
                    .queryParameter("alt"),
            )
        }

    @Test
    fun `download refuses files above the size limit`() {
        val stored = drive.put("mensis-backup.enc", ByteArray(2_000), emptyMap())
        val error = assertThrows<DriveHttpException> { runBlocking { client.download(stored.id, maxBytes = 1_000) } }
        assertEquals(413, error.code)
    }

    @Test
    fun `delete removes the file`() =
        runBlocking {
            val stored = drive.put("mensis-backup.enc", byteArrayOf(1), emptyMap())
            client.delete(stored.id)
            assertFalse(stored.id in drive.files)
            assertEquals("DELETE", drive.requests.single().method)
        }

    @Test
    fun `a rejected token is invalidated and the call repeated once`() =
        runBlocking {
            drive.acceptedToken = "token-2"
            assertEquals(emptyList<DriveFile>(), client.list())
            assertEquals(listOf("token-1"), tokens.invalidated)
            assertEquals(2, drive.requests.size)
        }

    @Test
    fun `a token rejected twice fails with 401`() {
        drive.acceptedToken = "never"
        val error = assertThrows<DriveHttpException> { runBlocking { client.list() } }
        assertEquals(401, error.code)
        assertEquals(1, tokens.invalidated.size)
    }

    @Test
    fun `a full Drive is recognised from the error reason`() {
        drive.failNext =
            FakeDriveServer.json(
                403,
                """{"error":{"code":403,"errors":[{"reason":"storageQuotaExceeded","message":"full"}]}}""",
            )
        val error = assertThrows<DriveHttpException> { runBlocking { client.upload("x", byteArrayOf(1), emptyMap()) } }
        assertTrue(error.isStorageFull)
        assertFalse(error.isTransient)
    }

    @Test
    fun `server errors and rate limits are transient`() {
        drive.failNext = FakeDriveServer.json(503, "{}")
        assertTrue(assertThrows<DriveHttpException> { runBlocking { client.list() } }.isTransient)
        drive.failNext =
            FakeDriveServer.json(403, """{"error":{"errors":[{"reason":"userRateLimitExceeded"}]}}""")
        assertTrue(assertThrows<DriveHttpException> { runBlocking { client.list() } }.isTransient)
    }

    @Test
    fun `a token that needs the user stops before any request`() {
        tokens.reconnectRequired = true
        assertThrows<ReconnectRequiredException> { runBlocking { client.list() } }
        assertTrue(drive.requests.isEmpty())
    }
}
