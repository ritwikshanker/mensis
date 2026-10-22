package com.deutschdreamers.mensis.ui.screens.export

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.data.export.ExportFormat
import com.deutschdreamers.mensis.ui.components.DoneHeader
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.PasswordField
import com.deutschdreamers.mensis.ui.components.RadioRow
import com.deutschdreamers.mensis.ui.components.SwitchRow
import com.deutschdreamers.mensis.ui.components.WorkingIndicator
import com.deutschdreamers.mensis.ui.screens.onboarding.PasswordStrengthBar

@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(viewModel.mimeType)) { uri ->
            viewModel.onLocationChosen(uri)
        }
    val shareLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { viewModel.onShareClosed() }
    LaunchedEffect(viewModel) {
        viewModel.shareRequests.collect { uri ->
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType(viewModel.mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shareLauncher.launch(Intent.createChooser(send, null))
        }
    }
    ExportContent(
        state = state,
        onBack = onBack,
        onFormat = viewModel::setFormat,
        onEncrypt = viewModel::setEncrypt,
        onPassword = viewModel::setPassword,
        onRepeat = viewModel::setRepeat,
        onChooseLocation = {
            viewModel.onPickerOpening()
            saveLauncher.launch(viewModel.suggestedFileName)
        },
        onShare = viewModel::share,
    )
}

/** Design §13: options, the system "Save to" picker, then "Saved". Stateless for tests and previews. */
@Composable
fun ExportContent(
    state: ExportUiState,
    onBack: () -> Unit,
    onFormat: (ExportFormat) -> Unit,
    onEncrypt: (Boolean) -> Unit,
    onPassword: (String) -> Unit,
    onRepeat: (String) -> Unit,
    onChooseLocation: () -> Unit,
    onShare: () -> Unit,
) {
    // Leaving while the file is written would cancel the write halfway.
    val saving = state.step == ExportStep.SAVING
    BackHandler(enabled = saving) {}
    Scaffold(
        topBar = {
            MensisTopBar(
                title = stringResource(R.string.settings_export),
                onBack = if (saving) null else onBack,
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (state.step) {
                ExportStep.OPTIONS -> OptionsStep(state, onFormat, onEncrypt, onPassword, onRepeat, onChooseLocation)
                ExportStep.SAVING ->
                    WorkingIndicator(
                        text =
                            stringResource(
                                if (state.encrypt) R.string.export_encrypting else R.string.export_saving,
                            ),
                        modifier = Modifier.padding(top = 120.dp),
                    )
                ExportStep.SAVED -> SavedStep(state, onShare, onDone = onBack)
            }
        }
    }
}

@Composable
private fun OptionsStep(
    state: ExportUiState,
    onFormat: (ExportFormat) -> Unit,
    onEncrypt: (Boolean) -> Unit,
    onPassword: (String) -> Unit,
    onRepeat: (String) -> Unit,
    onChooseLocation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.export_format),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
        Column {
            RadioRow(
                title = stringResource(R.string.export_json),
                supporting = stringResource(R.string.export_json_body),
                selected = state.format == ExportFormat.JSON,
                onSelect = { onFormat(ExportFormat.JSON) },
                horizontalPadding = 12.dp,
            )
            RadioRow(
                title = stringResource(R.string.export_csv),
                supporting = stringResource(R.string.export_csv_body),
                selected = state.format == ExportFormat.CSV,
                onSelect = { onFormat(ExportFormat.CSV) },
                horizontalPadding = 12.dp,
            )
        }
    }
    SwitchRow(
        icon = R.drawable.ms_lock,
        title = stringResource(R.string.export_encrypt),
        supporting = stringResource(R.string.export_encrypt_body),
        checked = state.encrypt,
        onCheckedChange = onEncrypt,
        horizontalPadding = 12.dp,
    )
    if (state.encrypt) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PasswordField(
                value = state.password,
                onValueChange = onPassword,
                label = stringResource(R.string.export_password_label),
            )
            PasswordStrengthBar(state.password)
            PasswordField(
                value = state.repeat,
                onValueChange = onRepeat,
                label = stringResource(R.string.export_password_repeat),
                isError = state.repeat.isNotEmpty() && !state.passwordsMatch,
                supporting =
                    stringResource(R.string.export_password_mismatch).takeIf {
                        state.repeat.isNotEmpty() &&
                            !state.passwordsMatch
                    },
            )
        }
        InfoBanner(
            text = stringResource(R.string.export_password_warning),
            icon = R.drawable.ms_key_off,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        InfoBanner(text = stringResource(R.string.export_unencrypted_warning), icon = R.drawable.ms_error)
    }
    val summary = state.summary
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.export_included),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary != null) {
            Text(text = ExportTexts.included(summary, state.format), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = ExportTexts.rangeAndSize(summary, state.estimatedBytes ?: 0),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (state.failed) {
        InfoBanner(
            text = stringResource(R.string.export_failed),
            icon = R.drawable.ms_error,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Button(
        onClick = onChooseLocation,
        enabled = state.canChooseLocation,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(stringResource(R.string.export_choose))
    }
}

@Composable
private fun SavedStep(
    state: ExportUiState,
    onShare: () -> Unit,
    onDone: () -> Unit,
) {
    DoneHeader(
        title = stringResource(R.string.export_saved),
        body = ExportTexts.saved(state.savedName.orEmpty(), state.savedBytes, state.savedEncrypted),
    )
    Spacer(modifier = Modifier.height(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            MsIcon(resId = R.drawable.ms_share, contentDescription = null, size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.export_share))
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.action_done))
        }
    }
}
