package com.deutschdreamers.mensis.ui.screens.backup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.backup.BackupFrequency
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.ui.components.ContentCard
import com.deutschdreamers.mensis.ui.components.FieldLabel
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.PasswordField
import com.deutschdreamers.mensis.ui.components.SegmentedChoice
import com.deutschdreamers.mensis.ui.components.SettingsRow
import com.deutschdreamers.mensis.ui.components.SwitchRow
import com.deutschdreamers.mensis.ui.screens.onboarding.PasswordStrengthBar
import com.deutschdreamers.mensis.ui.theme.MensisTheme

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onRestore: () -> Unit,
    reconnect: Boolean,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connect = rememberGoogleConnect(viewModel.accounts, viewModel.lock, viewModel::onConnectResult)
    // Today's "Reconnect" lands here and goes straight to Google's screen, once.
    var reconnectHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state?.config?.accountEmail, reconnect) {
        val email = state?.config?.accountEmail
        if (reconnect && !reconnectHandled && email != null) {
            reconnectHandled = true
            connect.connect(email)
        }
    }
    val current = state ?: return
    BackupContent(
        state = current,
        actions =
            BackupActions(
                onBack = onBack,
                onConnect = connect::connect,
                onPassword = viewModel::setPassword,
                onRepeat = viewModel::setRepeat,
                onConfirmPassword = viewModel::confirmPassword,
                onCancelPassword = viewModel::cancelPassword,
                onChangePassword = viewModel::startPasswordChange,
                onBackUpNow = viewModel::backUpNow,
                onFrequency = viewModel::setFrequency,
                onWifiOnly = viewModel::setWifiOnly,
                onRestore = onRestore,
                onTurnOff = viewModel::turnOff,
                onDeleteDrive = viewModel::deleteDriveBackups,
            ),
    )
}

