package com.medishelf.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.model.ReminderSettings
import com.medishelf.core.model.ThemeMode
import com.medishelf.core.ui.DropdownField
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.SectionHeader
import com.medishelf.feature.onboarding.DisclaimerText
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class DataDialog { BACKUP_PASSWORD, RESTORE_PASSWORD, RESTORE_CONFIRM, CSV, DISCLAIMER }

@Composable
internal fun SettingsScreen(
    onOpenAppLock: () -> Unit,
    showDeveloperOptions: Boolean,
    versionName: String,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(messageText(resources, it))
            viewModel.onMessageShown()
        }
    }
    var dialog by rememberSaveable { mutableStateOf<DataDialog?>(null) }
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        viewModel.onBackupDestinationChosen(it?.toString())
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreUri = uri.toString()
            dialog = DataDialog.RESTORE_CONFIRM
        }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        viewModel.onCsvDestinationChosen(it?.toString())
    }
    val today = LocalDate.now()

    Scaffold(
        topBar = { MediShelfTopBar(stringResource(R.string.feature_settings_title)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            RemindersSection(state.settings.reminders, viewModel::onRemindersChange)

            SectionHeader(stringResource(R.string.feature_settings_section_appearance))
            ThemeChooser(state.settings.themeMode, viewModel::onThemeChange)

            SectionHeader(stringResource(R.string.feature_settings_section_security))
            SettingsRow(
                title = stringResource(R.string.feature_settings_app_lock),
                summary = stringResource(if (state.settings.appLock.enabled) R.string.feature_settings_on else R.string.feature_settings_off),
                onClick = onOpenAppLock,
            )

            SectionHeader(stringResource(R.string.feature_settings_section_data))
            SettingsRow(
                title = stringResource(R.string.feature_settings_backup),
                summary = stringResource(R.string.feature_settings_backup_summary),
                onClick = { dialog = DataDialog.BACKUP_PASSWORD },
                enabled = !state.busy,
                tag = "settings_backup",
            )
            SettingsRow(
                title = stringResource(R.string.feature_settings_restore),
                summary = stringResource(R.string.feature_settings_restore_summary),
                onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = !state.busy,
            )
            SettingsRow(
                title = stringResource(R.string.feature_settings_csv),
                summary = stringResource(R.string.feature_settings_csv_summary),
                onClick = { dialog = DataDialog.CSV },
                enabled = !state.busy,
            )

            if (showDeveloperOptions) {
                SectionHeader(stringResource(R.string.feature_settings_section_developer))
                SwitchRow(
                    title = stringResource(R.string.feature_settings_demo_data),
                    summary = stringResource(R.string.feature_settings_demo_data_summary),
                    checked = state.demoLoaded,
                    enabled = !state.busy,
                    onChange = viewModel::onDemoDataChange,
                )
                SettingsRow(
                    title = stringResource(R.string.feature_settings_run_check),
                    summary = stringResource(R.string.feature_settings_run_check_summary),
                    onClick = viewModel::onRunReminderCheck,
                )
            }

            SectionHeader(stringResource(R.string.feature_settings_section_about))
            SettingsRow(
                title = stringResource(R.string.feature_settings_disclaimer),
                summary = stringResource(R.string.feature_settings_disclaimer_summary),
                onClick = { dialog = DataDialog.DISCLAIMER },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_settings_privacy)) },
                supportingContent = { Text(stringResource(R.string.feature_settings_privacy_summary)) },
            )
            ListItem(headlineContent = { Text(stringResource(R.string.feature_settings_version, versionName)) })
        }
    }

    when (dialog) {
        DataDialog.BACKUP_PASSWORD -> PasswordDialog(
            title = stringResource(R.string.feature_settings_backup),
            confirm = true,
            onDismiss = { dialog = null; viewModel.onDialogCancelled() },
            onPassword = { password ->
                dialog = null
                viewModel.onPasswordEntered(password)
                createBackup.launch("MediShelf-backup-$today.zip")
            },
        )
        DataDialog.RESTORE_CONFIRM -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.feature_settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.feature_settings_restore_confirm_body)) },
            confirmButton = { TextButton(onClick = { dialog = DataDialog.RESTORE_PASSWORD }) { Text(stringResource(R.string.feature_settings_continue)) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(com.medishelf.core.ui.R.string.core_ui_cancel)) } },
        )
        DataDialog.RESTORE_PASSWORD -> PasswordDialog(
            title = stringResource(R.string.feature_settings_restore),
            confirm = false,
            onDismiss = { dialog = null; viewModel.onDialogCancelled() },
            onPassword = { password ->
                dialog = null
                viewModel.onPasswordEntered(password)
                viewModel.onRestoreSourceConfirmed(restoreUri)
            },
        )
        DataDialog.CSV -> CsvDialog(
            members = state.members,
            onDismiss = { dialog = null },
            onExport = { request ->
                dialog = null
                viewModel.onCsvRequested(request)
                createCsv.launch("MediShelf-medicines-$today.csv")
            },
        )
        DataDialog.DISCLAIMER -> AlertDialog(
            onDismissRequest = { dialog = null },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { DisclaimerText() } },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(com.medishelf.core.ui.R.string.core_ui_ok)) } },
        )
        null -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersSection(reminders: ReminderSettings, onChange: ((ReminderSettings) -> ReminderSettings) -> Unit) {
    SectionHeader(stringResource(R.string.feature_settings_section_reminders))
    SwitchRow(
        title = stringResource(R.string.feature_settings_reminders),
        summary = stringResource(R.string.feature_settings_reminders_summary),
        checked = reminders.enabled,
        onChange = { v -> onChange { it.copy(enabled = v) } },
    )
    NotificationPermissionRow(reminders.enabled)
    if (!reminders.enabled) return
    Text(
        stringResource(R.string.feature_settings_remind_before),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReminderSettings.DAY_CHOICES.forEach { days ->
            val selected = days in reminders.daysBefore
            FilterChip(
                selected = selected,
                onClick = { onChange { it.copy(daysBefore = if (selected) it.daysBefore - days else it.daysBefore + days) } },
                label = { Text(stringResource(R.string.feature_settings_days_chip, days)) },
            )
        }
    }
    SwitchRow(
        title = stringResource(R.string.feature_settings_notify_expired),
        checked = reminders.notifyExpired,
        onChange = { v -> onChange { it.copy(notifyExpired = v) } },
    )
    SwitchRow(
        title = stringResource(R.string.feature_settings_notify_low_stock),
        checked = reminders.notifyLowStock,
        onChange = { v -> onChange { it.copy(notifyLowStock = v) } },
    )
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    DropdownField(
        label = stringResource(R.string.feature_settings_reminder_time),
        options = (6..22).toList(),
        selected = reminders.hourOfDay,
        optionLabel = { LocalTime.of(it, 0).format(timeFormat) },
        onSelect = { hour -> onChange { it.copy(hourOfDay = hour) } },
        modifier = Modifier.padding(16.dp),
    )
}

