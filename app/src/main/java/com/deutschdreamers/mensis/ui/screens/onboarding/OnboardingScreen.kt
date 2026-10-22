package com.deutschdreamers.mensis.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.domain.model.Goal
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisDatePickerDialog
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.NotificationPreview
import com.deutschdreamers.mensis.ui.components.PasswordField
import com.deutschdreamers.mensis.ui.components.RadioRow
import com.deutschdreamers.mensis.ui.components.StepProgressBar
import com.deutschdreamers.mensis.ui.components.StepperCard
import com.deutschdreamers.mensis.ui.components.SwitchRow
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.screens.backup.ConnectProblem
import com.deutschdreamers.mensis.ui.screens.backup.rememberGoogleConnect
import com.deutschdreamers.mensis.ui.theme.SerifStyles
import java.time.LocalDate

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onRestore: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    LaunchedEffect(draft.finished) { if (draft.finished) onFinished() }
    val connect = rememberGoogleConnect(viewModel.accounts, viewModel.lock, viewModel::onBackupConnect)
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.finish(remindersAllowed = granted)
        }
    OnboardingContent(
        draft = draft,
        today = viewModel.today,
        onUpdate = viewModel::update,
        onBack = viewModel::back,
        onSkip = viewModel::skip,
        onNext = {
            when {
                draft.isLast ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.finish(remindersAllowed = true)
                    }
                draft.needsGoogleConnect -> connect.connect(null)
                else -> viewModel.next()
            }
        },
        onRestore = onRestore,
    )
}

/** Design §1, nine steps. Stateless so tests and previews can show any step. */
@Composable
fun OnboardingContent(
    draft: OnboardingDraft,
    today: LocalDate,
    onUpdate: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onRestore: () -> Unit,
) {
    if (!draft.isFirst) BackHandler(onBack = onBack)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!draft.isFirst) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
                    MsIcon(resId = R.drawable.ms_arrow_back, contentDescription = stringResource(R.string.action_back))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (draft.skippable) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(if (draft.isLast) R.string.onboarding_not_now else R.string.onboarding_skip))
                }
            }
        }
        StepProgressBar(filled = draft.step + 1, total = OnboardingDraft.STEP_COUNT)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (draft.step) {
                0 -> WelcomeStep()
                1 -> HowItWorksStep()
                2 -> LastPeriodStep(draft, today, onUpdate)
                3 -> LengthsStep(draft, onUpdate)
                4 -> GoalStep(draft, onUpdate)
                5 -> LockStep(draft, onUpdate)
                6 -> BackupStep(draft, onUpdate)
                7 -> PasswordStep(draft, onUpdate)
                else -> RemindersStep()
            }
        }
        Column(modifier = Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNext, enabled = draft.canContinue, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(
                    stringResource(
                        when {
                            draft.isFirst -> R.string.onboarding_get_started
                            draft.isLast -> R.string.onboarding_allow_notifications
                            else -> R.string.action_continue
                        },
                    ),
                )
            }
            if (draft.isFirst) {
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(R.string.onboarding_restore))
                }
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
private fun FeatureRow(
    icon: Int,
    title: String,
    body: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        MsIcon(resId = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Supporting(body)
        }
    }
}

@Composable
private fun WelcomeStep() {
    Box(
        modifier =
            Modifier
                .size(
                    64.dp,
                ).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        MsIcon(
            resId = R.drawable.ms_timeline_fill,
            contentDescription = null,
            size = 32.dp,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.app_name), style = SerifStyles.wordmark)
        Text(text = stringResource(R.string.onboarding_welcome_subtitle), style = MaterialTheme.typography.bodyLarge)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FeatureRow(
            R.drawable.ms_lock,
            stringResource(R.string.onboarding_feature_private),
            stringResource(R.string.onboarding_feature_private_body),
        )
        FeatureRow(
            R.drawable.ms_query_stats,
            stringResource(R.string.onboarding_feature_ranges),
            stringResource(R.string.onboarding_feature_ranges_body),
        )
        FeatureRow(
            R.drawable.ms_code,
            stringResource(R.string.onboarding_feature_open),
            stringResource(R.string.onboarding_feature_open_body),
        )
    }
}

@Composable
private fun NumberedCard(
    badge: @Composable () -> Unit,
    title: String,
    body: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    MaterialTheme.shapes.large,
                ).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { badge() }
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Supporting(body)
        }
    }
}