/** Design §11. Stateless so tests and previews can show every state. */
@Composable
fun BackupContent(
    state: BackupUiState,
    actions: BackupActions,
) {
    val busy = state.draft.busy
    // Leaving while the key is derived or the password changes would cancel it halfway.
    BackHandler(enabled = busy != null) {}
    BackHandler(enabled = busy == null && state.draft.passwordMode != null, onBack = actions.onCancelPassword)
    var confirm by remember { mutableStateOf<BackupBusy?>(null) }
    Scaffold(
        topBar = {
            MensisTopBar(
                title = stringResource(R.string.settings_backup),
                onBack = actions.onBack.takeIf { busy == null },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state, actions)
            state.draft.note?.let { NoteBanner(it, state) }
            when {
                state.card == BackupCard.UNAVAILABLE -> Unit
                state.draft.passwordMode != null -> PasswordSection(state, actions)
                !state.config.enabled -> OffSection(actions)
                else -> OnSection(state, actions, onConfirm = { confirm = it })
            }
            if (state.card != BackupCard.UNAVAILABLE) {
                Text(
                    text = stringResource(R.string.backup_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    confirm?.let { kind ->
        ConfirmDialog(
            kind = kind,
            email = state.config.accountEmail.orEmpty(),
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                if (kind == BackupBusy.TURNING_OFF) actions.onTurnOff() else actions.onDeleteDrive()
            },
        )
    }
}

@Composable
private fun StatusCard(
    state: BackupUiState,
    actions: BackupActions,
) {
    val config = state.config
    val teal = MensisTheme.colors.fertile
    val (icon, tint) =
        when (state.card) {
            BackupCard.UNAVAILABLE, BackupCard.OFF ->
                R.drawable.ms_cloud_off to
                    MaterialTheme.colorScheme.onSurfaceVariant
            BackupCard.RUNNING, BackupCard.WAITING -> R.drawable.ms_cloud_sync to MaterialTheme.colorScheme.primary
            BackupCard.RECONNECT -> R.drawable.ms_manage_accounts to MaterialTheme.colorScheme.error
            BackupCard.FAILING -> R.drawable.ms_sync_problem to MaterialTheme.colorScheme.error
            BackupCard.NEVER -> R.drawable.ms_cloud_upload to MaterialTheme.colorScheme.onSurfaceVariant
            BackupCard.DONE, BackupCard.IDLE -> R.drawable.ms_cloud_done to teal
        }
    val lastBackup =
        config.lastSuccessAt?.let { BackupTexts.backedUp(it, state.today, state.zone) }
    val details = BackupTexts.details(config.lastSuccessBytes, config.lastSuccessDevice)
    val (title, body) =
        when (state.card) {
            BackupCard.UNAVAILABLE ->
                stringResource(R.string.backup_off_title) to
                    stringResource(
                        if (state.availability == BackupAvailability.NO_PLAY_SERVICES) {
                            R.string.backup_unavailable_play
                        } else {
                            R.string.backup_unavailable_config
                        },
                    )
            BackupCard.OFF -> stringResource(R.string.backup_off_title) to stringResource(R.string.backup_off_body)
            BackupCard.RUNNING ->
                stringResource(R.string.backup_running_title) to
                    stringResource(R.string.backup_running_body)
            BackupCard.WAITING ->
                stringResource(R.string.backup_waiting) to
                    stringResource(R.string.backup_running_body)
            BackupCard.DONE -> stringResource(R.string.backup_done) to details
            BackupCard.RECONNECT -> stringResource(R.string.backup_reconnect_body) to lastBackup
            BackupCard.FAILING -> BackupTexts.failed(requireNotNull(config.lastError)) to lastBackup
            BackupCard.IDLE -> lastBackup.orEmpty() to details
            BackupCard.NEVER -> stringResource(R.string.backup_not_yet) to null
        }
    ContentCard {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            MsIcon(resId = icon, contentDescription = null, tint = tint)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                body?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.card == BackupCard.RUNNING || state.card == BackupCard.WAITING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
        }
        if (state.card == BackupCard.RECONNECT) {
            Button(onClick = { actions.onConnect(config.accountEmail) }, modifier = Modifier.height(40.dp)) {
                Text(stringResource(R.string.backup_reconnect))
            }
        }
    }
}

@Composable
private fun NoteBanner(
    note: BackupNote,
    state: BackupUiState,
) {
    val text =
        when (note) {
            BackupNote.NO_ACCOUNT -> stringResource(R.string.backup_connect_no_account)
            BackupNote.CONNECT_FAILED -> stringResource(R.string.backup_connect_failed)
            BackupNote.DELETED -> stringResource(R.string.backup_deleted)
            BackupNote.DELETE_FAILED -> stringResource(R.string.backup_delete_failed)
            BackupNote.CHANGE_FAILED ->
                state.config.lastError?.let(BackupTexts::failed)
                    ?: stringResource(R.string.backup_reconnect_body)
        }
    val colors = MaterialTheme.colorScheme
    val problem = note != BackupNote.DELETED
    InfoBanner(
        text = text,
        icon = if (problem) R.drawable.ms_error else R.drawable.ms_check_circle,
        containerColor = if (problem) colors.errorContainer else colors.surfaceContainerHigh,
        contentColor = if (problem) colors.onErrorContainer else colors.onSurface,
        iconTint = if (problem) colors.onErrorContainer else colors.onSurfaceVariant,
    )
}

@Composable
private fun OffSection(actions: BackupActions) {
    Button(onClick = { actions.onConnect(null) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(stringResource(R.string.backup_turn_on))
    }
    Column {
        SettingsRow(
            icon = R.drawable.ms_settings_backup_restore,
            title = stringResource(R.string.backup_restore),
            supporting = null,
            onClick = actions.onRestore,
        )
    }
}

@Composable
private fun OnSection(
    state: BackupUiState,
    actions: BackupActions,
    onConfirm: (BackupBusy) -> Unit,
) {
    val config = state.config
    val running = state.card == BackupCard.RUNNING || state.card == BackupCard.WAITING
    val busy = state.draft.busy
    FilledTonalButton(
        onClick = actions.onBackUpNow,
        enabled = !running && busy == null && !config.needsReconnect,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(stringResource(R.string.backup_now))
    }
    if (busy == BackupBusy.DELETING || busy == BackupBusy.TURNING_OFF) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
        }
    }
    Column {
        AccountRow(
            email = config.accountEmail.orEmpty(),
            enabled = busy == null,
            onChange = { actions.onConnect(null) },
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FieldLabel(stringResource(R.string.backup_frequency))
            Spacer(modifier = Modifier.height(8.dp))
            val labels = BackupFrequency.entries.associateWith { stringResource(it.labelRes) }
            SegmentedChoice(
                options = BackupFrequency.entries,
                selected = config.frequency,
                label = labels::getValue,
                onSelect = actions.onFrequency,
            )
        }
        SwitchRow(
            icon = R.drawable.ms_wifi,
            title = stringResource(R.string.backup_wifi_only),
            supporting = stringResource(R.string.backup_wifi_only_body),
            checked = config.wifiOnly,
            onCheckedChange = actions.onWifiOnly,
        )
        SettingsRow(
            icon = R.drawable.ms_key,
            title = stringResource(R.string.backup_change_password),
            supporting = null,
            enabled = busy == null && !running,
            onClick = actions.onChangePassword,
        )
        SettingsRow(
            icon = R.drawable.ms_settings_backup_restore,
            title = stringResource(R.string.backup_restore),
            supporting = null,
            enabled = busy == null,
            onClick = actions.onRestore,
        )
        SettingsRow(
            icon = R.drawable.ms_cloud_off,
            title = stringResource(R.string.backup_turn_off),
            supporting = stringResource(R.string.backup_turn_off_body),
            enabled = busy == null,
            onClick = { onConfirm(BackupBusy.TURNING_OFF) },
        )
        SettingsRow(
            icon = R.drawable.ms_delete_forever,
            title = stringResource(R.string.backup_delete_drive),
            supporting = stringResource(R.string.backup_delete_drive_body),
            titleColor = MaterialTheme.colorScheme.error,
            iconTint = MaterialTheme.colorScheme.error,
            enabled = busy == null,
            onClick = { onConfirm(BackupBusy.DELETING) },
        )
    }
}

/** Design §11 "Google Drive account (Change)". */
@Composable
private fun AccountRow(
    email: String,
    enabled: Boolean,
    onChange: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MsIcon(
            resId = R.drawable.ms_account_circle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.backup_account), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onChange, enabled = enabled) { Text(stringResource(R.string.backup_account_change)) }
    }
}

