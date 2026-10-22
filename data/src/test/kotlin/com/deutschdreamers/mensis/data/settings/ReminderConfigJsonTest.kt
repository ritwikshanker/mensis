package com.deutschdreamers.mensis.data.settings

import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import com.deutschdreamers.mensis.domain.reminders.ReminderOption
import com.deutschdreamers.mensis.domain.reminders.ReminderSetting
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalTime

class ReminderConfigJsonTest {
    @Test
    fun `every setting survives a round trip`() {
        val config =
            ReminderConfig(
                settings =
                    ReminderType.entries.associateWith { type ->
                        ReminderSetting(
                            enabled = type.ordinal % 2 == 0,
                            time = LocalTime.of(6 + type.ordinal, 15),
                            option = ReminderOption.entries[type.ordinal],
                        )
                    },
                discreet = false,
                discreetTitle = "Water the plants",
                discreetBody = "Balcony",
            )
        val text = ReminderConfigJson.encode(config)
        assertEquals(config, ReminderConfigJson.decode(text, discreet = false))
    }

    @Test
    fun `the stored form is the documented one`() {
        val config =
            ReminderConfig().update(ReminderType.PERIOD) {
                it.copy(enabled = true, time = LocalTime.of(9, 0), option = ReminderOption.TWO_DAYS_BEFORE)
            }
        assertEquals(
            """{"period":{"enabled":true,"time":"09:00","option":"two_days_before"}}""",
            ReminderConfigJson.encode(config),
        )
        assertEquals("{}", ReminderConfigJson.encode(ReminderConfig()))
    }

    @Test
    fun `unknown values fall back to the defaults instead of failing`() {
        val text =
            """
            {"period": {"enabled": true, "time": "25:99", "option": "EVERY_FULL_MOON", "lead": 2},
             "pill": "yes", "future": {"enabled": true}, "discreetTitle": 3}
            """.trimIndent()
        val config = ReminderConfigJson.decode(text, discreet = true)
        assertEquals(mapOf(ReminderType.PERIOD to ReminderSetting(enabled = true)), config.settings)
        assertEquals(null, config.discreetTitle)
        assertEquals(true, config.discreet)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "{}", "[]", "not json", "null"])
    fun `empty or broken text reads as no reminders`(text: String) {
        assertEquals(ReminderConfig(discreet = true), ReminderConfigJson.decode(text, discreet = true))
    }
}
