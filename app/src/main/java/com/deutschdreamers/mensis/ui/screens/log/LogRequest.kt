package com.deutschdreamers.mensis.ui.screens.log

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.deutschdreamers.mensis.domain.model.Flow
import java.time.LocalDate

/** Why the daily log opened: which day, whether the pregnancy test goes first (Late state), and a preset flow. */
data class LogRequest(
    val date: LocalDate,
    val testsFirst: Boolean = false,
    val presetFlow: Flow? = null,
) {
    companion object {
        val Saver: Saver<LogRequest?, Any> =
            listSaver(
                save = { value ->
                    if (value ==
                        null
                    ) {
                        emptyList()
                    } else {
                        listOf(value.date.toString(), value.testsFirst, value.presetFlow?.name ?: "")
                    }
                },
                restore = { list ->
                    if (list.isEmpty()) {
                        null
                    } else {
                        LogRequest(
                            date = LocalDate.parse(list[0] as String),
                            testsFirst = list[1] as Boolean,
                            presetFlow = (list[2] as String).takeIf { it.isNotEmpty() }?.let { Flow.valueOf(it) },
                        )
                    }
                },
            )
    }
}
