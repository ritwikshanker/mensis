package com.deutschdreamers.mensis.ui.screens.restore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.backup.RestoreError
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.ui.components.ContentCard
import com.deutschdreamers.mensis.ui.components.DoneHeader
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.PasswordField
import com.deutschdreamers.mensis.ui.screens.backup.BackupTexts
import com.deutschdreamers.mensis.ui.screens.backup.ConnectProblem
import com.deutschdreamers.mensis.ui.screens.backup.rememberGoogleConnect

@Composable
fun RestoreScreen(
    onClose: () -> Unit,
    onRestored: () -> Unit,
    fromOnboarding: Boolean,
    viewModel: RestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connect = rememberGoogleConnect(viewModel.accounts, viewModel.lock, viewModel::onConnectResult)
    RestoreContent(
        state = state,
        actions =
            RestoreActions(
                onBack = { if (!viewModel.back()) onClose() },
                onConnect = { connect.connect(null) },
                onRetry = viewModel::retry,
                onReplaceConfirmed = viewModel::setReplaceConfirmed,
                onChoose = viewModel::chooseBackup,
                onPassword = viewModel::setPassword,
                onDecrypt = viewModel::decryptAndRestore,
                onOpen = onRestored,
                onStartFresh = onClose.takeIf { fromOnboarding },
            ),
    )
}

