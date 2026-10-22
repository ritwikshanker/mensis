package com.deutschdreamers.mensis.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.BuildConfig
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.RadioRow
import com.deutschdreamers.mensis.ui.components.SectionHeader
import com.deutschdreamers.mensis.ui.components.SettingsRow
import com.deutschdreamers.mensis.ui.components.SwitchRow
import com.deutschdreamers.mensis.ui.navigation.About
import com.deutschdreamers.mensis.ui.navigation.Backup
import com.deutschdreamers.mensis.ui.navigation.Contraception
import com.deutschdreamers.mensis.ui.navigation.Export
import com.deutschdreamers.mensis.ui.navigation.Import
import com.deutschdreamers.mensis.ui.navigation.MeasurementUnits
import com.deutschdreamers.mensis.ui.navigation.MensisNavKey
import com.deutschdreamers.mensis.ui.navigation.Mode
import com.deutschdreamers.mensis.ui.navigation.Reminders
import com.deutschdreamers.mensis.ui.navigation.Report
import com.deutschdreamers.mensis.ui.navigation.Theme
import com.deutschdreamers.mensis.ui.navigation.TrackLevel
import com.deutschdreamers.mensis.ui.screens.deleteall.DeleteAllDialog
import com.deutschdreamers.mensis.ui.screens.deleteall.DeleteAllViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigate: (MensisNavKey) -> Unit,
    onDataDeleted: () -> Unit,
    deleteViewModel: DeleteAllViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDelete by rememberSaveable { mutableStateOf(false) }
    val current = state
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    SettingsScreenContent(
        state = current,
        onNavigate = onNavigate,
        onLockEnabled = viewModel::setLockEnabled,
        onHideInRecents = viewModel::setHideInRecents,
        onRelockTimeout = viewModel::setRelockTimeout,
        onDeleteAll = { showDelete = true },
    )
    if (showDelete) {
        val deleteState by deleteViewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(deleteState.deleted) { if (deleteState.deleted) onDataDeleted() }
        DeleteAllDialog(
            state = deleteState,
            onDeleteDriveChange = deleteViewModel::setDeleteDrive,
            onConfirm = deleteViewModel::delete,
            onDismiss = {
                showDelete = false
                deleteViewModel.reset()
            },
        )
    }
}

/** Design §7 settings list. */
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onNavigate: (MensisNavKey) -> Unit,
    onLockEnabled: (Boolean) -> Unit,
    onHideInRecents: (Boolean) -> Unit,
    onRelockTimeout: (Int) -> Unit,
    onDeleteAll: () -> Unit,
) {
    var showRelockDialog by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        MensisTopBar(title = stringResource(R.string.tab_settings))
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionHeader(stringResource(R.string.settings_section_cycle))
            SettingsRow(
                if (state.pregnancyMode) R.drawable.ms_child_care else R.drawable.ms_timeline,
                stringResource(R.string.settings_mode),
                state.modeSummary,
            ) {
                onNavigate(Mode)
            }
            SettingsRow(R.drawable.ms_checklist, stringResource(R.string.settings_track), state.trackSummary) {
                onNavigate(TrackLevel)
            }
            SettingsRow(
                R.drawable.ms_medication,
                stringResource(R.string.settings_contraception),
                state.contraceptionSummary,
            ) {
                onNavigate(Contraception)
            }
            SettingsRow(R.drawable.ms_straighten, stringResource(R.string.settings_units), state.unitsSummary) {
                onNavigate(MeasurementUnits)
            }
            SettingsRow(
                R.drawable.ms_notifications,
                stringResource(R.string.settings_reminders),
                state.remindersSummary,
            ) {
                onNavigate(Reminders)
            }
            SectionHeader(stringResource(R.string.settings_section_privacy))
            SwitchRow(
                icon = R.drawable.ms_fingerprint,
                title = stringResource(R.string.settings_app_lock),
                supporting =
                    if (state.lockAvailable) {
                        stringResource(R.string.settings_app_lock_supporting)
                    } else {
                        stringResource(R.string.settings_app_lock_unavailable)
                    },
                checked = state.lockEnabled,
                onCheckedChange = onLockEnabled,
                enabled = state.lockAvailable,
            )
            if (state.lockEnabled && state.lockAvailable) {
                SettingsRow(
                    icon = R.drawable.ms_schedule,
                    title = stringResource(R.string.settings_relock),
                    supporting = SettingsViewModel.relockLabel(state.relockTimeoutMinutes),
                    showChevron = false,
                    onClick = { showRelockDialog = true },
                )
            }
            SwitchRow(
                icon = R.drawable.ms_visibility_off,
                title = stringResource(R.string.settings_hide_recents),
                supporting = stringResource(R.string.settings_hide_recents_supporting),
                checked = state.hideInRecents,
                onCheckedChange = onHideInRecents,
            )
            SectionHeader(stringResource(R.string.settings_section_data))
            SettingsRow(R.drawable.ms_cloud_upload, stringResource(R.string.settings_backup), state.backupSummary) {
                onNavigate(Backup())
            }
            SettingsRow(
                R.drawable.ms_download,
                stringResource(R.string.settings_export),
                stringResource(R.string.settings_export_supporting),
            ) {
                onNavigate(Export)
            }
            SettingsRow(
                R.drawable.ms_upload,
                stringResource(R.string.settings_import),
                stringResource(R.string.settings_import_supporting),
            ) {
                onNavigate(Import)
            }
            SettingsRow(
                R.drawable.ms_description,
                stringResource(R.string.settings_report),
                stringResource(R.string.settings_report_supporting),
            ) {
                onNavigate(Report)
            }
            SettingsRow(
                icon = R.drawable.ms_delete_forever,
                title = stringResource(R.string.settings_delete_all),
                supporting = stringResource(R.string.settings_delete_all_supporting),
                titleColor = MaterialTheme.colorScheme.error,
                iconTint = MaterialTheme.colorScheme.error,
                showChevron = false,
                onClick = onDeleteAll,
            )
            SectionHeader(stringResource(R.string.settings_section_appearance))
            SettingsRow(R.drawable.ms_palette, stringResource(R.string.settings_theme), state.themeSummary) {
                onNavigate(Theme)
            }
            SettingsRow(
                icon = R.drawable.ms_info,
                title = stringResource(R.string.settings_about),
                supporting = stringResource(R.string.settings_about_supporting, BuildConfig.VERSION_NAME),
                onClick = { onNavigate(About) },
            )
        }
    }
    if (showRelockDialog) {
        RelockDialog(
            current = state.relockTimeoutMinutes,
            onDismiss = { showRelockDialog = false },
            onSelect = { minutes ->
                showRelockDialog = false
                onRelockTimeout(minutes)
            },
        )
    }
}

@Composable
private fun RelockDialog(
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_relock)) },
        text = {
            Column {
                for (option in SettingsViewModel.RELOCK_OPTIONS) {
                    RadioRow(
                        title = SettingsViewModel.relockLabel(option),
                        supporting = null,
                        selected = option == current,
                        onSelect = { onSelect(option) },
                        horizontalPadding = 0.dp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