/** Android 13+: reminders need the notification permission; explain and offer to grant it. */
@Composable
private fun NotificationPermissionRow(remindersOn: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !remindersOn) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose {}
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (!granted) {
        SettingsRow(
            title = stringResource(R.string.feature_settings_allow_notifications),
            summary = stringResource(R.string.feature_settings_allow_notifications_summary),
            onClick = { request.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
    }
}

@Composable
private fun ThemeChooser(current: ThemeMode, onChange: (ThemeMode) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        ThemeMode.entries.forEach { mode ->
            androidx.compose.foundation.layout.Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp).selectable(selected = mode == current, role = Role.RadioButton, onClick = { onChange(mode) })
                    .padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == current, onClick = null)
                Text(
                    stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.feature_settings_theme_system
                            ThemeMode.LIGHT -> R.string.feature_settings_theme_light
                            ThemeMode.DARK -> R.string.feature_settings_theme_dark
                        },
                    ),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
internal fun SettingsRow(title: String, summary: String?, onClick: () -> Unit, enabled: Boolean = true, tag: String? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, summary: String? = null, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

private fun messageText(resources: android.content.res.Resources, message: SettingsMessage): String = when (message) {
    is SettingsMessage.BackupSaved -> resources.getQuantityString(R.plurals.feature_settings_msg_backup_saved, message.summary.medicines, message.summary.medicines, message.summary.photos)
    is SettingsMessage.Restored -> resources.getQuantityString(R.plurals.feature_settings_msg_restored, message.summary.medicines, message.summary.medicines)
    is SettingsMessage.CsvSaved -> resources.getQuantityString(R.plurals.feature_settings_msg_csv_saved, message.rows, message.rows)
    SettingsMessage.WrongPassword -> resources.getString(R.string.feature_settings_msg_wrong_password)
    SettingsMessage.NotABackup -> resources.getString(R.string.feature_settings_msg_not_backup)
    SettingsMessage.TooNew -> resources.getString(R.string.feature_settings_msg_too_new)
    SettingsMessage.OperationFailed -> resources.getString(R.string.feature_settings_msg_failed)
    SettingsMessage.ReminderCheckQueued -> resources.getString(R.string.feature_settings_msg_check_queued)
}
