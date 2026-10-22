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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.BuildConfig
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.RadioRow
import com.deutschdreamers.mensis.ui.components.SectionHeader
import com.deutschdreamers.mensis.ui.components.SettingsRow
import com.deutschdreamers.mensis.ui.components.SwitchRow
import com.deutschdreamers.mensis.ui.navigation.ComingSoon
import com.deutschdreamers.mensis.ui.navigation.Contraception
import com.deutschdreamers.mensis.ui.navigation.LaterScreen
import com.deutschdreamers.mensis.ui.navigation.MensisNavKey
import com.deutschdreamers.mensis.ui.navigation.TrackLevel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigate: (MensisNavKey) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    )
}

/** Design §7 settings list. Rows whose screens come later open a placeholder. */
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onNavigate: (MensisNavKey) -> Unit,
    onLockEnabled: (Boolean) -> Unit,
    onHideInRecents: (Boolean) -> Unit,
    onRelockTimeout: (Int) -> Unit,
) {
    var showRelockDialog by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        MensisTopBar(title = stringResource(R.string.tab_settings))
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionHeader(stringResource(R.string.settings_section_cycle))
            SettingsRow(R.drawable.ms_timeline, stringResource(R.string.settings_mode), state.modeSummary) {
                onNavigate(ComingSoon(LaterScreen.MODE))
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
                onNavigate(ComingSoon(LaterScreen.UNITS))
            }
            SettingsRow(
                R.drawable.ms_notifications,
                stringResource(R.string.settings_reminders),
                stringResource(R.string.settings_reminders_off),
            ) {
                onNavigate(ComingSoon(LaterScreen.REMINDERS))
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
                onNavigate(ComingSoon(LaterScreen.BACKUP))
            }
            SettingsRow(
                R.drawable.ms_download,
                stringResource(R.string.settings_export),
                stringResource(R.string.settings_export_supporting),
            ) {
                onNavigate(ComingSoon(LaterScreen.EXPORT))
            }
            SettingsRow(
                R.drawable.ms_upload,
                stringResource(R.string.settings_import),
                stringResource(R.string.settings_import_supporting),
            ) {
                onNavigate(ComingSoon(LaterScreen.IMPORT))
            }
            SettingsRow(
                R.drawable.ms_description,
                stringResource(R.string.settings_report),
                stringResource(R.string.settings_report_supporting),
            ) {
                onNavigate(ComingSoon(LaterScreen.REPORT))
            }
            SettingsRow(
                icon = R.drawable.ms_delete_forever,
                title = stringResource(R.string.settings_delete_all),
                supporting = stringResource(R.string.settings_delete_all_supporting),
                titleColor = MaterialTheme.colorScheme.error,
                iconTint = MaterialTheme.colorScheme.error,
                showChevron = false,
                onClick = { onNavigate(ComingSoon(LaterScreen.DELETE_ALL)) },
            )
            SectionHeader(stringResource(R.string.settings_section_appearance))
            SettingsRow(R.drawable.ms_palette, stringResource(R.string.settings_theme), state.themeSummary) {
                onNavigate(ComingSoon(LaterScreen.THEME))
            }
            SettingsRow(
                icon = R.drawable.ms_info,
                title = stringResource(R.string.settings_about),
                supporting = stringResource(R.string.settings_about_supporting, BuildConfig.VERSION_NAME),
                onClick = { onNavigate(ComingSoon(LaterScreen.ABOUT)) },
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
