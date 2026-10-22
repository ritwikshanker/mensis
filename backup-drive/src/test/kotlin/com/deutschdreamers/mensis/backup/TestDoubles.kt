package com.deutschdreamers.mensis.backup

import android.app.Activity
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.deutschdreamers.mensis.backup.auth.AuthorizationOutcome
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.backup.auth.GoogleAccountGateway
import com.deutschdreamers.mensis.backup.auth.SignInResult
import com.deutschdreamers.mensis.backup.drive.AccessTokenSource
import com.deutschdreamers.mensis.backup.drive.DriveAppDataClient
import com.deutschdreamers.mensis.backup.drive.ReconnectRequiredException
import com.deutschdreamers.mensis.data.crypto.ContainerKey
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.crypto.KdfParams
import com.deutschdreamers.mensis.data.db.StoredRows
import com.deutschdreamers.mensis.data.db.toEntity
import com.deutschdreamers.mensis.data.export.ExportFiles
import com.deutschdreamers.mensis.data.export.ExportedApp
import com.deutschdreamers.mensis.data.export.MensisExport
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow as KFlow

/** Synthetic data and in-memory collaborators for the backup tests. No real data, ever. */
object Fixtures {
    /** Cheap Argon2 parameters; files carry them in the header, so opening needs nothing else. */
    val FAST_KDF = KdfParams(memoryKib = 1_024, iterations = 1, parallelism = 1)
    const val PASSWORD = "synthetic backup password"
    const val EMAIL = "someone@example.com"
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)

    fun key(password: String = PASSWORD): ContainerKey = EncryptedContainer.deriveKey(password.toCharArray(), FAST_KDF)

    fun export(
        vararg flows: Pair<String, Flow>,
        exportedAt: String = "2026-10-21T12:00:00Z",
    ): MensisExport {
        val rows =
            flows.map { (date, flow) ->
                DayEntry(
                    LocalDate.parse(date),
                    flow = flow,
                ).toEntity("id-$date", "2026-10-01T08:00:00Z", "2026-10-01T08:00:00Z")
            }
        return ExportFiles.snapshot(
            StoredRows(rows, emptyList(), null),
            null,
            ExportedApp("test", 1),
            Instant.parse(exportedAt),
        )
    }

    fun uiPrefsStore(dir: File): UiPrefsStore =
        UiPrefsStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                File(dir, "ui_prefs.preferences_pb")
            },
        )

    fun statusStore(
        dir: File,
        availability: BackupAvailability = BackupAvailability.AVAILABLE,
    ): BackupStatusStore =
        BackupStatusStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                File(dir, "backup_prefs.preferences_pb")
            },
            clock,
        ) { availability }
}

class InMemoryKeyStore(
    var key: ContainerKey? = null,
) : BackupKeyStore {
    /** Hands out copies, since callers destroy the keys they load. */
    override fun load(): ContainerKey? = key?.let { ContainerKey(it.bytes.copyOf(), it.salt.copyOf(), it.params) }

    override fun store(key: ContainerKey) {
        this.key = ContainerKey(key.bytes.copyOf(), key.salt.copyOf(), key.params)
    }

    override fun delete() {
        key = null
    }
}

class FakeTokens(
    var current: String = "token-1",
) : AccessTokenSource {
    val invalidated = mutableListOf<String>()
    var reconnectRequired = false

    override suspend fun token(): String = if (reconnectRequired) throw ReconnectRequiredException() else current

    override suspend fun invalidate(token: String) {
        invalidated += token
        current = "token-${invalidated.size + 1}"
    }
}

class FakeNotifier : ReconnectNotifier {
    var posted = 0
    var cancelled = 0

    override fun notifyReconnect() {
        posted++
    }

    override fun cancel() {
        cancelled++
    }
}

class FakeScheduler : BackupScheduler {
    val applied = mutableListOf<BackupConfig>()
    var manualRequests = 0
    var cancelled = 0

    override fun apply(config: BackupConfig) {
        applied += config
    }

    override fun manualRuns(): KFlow<ManualBackupState> = flowOf(ManualBackupState.None)

    override fun cancelAll() {
        cancelled++
    }

    override fun backUpNow() {
        manualRequests++
    }
}

/** A Drive client against [server] with [tokens]; every account shares the one fake folder. */
fun driveFactory(
    server: MockWebServer,
    tokens: FakeTokens,
): DriveFactory = DriveFactory { DriveAppDataClient(OkHttpClient(), tokens, server.url("/")) }

class FakeGateway(
    var availability: BackupAvailability = BackupAvailability.AVAILABLE,
) : GoogleAccountGateway {
    val disconnected = mutableListOf<String>()

    override fun availability(): BackupAvailability = availability

    override suspend fun signIn(activity: Activity): SignInResult = SignInResult.SignedIn(Fixtures.EMAIL)

    override suspend fun authorize(email: String): AuthorizationOutcome = AuthorizationOutcome.Granted("token-1")

    override fun tokenFromConsent(data: Intent?): String? = "token-1"

    override suspend fun clearToken(token: String) = Unit

    override suspend fun disconnect(email: String) {
        disconnected += email
    }
}
