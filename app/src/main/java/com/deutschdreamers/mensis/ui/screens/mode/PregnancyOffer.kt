package com.deutschdreamers.mensis.ui.screens.mode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.ui.components.MensisDatePickerDialog
import com.deutschdreamers.mensis.ui.components.MsIcon
import java.time.LocalDate

/**
 * Design §4 item 8: a positive pregnancy test offers pregnancy mode. [lmp] is the latest period start
 * on or before the test, or null when none is logged (the user then picks the day).
 */
data class PregnancyOffer(
    val testDate: LocalDate,
    val lmp: LocalDate?,
) {
    companion object {
        /**
         * What saving a positive test on [testDate] offers: nothing in pregnancy mode, and nothing when the
         * day already held a positive test (re-saving it must not ask again).
         */
        fun onPositive(
            mode: TrackerMode,
            before: TestResult?,
            testDate: LocalDate,
            lmp: LocalDate?,
        ): PregnancyOffer? =
            PregnancyOffer(testDate, lmp).takeIf { mode == TrackerMode.CYCLE && before != TestResult.POSITIVE }

        val Saver: Saver<PregnancyOffer?, Any> =
            listSaver(
                save = { value ->
                    if (value == null) emptyList() else listOf(value.testDate.toString(), value.lmp?.toString() ?: "")
                },
                restore = { list ->
                    if (list.isEmpty()) {
                        null
                    } else {
                        PregnancyOffer(
                            testDate = LocalDate.parse(list[0]),
                            lmp = list[1].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                        )
                    }
                },
            )
    }
}

@Composable
fun PregnancyOfferDialog(
    offer: PregnancyOffer,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var lmp by rememberSaveable { mutableStateOf(offer.lmp) }
    var picking by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { MsIcon(resId = R.drawable.ms_child_care, contentDescription = null) },
        title = { Text(stringResource(R.string.pregnancy_offer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.pregnancy_offer_body), style = MaterialTheme.typography.bodyMedium)
                LmpRow(lmp = lmp, onClick = { picking = true })
            }
        },
        confirmButton = {
            Button(onClick = { lmp?.let(onConfirm) }, enabled = lmp != null) {
                Text(stringResource(R.string.pregnancy_offer_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pregnancy_offer_dismiss)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    if (picking) {
        MensisDatePickerDialog(
            initial = lmp ?: offer.testDate,
            latest = offer.testDate,
            onDismiss = { picking = false },
            onConfirm = { date ->
                picking = false
                lmp = date
            },
        )
    }
}
