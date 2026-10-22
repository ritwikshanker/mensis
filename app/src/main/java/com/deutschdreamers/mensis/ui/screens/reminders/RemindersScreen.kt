package com.deutschdreamers.mensis.ui.screens.reminders

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.domain.reminders.ReminderOption
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import com.deutschdreamers.mensis.notifications.ReminderTexts
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisTimePickerDialog
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.NotificationPreview
import com.deutschdreamers.mensis.ui.components.SelectChip
import com.deutschdreamers.mensis.ui.components.SettingsRow
import com.deutschdreamers.mensis.ui.components.SingleChoiceChips
import com.deutschdreamers.mensis.ui.components.SwitchRow
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT

@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var askedForPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            askedForPermission = true
            viewModel.refreshSystemState()
        }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSystemState()
        onPauseOrDispose { }
    }
    val current = state
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // Android's dialog while it can still be shown, Android's settings after that.
    val canAsk =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedForPermission && !context.hasPermission()

    fun openSettings(intent: Intent) {
        viewModel.lock.allowExternalTrip()
        try {
            context.startActivity(intent)
        } catch (expected: ActivityNotFoundException) {
            viewModel.lock.endExternalTrip()
        }
    }

    fun allowNotifications() {
        if (canAsk) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openSettings(
                Intent(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    RemindersContent(
        state = current,
        actions =
            RemindersActions(
                onBack = onBack,
                onEnabled = { type, enabled ->
                    viewModel.setEnabled(type, enabled)
                    if (enabled && !current.notificationsAllowed && canAsk) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOption = viewModel::setOption,
                onTime = viewModel::setTime,
                onDiscreet = viewModel::setDiscreet,
                onGenericText = viewModel::setGenericText,
                onAllowNotifications = ::allowNotifications,
                onAllowExactAlarms = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        openSettings(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                "package:${context.packageName}".toUri(),
                            ),
                        )
                    }
                },
            ),
    )
}

private fun Context.hasPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Design §10: expandable rows with a switch, lead chips and a time field; discreet wording, a live
 * preview and the footer. Stateless so tests and previews can show any state.
 */
@Composable
fun RemindersContent(
    state: RemindersUiState,
    actions: RemindersActions,
) {
    var expanded by rememberSaveable { mutableStateOf<ReminderType?>(null) }
    var timeFor by rememberSaveable { mutableStateOf<ReminderType?>(null) }
    var editingText by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        MensisTopBar(title = stringResource(R.string.settings_reminders), onBack = actions.onBack)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.notificationsAllowed) {
                InfoBanner(
                    text = stringResource(R.string.reminders_notifications_off),
                    icon = R.drawable.ms_notifications,
                    actionLabel = stringResource(R.string.reminders_notifications_allow),
                    onAction = actions.onAllowNotifications,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in state.rows) {
                    ReminderRowItem(
                        row = row,
                        open = expanded == row.type,
                        onToggleOpen = { expanded = if (expanded == row.type) null else row.type },
                        onEnabled = { actions.onEnabled(row.type, it) },
                        onOption = { actions.onOption(row.type, it) },
                        onPickTime = { timeFor = row.type },
                    )
                }
            }
            if (state.anyEnabled && state.notificationsAllowed && !state.exactAlarms) {
                InfoBanner(
                    text = stringResource(R.string.reminders_exact_off),
                    icon = R.drawable.ms_schedule,
                    actionLabel = stringResource(R.string.reminders_exact_allow),
                    onAction = actions.onAllowExactAlarms,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column {
                SwitchRow(
                    icon = R.drawable.ms_visibility_off,
                    title = stringResource(R.string.reminders_discreet),
                    supporting = stringResource(R.string.reminders_discreet_supporting, state.generic.title),
                    checked = state.discreet,
                    onCheckedChange = actions.onDiscreet,
                    horizontalPadding = 0.dp,
                )
                if (state.discreet) {
                    SettingsRow(
                        icon = R.drawable.ms_edit_note,
                        title = stringResource(R.string.reminders_text),
                        supporting = "${state.generic.title} $MIDDLE_DOT ${state.generic.body}",
                        showChevron = false,
                        horizontalPadding = 0.dp,
                        onClick = { editingText = true },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.reminders_preview),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NotificationPreview(
                    appLine = stringResource(R.string.reminders_preview_app, Formats.time(state.previewTime)),
                    title = state.preview.title,
                    body = state.preview.body,
                )
            }
            Text(
                text = stringResource(R.string.reminders_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val pickingFor = timeFor?.let { type -> state.rows.firstOrNull { it.type == type } }
    if (pickingFor != null) {
        MensisTimePickerDialog(
            initial = pickingFor.time,
            title = stringResource(R.string.reminders_time_dialog),
            onDismiss = { timeFor = null },
            onConfirm = {
                actions.onTime(pickingFor.type, it)
                timeFor = null
            },
        )
    }
    if (editingText) {
        GenericTextDialog(
            state = state,
            onDismiss = { editingText = false },
            onSave = { title, body ->
                actions.onGenericText(title, body)
                editingText = false
            },
        )
    }
}

@Composable
private fun ReminderRowItem(
    row: ReminderRow,
    open: Boolean,
    onToggleOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onOption: (ReminderOption) -> Unit,
    onPickTime: () -> Unit,
) {
    val background = if (open) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
    Column(modifier = Modifier.fillMaxWidth().background(background, MaterialTheme.shapes.large)) {
        Row(
            modifier = Modifier.defaultMinSize(minHeight = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val expandLabel = stringResource(R.string.reminders_show_options, row.title)
            val openState = stringResource(if (open) R.string.reminders_expanded else R.string.reminders_collapsed)
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(onClick = onToggleOpen, onClickLabel = expandLabel, role = Role.Button)
                        .semantics { stateDescription = openState },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MsIcon(
                    resId = row.type.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = row.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = row.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = row.enabled, onCheckedChange = onEnabled)
        }
        if (open) {
            Column(
                modifier = Modifier.padding(start = 52.dp, end = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // A reminder always has an option; tapping the selected chip keeps it.
                SingleChoiceChips(
                    options = row.options,
                    selected = row.option,
                    label = ReminderTexts::option,
                    onSelect = { it?.let(onOption) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MsIcon(
                        resId = R.drawable.ms_schedule,
                        contentDescription = null,
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = stringResource(R.string.reminders_time), modifier = Modifier.weight(1f))
                    SelectChip(label = Formats.time(row.time), selected = false, onClick = onPickTime)
                }
            }
        }
    }
}

@Composable
private fun GenericTextDialog(
    state: RemindersUiState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(state.generic.title) }
    var body by rememberSaveable { mutableStateOf(state.generic.body) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders_text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reminders_text_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_TEXT_LENGTH) },
                    label = { Text(stringResource(R.string.reminders_text_title_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(MAX_TEXT_LENGTH) },
                    label = { Text(stringResource(R.string.reminders_text_body_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, body) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_TEXT_LENGTH = 60

@get:DrawableRes
private val ReminderType.icon: Int
    get() =
        when (this) {
            ReminderType.PERIOD -> R.drawable.ms_water_drop
            ReminderType.FERTILE -> R.drawable.ms_waves
            ReminderType.PILL -> R.drawable.ms_pill
            ReminderType.PATCH -> R.drawable.ms_healing
            ReminderType.RING -> R.drawable.ms_radio_button_unchecked
            ReminderType.INJECTION -> R.drawable.ms_vaccines
            ReminderType.REPLACEMENT -> R.drawable.ms_event_repeat
            ReminderType.TEMPERATURE -> R.drawable.ms_thermostat
        }
