package com.deutschdreamers.mensis.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.deutschdreamers.mensis.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Material date picker returning a [LocalDate]; selections are interpreted in UTC as the picker does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MensisDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis =
                (initial ?: LocalDate.now())
                    .atStartOfDay(
                        ZoneOffset.UTC,
                    ).toInstant()
                    .toEpochMilli(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** Material time picker returning a [LocalTime]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MensisTimePickerDialog(
    initial: LocalTime?,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val start = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
