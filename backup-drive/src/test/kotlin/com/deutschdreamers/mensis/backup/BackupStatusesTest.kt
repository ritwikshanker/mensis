package com.deutschdreamers.mensis.backup

import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.data.backup.BackupError
import com.deutschdreamers.mensis.data.backup.BackupStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.util.stream.Stream

class BackupStatusesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `config maps to the status Today and Settings show`(
        @Suppress("UNUSED_PARAMETER") name: String,
        config: BackupConfig,
        availability: BackupAvailability,
        expected: BackupStatus,
    ) {
        assertEquals(expected, BackupStatuses.of(config, availability, NOW))
    }

    companion object {
        private val NOW = Instant.parse("2026-10-21T12:00:00Z")
        private val on = BackupConfig(enabled = true, accountEmail = "someone@example.com")

        private fun daysAgo(days: Long): Instant = NOW.minusSeconds(days * 86_400)

        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("no Play services", on, BackupAvailability.NO_PLAY_SERVICES, BackupStatus.Unavailable),
                Arguments.of("not configured", on, BackupAvailability.NOT_CONFIGURED, BackupStatus.Unavailable),
                Arguments.of("off", BackupConfig(), BackupAvailability.AVAILABLE, BackupStatus.Off),
                Arguments.of(
                    "reconnect before failure",
                    on.copy(needsReconnect = true, lastError = BackupError.NETWORK),
                    BackupAvailability.AVAILABLE,
                    BackupStatus.NeedsReconnect,
                ),
                Arguments.of(
                    "failing",
                    on.copy(lastError = BackupError.NETWORK, lastSuccessAt = daysAgo(1)),
                    BackupAvailability.AVAILABLE,
                    BackupStatus.Failing(BackupError.NETWORK, wifiOnly = true),
                ),
                Arguments.of(
                    "weekly, 10 days ago",
                    on.copy(lastSuccessAt = daysAgo(10)),
                    BackupAvailability.AVAILABLE,
                    BackupStatus.Idle(daysAgo(10)),
                ),
                Arguments.of(
                    "weekly, 15 days ago",
                    on.copy(lastSuccessAt = daysAgo(15)),
                    BackupAvailability.AVAILABLE,
                    BackupStatus.Overdue(15),
                ),
                Arguments.of(
                    "old upload but checked recently",
                    on.copy(lastSuccessAt = daysAgo(40), lastCheckedAt = daysAgo(2)),
                    BackupAvailability.AVAILABLE,
                    BackupStatus.Idle(daysAgo(40)),
                ),
                Arguments.of(
                    "daily, 3 days ago",
                    on.copy(frequency = BackupFrequency.DAILY, lastSuccessAt = daysAgo(3)),
                    BackupAvailability.AVAILABLE,
                    BackupStatus.Overdue(3),
                ),
                Arguments.of(
                    "manual is never overdue",
                    on.copy(frequency = BackupFrequency.MANUAL, lastSuccessAt = daysAgo(300)),
                    BackupAvailability.AVAILABLE,
                    BackupStatus.Idle(daysAgo(300)),
                ),
                Arguments.of(
                    "never succeeded since the password was set 20 days ago",
                    on.copy(passwordSetAt = daysAgo(20)),
                    BackupAvailability.AVAILABLE,
                    BackupStatus.Overdue(20),
                ),
            )
    }
}