@Composable
private fun HowItWorksStep() {
    Headline(stringResource(R.string.onboarding_how_title))
    Supporting(stringResource(R.string.onboarding_how_body))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberedCard(
            badge = {
                Text(
                    "1",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            title = stringResource(R.string.onboarding_how_one),
            body = stringResource(R.string.onboarding_how_one_body),
        )
        NumberedCard(
            badge = {
                Text(
                    "3",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            title = stringResource(R.string.onboarding_how_three),
            body = stringResource(R.string.onboarding_how_three_body),
        )
        NumberedCard(
            badge = {
                MsIcon(
                    resId = R.drawable.ms_thermostat,
                    contentDescription = null,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            title = stringResource(R.string.onboarding_how_temperature),
            body = stringResource(R.string.onboarding_how_temperature_body),
        )
    }
    InfoBanner(text = stringResource(R.string.onboarding_not_contraception))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LastPeriodStep(
    draft: OnboardingDraft,
    today: LocalDate,
    onUpdate: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Headline(stringResource(R.string.onboarding_last_period_title))
    OutlinedTextField(
        value =
            draft.lastPeriodStart?.let(Formats::dayMonthYear)
                ?: if (draft.lastPeriodUnknown) stringResource(R.string.onboarding_dont_remember) else "",
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.onboarding_last_period_label)) },
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                MsIcon(
                    resId = R.drawable.ms_calendar_month,
                    contentDescription = stringResource(R.string.action_pick_date),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { onUpdate { it.copy(lastPeriodStart = today, lastPeriodUnknown = false) } },
            label = { Text(stringResource(R.string.onboarding_today)) },
        )
        AssistChip(
            onClick = { onUpdate { it.copy(lastPeriodStart = today.minusDays(1), lastPeriodUnknown = false) } },
            label = { Text(stringResource(R.string.onboarding_yesterday)) },
        )
        AssistChip(
            onClick = { onUpdate { it.copy(lastPeriodStart = null, lastPeriodUnknown = true) } },
            label = { Text(stringResource(R.string.onboarding_dont_remember)) },
        )
    }
    Supporting(stringResource(R.string.onboarding_last_period_helper))
    if (showPicker) {
        MensisDatePickerDialog(
            initial = draft.lastPeriodStart ?: today,
            onDismiss = { showPicker = false },
            onConfirm = { date ->
                showPicker = false
                if (!date.isAfter(today)) onUpdate { it.copy(lastPeriodStart = date, lastPeriodUnknown = false) }
            },
        )
    }
}

@Composable
private fun LengthsStep(
    draft: OnboardingDraft,
    onUpdate: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
) {
    Headline(stringResource(R.string.onboarding_lengths_title))
    Supporting(stringResource(R.string.onboarding_lengths_helper))
    StepperCard(
        title = stringResource(R.string.onboarding_cycle_length),
        supporting = stringResource(R.string.onboarding_cycle_length_body),
        value = draft.cycleLength,
        onValueChange = { v -> onUpdate { it.copy(cycleLength = v) } },
        decreaseDescription = stringResource(R.string.onboarding_decrease),
        increaseDescription = stringResource(R.string.onboarding_increase),
        min = MIN_CYCLE_LENGTH,
        max = MAX_CYCLE_LENGTH,
    )
    StepperCard(
        title = stringResource(R.string.onboarding_period_length),
        supporting = stringResource(R.string.onboarding_period_length_body),
        value = draft.periodLength,
        onValueChange = { v -> onUpdate { it.copy(periodLength = v) } },
        decreaseDescription = stringResource(R.string.onboarding_decrease),
        increaseDescription = stringResource(R.string.onboarding_increase),
        min = 1,
        max = MAX_PERIOD_LENGTH,
    )
}

@Composable
private fun GoalStep(
    draft: OnboardingDraft,
    onUpdate: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
) {
    Headline(stringResource(R.string.onboarding_goal_title))
    Supporting(stringResource(R.string.onboarding_goal_body))
    Column {
        for (goal in Goal.entries) {
            RadioRow(
                title = stringResource(goal.titleRes),
                supporting = stringResource(goal.bodyRes),
                selected = goal == draft.goal,
                onSelect = { onUpdate { it.copy(goal = goal) } },
                horizontalPadding = 0.dp,
            )
        }
    }
}

@Composable
private fun LockStep(
    draft: OnboardingDraft,
    onUpdate: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
) {
    Headline(stringResource(R.string.onboarding_lock_title))
    Supporting(stringResource(R.string.onboarding_lock_body))
    SwitchRow(
        icon = R.drawable.ms_fingerprint,
        title = stringResource(R.string.onboarding_require_unlock),
        supporting = stringResource(R.string.onboarding_require_unlock_body),
        checked = draft.lockEnabled,
        onCheckedChange = { v -> onUpdate { it.copy(lockEnabled = v) } },
        horizontalPadding = 0.dp,
    )
    SwitchRow(
        icon = R.drawable.ms_visibility_off,
        title = stringResource(R.string.settings_hide_recents),
        supporting = stringResource(R.string.onboarding_hide_recents_body),
        checked = draft.hideInRecents,
        onCheckedChange = { v -> onUpdate { it.copy(hideInRecents = v) } },
        horizontalPadding = 0.dp,
    )
}

@Composable
private fun BackupStep(
    draft: OnboardingDraft,
    onUpdate: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
) {
    Headline(stringResource(R.string.onboarding_backup_title))
    Supporting(stringResource(R.string.onboarding_backup_body))
    SwitchRow(
        icon = R.drawable.ms_cloud_upload,
        title = stringResource(R.string.onboarding_backup_switch),
        supporting =
            draft.backupEmail?.let { stringResource(R.string.onboarding_backup_connected, it) }
                ?: stringResource(R.string.onboarding_backup_switch_body),
        checked = draft.backupEnabled && draft.backupOffered,
        onCheckedChange = { v -> onUpdate { it.copy(backupEnabled = v, backupProblem = null) } },
        enabled = draft.backupOffered,
        horizontalPadding = 0.dp,
    )
    if (!draft.backupOffered) {
        InfoBanner(
            text =
                stringResource(
                    if (draft.backupAvailability == BackupAvailability.NO_PLAY_SERVICES) {
                        R.string.backup_unavailable_play
                    } else {
                        R.string.backup_unavailable_config
                    },
                ),
        )
    }
    draft.backupProblem?.let { problem ->
        InfoBanner(
            text =
                stringResource(
                    if (problem == ConnectProblem.NO_ACCOUNT) {
                        R.string.backup_connect_no_account
                    } else {
                        R.string.backup_connect_failed
                    },
                ),
            icon = R.drawable.ms_error,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    InfoBanner(text = stringResource(R.string.onboarding_backup_warning), icon = R.drawable.ms_phonelink_erase)
}

@Composable
private fun PasswordStep(
    draft: OnboardingDraft,
    onUpdate: ((OnboardingDraft) -> OnboardingDraft) -> Unit,
) {
    Headline(stringResource(R.string.onboarding_password_title))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PasswordField(
            value = draft.backupPassword,
            onValueChange = { v -> onUpdate { it.copy(backupPassword = v) } },
            label = stringResource(R.string.onboarding_password_label),
            supporting = stringResource(R.string.onboarding_password_hint),
        )
        PasswordStrengthBar(draft.backupPassword)
        PasswordField(
            value = draft.backupRepeat,
            onValueChange = { v -> onUpdate { it.copy(backupRepeat = v) } },
            label = stringResource(R.string.export_password_repeat),
            isError = draft.backupRepeat.isNotEmpty() && !draft.passwordsMatch,
            supporting =
                stringResource(R.string.export_password_mismatch).takeIf {
                    draft.backupRepeat.isNotEmpty() && !draft.passwordsMatch
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
}

@Composable
private fun RemindersStep() {
    Headline(stringResource(R.string.onboarding_reminders_title))
    Supporting(stringResource(R.string.onboarding_reminders_body))
    NotificationPreview(
        appLine = stringResource(R.string.onboarding_notification_app),
        title = stringResource(R.string.onboarding_notification_title),
        body = stringResource(R.string.onboarding_notification_body),
    )
    Supporting(stringResource(R.string.onboarding_reminders_footer))
}

private val Goal.titleRes: Int
    get() =
        when (this) {
            Goal.TRACK_CYCLE -> R.string.goal_track
            Goal.TRYING_TO_CONCEIVE -> R.string.goal_conceive
            Goal.AVOIDING_PREGNANCY -> R.string.goal_avoid
            Goal.UNDERSTAND_SYMPTOMS -> R.string.goal_symptoms
        }

private val Goal.bodyRes: Int
    get() =
        when (this) {
            Goal.TRACK_CYCLE -> R.string.goal_track_body
            Goal.TRYING_TO_CONCEIVE -> R.string.goal_conceive_body
            Goal.AVOIDING_PREGNANCY -> R.string.goal_avoid_body
            Goal.UNDERSTAND_SYMPTOMS -> R.string.goal_symptoms_body
        }

private const val MIN_CYCLE_LENGTH = 15
private const val MAX_CYCLE_LENGTH = 60
private const val MAX_PERIOD_LENGTH = 14