/** Design §12. Stateless so tests and previews can show every step. */
@Composable
fun RestoreContent(
    state: RestoreUiState,
    actions: RestoreActions,
) {
    val restoring = state.step == RestoreStep.RESTORING || state.decrypting
    val done = state.step == RestoreStep.DONE
    // Leaving halfway through replacing the data is not offered; once done, back opens the app.
    BackHandler(enabled = restoring) {}
    BackHandler(enabled = done, onBack = actions.onOpen)
    BackHandler(enabled = !restoring && !done, onBack = actions.onBack)
    Scaffold(
        topBar = {
            MensisTopBar(
                title = stringResource(R.string.onboarding_restore),
                onBack = actions.onBack.takeIf { !restoring && !done },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (state.step) {
                RestoreStep.SIGN_IN -> SignInStep(state, actions)
                RestoreStep.FOUND -> FoundStep(state, actions)
                RestoreStep.NONE_FOUND -> NoneFoundStep(state, actions)
                RestoreStep.PASSWORD -> PasswordStep(state, actions)
                RestoreStep.RESTORING -> RestoringStep(state)
                RestoreStep.DONE -> DoneStep(state, actions)
            }
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun Supporting(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorBanner(
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    InfoBanner(
        text = text,
        icon = R.drawable.ms_error,
        actionLabel = action,
        onAction = onAction,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        iconTint = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun errorText(error: RestoreError): String =
    stringResource(
        when (error) {
            RestoreError.WRONG_PASSWORD -> R.string.restore_wrong_password
            RestoreError.DAMAGED -> R.string.restore_error_damaged
            RestoreError.NEWER_VERSION -> R.string.restore_error_newer
            RestoreError.NETWORK -> R.string.restore_error_network
            RestoreError.RECONNECT -> R.string.restore_error_reconnect
        },
    )

@Composable
private fun SignInStep(
    state: RestoreUiState,
    actions: RestoreActions,
) {
    val available = state.availability == BackupAvailability.AVAILABLE
    Headline(stringResource(R.string.restore_sign_in_title))
    Supporting(stringResource(R.string.restore_sign_in_body))
    if (!available) {
        InfoBanner(
            text =
                stringResource(
                    if (state.availability == BackupAvailability.NO_PLAY_SERVICES) {
                        R.string.backup_unavailable_play
                    } else {
                        R.string.backup_unavailable_config
                    },
                ),
        )
    }
    state.connectProblem?.let { problem ->
        ErrorBanner(
            stringResource(
                if (problem ==
                    ConnectProblem.NO_ACCOUNT
                ) {
                    R.string.backup_connect_no_account
                } else {
                    R.string.backup_connect_failed
                },
            ),
        )
    }
    state.error?.let { error ->
        ErrorBanner(
            text = errorText(error),
            action = stringResource(R.string.restore_try_again).takeIf { error == RestoreError.NETWORK },
            onAction = actions.onRetry.takeIf { error == RestoreError.NETWORK },
        )
    }
    OutlinedButton(
        onClick = actions.onConnect,
        enabled = available && !state.checking,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (state.checking) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            MsIcon(resId = R.drawable.ms_account_circle, contentDescription = null, size = 20.dp)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.restore_continue_google))
        }
    }
}

@Composable
private fun FoundStep(
    state: RestoreUiState,
    actions: RestoreActions,
) {
    val file = state.file ?: return
    Headline(stringResource(R.string.restore_found_title))
    ContentCard {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MsIcon(resId = R.drawable.ms_cloud_done, contentDescription = null)
            Text(
                text = BackupTexts.backupFile(file.name, file.createdAt, file.sizeBytes, file.device, state.zone),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    val local = state.local
    when {
        local == null -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        !state.localHasData -> Supporting(stringResource(R.string.restore_empty_phone))
        else -> {
            InfoBanner(text = BackupTexts.replaces(local), icon = R.drawable.ms_error)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.replaceConfirmed,
                            role = Role.Checkbox,
                            onValueChange = actions.onReplaceConfirmed,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.replaceConfirmed, onCheckedChange = null)
                Spacer(modifier = Modifier.size(12.dp))
                Text(stringResource(R.string.restore_replace_confirm), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    state.error?.let { ErrorBanner(errorText(it)) }
    Button(
        onClick = actions.onChoose,
        enabled = state.canRestore,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(stringResource(R.string.restore_this))
    }
}

@Composable
private fun NoneFoundStep(
    state: RestoreUiState,
    actions: RestoreActions,
) {
    Headline(stringResource(R.string.restore_none_title))
    Supporting(stringResource(R.string.restore_none_body, state.email.orEmpty()))
    OutlinedButton(onClick = actions.onConnect, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(stringResource(R.string.restore_other_account))
    }
}

@Composable
private fun PasswordStep(
    state: RestoreUiState,
    actions: RestoreActions,
) {
    var lostShown by rememberSaveable { mutableStateOf(false) }
    Headline(stringResource(R.string.restore_password_title))
    PasswordField(
        value = state.password,
        onValueChange = actions.onPassword,
        label = stringResource(R.string.onboarding_password_label),
        isError = state.wrongPassword,
        supporting = stringResource(R.string.restore_wrong_password).takeIf { state.wrongPassword },
    )
    state.error?.let { ErrorBanner(errorText(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = actions.onDecrypt,
            enabled = state.canDecrypt,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.decrypting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.restore_decrypting))
            } else {
                Text(stringResource(R.string.restore_decrypt))
            }
        }
        TextButton(onClick = { lostShown = true }, enabled = !state.decrypting) {
            Text(stringResource(R.string.restore_lost_password))
        }
    }
    if (lostShown) {
        AlertDialog(
            onDismissRequest = { lostShown = false },
            icon = { MsIcon(resId = R.drawable.ms_key_off, contentDescription = null) },
            title = { Text(stringResource(R.string.restore_lost_title)) },
            text = { Text(stringResource(R.string.restore_lost_body)) },
            confirmButton = {
                val startFresh = actions.onStartFresh
                if (startFresh != null) {
                    Button(onClick = {
                        lostShown = false
                        startFresh()
                    }) { Text(stringResource(R.string.restore_start_fresh)) }
                } else {
                    TextButton(onClick = { lostShown = false }) { Text(stringResource(R.string.action_ok)) }
                }
            },
            dismissButton =
                actions.onStartFresh?.let {
                    { TextButton(onClick = { lostShown = false }) { Text(stringResource(R.string.action_cancel)) } }
                },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun RestoringStep(state: RestoreUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Text(
            text = stringResource(R.string.restore_restoring),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
        )
        Text(
            text = BackupTexts.restoring(state.file?.sizeBytes ?: 0, state.cycles),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DoneStep(
    state: RestoreUiState,
    actions: RestoreActions,
) {
    val result = state.result ?: return
    DoneHeader(
        title = stringResource(R.string.restore_done),
        body = BackupTexts.restored(result),
        icon = R.drawable.ms_check,
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = actions.onOpen, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(stringResource(R.string.restore_open))
    }
}
