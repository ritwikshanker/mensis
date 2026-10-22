package com.deutschdreamers.mensis.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.data.backup.BackupError
import com.deutschdreamers.mensis.data.backup.BackupStatus
import com.deutschdreamers.mensis.data.backup.BackupStatusSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Design §11 frequency (Monthly added from the brief). Manual means no scheduled runs. */
enum class BackupFrequency(
    val days: Long?,
) {
    DAILY(1),
    WEEKLY(7),
    MONTHLY(30),
    MANUAL(null),
}

/**
 * Backup settings and the outcome of the last run. No health data and no secrets: the key lives in
 * [BackupKeyStore], the password nowhere.
 */
data class BackupConfig(
    val enabled: Boolean = false,
    val accountEmail: String? = null,
    val frequency: BackupFrequency = BackupFrequency.WEEKLY,
    val wifiOnly: Boolean = true,
    /** When the newest backup on Drive was made, its size and the phone that made it. */
    val lastSuccessAt: Instant? = null,
    val lastSuccessBytes: Long? = null,
    val lastSuccessDevice: String? = null,
    /** The last run that found Drive up to date, with or without uploading. */
    val lastCheckedAt: Instant? = null,
    val lastError: BackupError? = null,
    val lastErrorAt: Instant? = null,
    val lastFingerprint: String? = null,
    val needsReconnect: Boolean = false,
    val passwordSetAt: Instant? = null,
    /** Key ids of passwords replaced by "Change backup password"; backups under them are pruned. */
    val retiredKeyIds: Set<String> = emptySet(),
)

/** Maps the stored config to what Today and Settings show. Pure, so the precedence is unit-tested. */
object BackupStatuses {
    /** A backup older than this many intervals counts as overdue (plan: "> 2 × interval"). */
    const val OVERDUE_INTERVALS: Long = 2

    fun of(
        config: BackupConfig,
        availability: BackupAvailability,
        now: Instant,
    ): BackupStatus =
        when {
            availability != BackupAvailability.AVAILABLE -> BackupStatus.Unavailable
            !config.enabled -> BackupStatus.Off
            config.needsReconnect -> BackupStatus.NeedsReconnect
            config.lastError != null -> BackupStatus.Failing(config.lastError, config.wifiOnly)
            else -> overdue(config, now) ?: BackupStatus.Idle(config.lastSuccessAt)
        }

    private fun overdue(
        config: BackupConfig,
        now: Instant,
    ): BackupStatus.Overdue? {
        val days = config.frequency.days ?: return null
        val last = listOfNotNull(config.lastSuccessAt, config.lastCheckedAt).maxOrNull() ?: config.passwordSetAt
        last ?: return null
        val age = Duration.between(last, now)
        return if (age > Duration.ofDays(days * OVERDUE_INTERVALS)) {
            BackupStatus.Overdue(age.toDays().toInt())
        } else {
            null
        }
    }
}

/** Backup settings in their own unencrypted DataStore, readable by the worker without the UI. */
class BackupStatusStore(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val availability: () -> BackupAvailability,
) : BackupStatusSource {
    val config: Flow<BackupConfig> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it.toConfig() }

    override val status: Flow<BackupStatus> =
        config.map { BackupStatuses.of(it, availability(), Instant.now(clock)) }

    suspend fun current(): BackupConfig = config.first()

    suspend fun update(transform: (BackupConfig) -> BackupConfig) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toConfig())
            prefs.clear()
            prefs.write(updated)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toConfig(): BackupConfig {
        val defaults = BackupConfig()
        return BackupConfig(
            enabled = this[ENABLED] ?: defaults.enabled,
            accountEmail = this[ACCOUNT],
            frequency = BackupFrequency.entries.firstOrNull { it.name == this[FREQUENCY] } ?: defaults.frequency,
            wifiOnly = this[WIFI_ONLY] ?: defaults.wifiOnly,
            lastSuccessAt = this[LAST_SUCCESS_AT]?.let(Instant::ofEpochMilli),
            lastSuccessBytes = this[LAST_SUCCESS_BYTES],
            lastSuccessDevice = this[LAST_SUCCESS_DEVICE],
            lastCheckedAt = this[LAST_CHECKED_AT]?.let(Instant::ofEpochMilli),
            lastError = BackupError.entries.firstOrNull { it.name == this[LAST_ERROR] },
            lastErrorAt = this[LAST_ERROR_AT]?.let(Instant::ofEpochMilli),
            lastFingerprint = this[LAST_FINGERPRINT],
            needsReconnect = this[NEEDS_RECONNECT] ?: defaults.needsReconnect,
            passwordSetAt = this[PASSWORD_SET_AT]?.let(Instant::ofEpochMilli),
            retiredKeyIds = this[RETIRED_KEY_IDS] ?: defaults.retiredKeyIds,
        )
    }

    private fun MutablePreferences.write(config: BackupConfig) {
        this[ENABLED] = config.enabled
        config.accountEmail?.let { this[ACCOUNT] = it }
        this[FREQUENCY] = config.frequency.name
        this[WIFI_ONLY] = config.wifiOnly
        config.lastSuccessAt?.let { this[LAST_SUCCESS_AT] = it.toEpochMilli() }
        config.lastSuccessBytes?.let { this[LAST_SUCCESS_BYTES] = it }
        config.lastSuccessDevice?.let { this[LAST_SUCCESS_DEVICE] = it }
        config.lastCheckedAt?.let { this[LAST_CHECKED_AT] = it.toEpochMilli() }
        config.lastError?.let { this[LAST_ERROR] = it.name }
        config.lastErrorAt?.let { this[LAST_ERROR_AT] = it.toEpochMilli() }
        config.lastFingerprint?.let { this[LAST_FINGERPRINT] = it }
        this[NEEDS_RECONNECT] = config.needsReconnect
        config.passwordSetAt?.let { this[PASSWORD_SET_AT] = it.toEpochMilli() }
        if (config.retiredKeyIds.isNotEmpty()) this[RETIRED_KEY_IDS] = config.retiredKeyIds
    }

    companion object {
        const val FILE_NAME: String = "backup_prefs"
        private val ENABLED = booleanPreferencesKey("enabled")
        private val ACCOUNT = stringPreferencesKey("account_email")
        private val FREQUENCY = stringPreferencesKey("frequency")
        private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val LAST_SUCCESS_AT = longPreferencesKey("last_success_at")
        private val LAST_SUCCESS_BYTES = longPreferencesKey("last_success_bytes")
        private val LAST_SUCCESS_DEVICE = stringPreferencesKey("last_success_device")
        private val LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
        private val LAST_ERROR = stringPreferencesKey("last_error")
        private val LAST_ERROR_AT = longPreferencesKey("last_error_at")
        private val LAST_FINGERPRINT = stringPreferencesKey("last_fingerprint")
        private val NEEDS_RECONNECT = booleanPreferencesKey("needs_reconnect")
        private val PASSWORD_SET_AT = longPreferencesKey("password_set_at")
        private val RETIRED_KEY_IDS = stringSetPreferencesKey("retired_key_ids")
    }
}