@Composable
private fun PasswordSection(
    state: BackupUiState,
    actions: BackupActions,
) {
    val draft = state.draft
    val setup = draft.passwordMode == PasswordMode.SETUP
    val working = draft.busy != null
    Text(
        text = stringResource(if (setup) R.string.backup_setup_title else R.string.backup_change_password),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        text =
            if (setup) {
                stringResource(R.string.backup_setup_connected, draft.setupEmail.orEmpty())
            } else {
                stringResource(R.string.backup_password_change_body)
            },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PasswordField(
            value = draft.password,
            onValueChange = actions.onPassword,
            label =
                stringResource(if (setup) R.string.onboarding_password_label else R.string.backup_password_new),
            supporting = stringResource(R.string.onboarding_password_hint),
        )
        PasswordStrengthBar(draft.password)
        PasswordField(
            value = draft.repeat,
            onValueChange = actions.onRepeat,
            label = stringResource(R.string.export_password_repeat),
            isError = draft.repeat.isNotEmpty() && !state.passwordsMatch,
            supporting =
                stringResource(R.string.export_password_mismatch).takeIf {
                    draft.repeat.isNotEmpty() && !state.passwordsMatch
                },
        )
    }
    InfoBanner(
        text = stringResource(R.string.onboarding_password_warning),
        icon = R.drawable.ms_key_off,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        iconTint = MaterialTheme.colorScheme.onErrorContainer,
    )
    if (working) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
            Text(
                text =
                    stringResource(if (setup) R.string.backup_setting_up else R.string.backup_password_changing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = actions.onConfirmPassword,
            enabled = state.passwordValid && !working,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                stringResource(if (setup) R.string.backup_setup_confirm else R.string.backup_password_change_confirm),
            )
        }
        OutlinedButton(
            onClick = actions.onCancelPassword,
            enabled = !working,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun ConfirmDialog(
    kind: BackupBusy,
    email: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val delete = kind == BackupBusy.DELETING
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            MsIcon(
                resId = if (delete) R.drawable.ms_delete_forever else R.drawable.ms_cloud_off,
                contentDescription = null,
                tint = if (delete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = { Text(stringResource(if (delete) R.string.backup_delete_title else R.string.backup_turn_off_title)) },
        text = {
            Text(
                if (delete) {
                    stringResource(R.string.backup_delete_message, email)
                } else {
                    stringResource(R.string.backup_turn_off_message)
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    if (delete) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
            ) {
                Text(stringResource(if (delete) R.string.backup_delete_confirm else R.string.backup_turn_off_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconContentColor = Color.Unspecified,
    )
}

private val BackupFrequency.labelRes: Int
    get() =
        when (this) {
            BackupFrequency.DAILY -> R.string.backup_daily
            BackupFrequency.WEEKLY -> R.string.backup_weekly
            BackupFrequency.MONTHLY -> R.string.backup_monthly
            BackupFrequency.MANUAL -> R.string.backup_manual
        }
