package com.medishelf.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.medishelf.core.domain.backup.BackupPasswordProblem
import com.medishelf.core.domain.backup.BackupPasswordRules
import com.medishelf.core.domain.export.CsvExporter
import com.medishelf.core.domain.export.CsvLabels
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.QuantityUnit
import com.medishelf.core.ui.label

/** Backup password entry. Kept in memory only (never rememberSaveable, so it isn't written to saved state). */
@Composable
internal fun PasswordDialog(title: String, confirm: Boolean, onDismiss: () -> Unit, onPassword: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val problem = BackupPasswordRules.validate(password, if (confirm) confirmation else null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(if (confirm) R.string.feature_settings_backup_password_body else R.string.feature_settings_restore_password_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(stringResource(R.string.feature_settings_password), password, { password = it }, "password")
                if (confirm) {
                    PasswordField(stringResource(R.string.feature_settings_password_confirm), confirmation, { confirmation = it }, "password_confirm")
                }
                if (attempted && problem != null && (confirm || problem == BackupPasswordProblem.TOO_SHORT)) {
                    Text(
                        stringResource(
                            if (problem == BackupPasswordProblem.TOO_SHORT) R.string.feature_settings_password_too_short else R.string.feature_settings_password_mismatch,
                            BackupPasswordRules.MIN_LENGTH,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attempted = true
                    // Restores accept any length: older backups may predate the rule.
                    if (problem == null || (!confirm && password.isNotEmpty())) onPassword(password.toCharArray())
                },
                modifier = Modifier.testTag("password_ok"),
            ) { Text(stringResource(R.string.feature_settings_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.medishelf.core.ui.R.string.core_ui_cancel)) } },
    )
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** Who the CSV is for, and whether to include finished/disposed history. */
@Composable
internal fun CsvDialog(members: List<FamilyMember>, onDismiss: () -> Unit, onExport: (CsvRequest) -> Unit) {
    var memberId by rememberSaveable { mutableStateOf<Long?>(null) }
    var includePast by rememberSaveable { mutableStateOf(false) }
    val labels = csvLabels()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_settings_csv)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.feature_settings_csv_for), style = MaterialTheme.typography.titleSmall)
                (listOf<FamilyMember?>(null) + members).forEach { member ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = memberId == member?.id, role = Role.RadioButton, onClick = { memberId = member?.id }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = memberId == member?.id, onClick = null)
                        Text(member?.name ?: stringResource(R.string.feature_settings_csv_everyone))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = includePast, role = Role.Checkbox, onValueChange = { includePast = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includePast, onCheckedChange = null)
                    Text(stringResource(R.string.feature_settings_csv_include_past))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(CsvRequest(memberId, includePast, labels)) }) { Text(stringResource(R.string.feature_settings_export)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.medishelf.core.ui.R.string.core_ui_cancel)) } },
    )
}

/** Column titles and enum labels in the user's language. */
@Composable
private fun csvLabels(): CsvLabels {
    val headers = listOf(
        R.string.feature_settings_csv_col_name, R.string.feature_settings_csv_col_generic, R.string.feature_settings_csv_col_strength,
        R.string.feature_settings_csv_col_form, R.string.feature_settings_csv_col_quantity, R.string.feature_settings_csv_col_unit,
        R.string.feature_settings_csv_col_expiry, R.string.feature_settings_csv_col_status, R.string.feature_settings_csv_col_people,
        R.string.feature_settings_csv_col_dosage, R.string.feature_settings_csv_col_doctor, R.string.feature_settings_csv_col_rx,
        R.string.feature_settings_csv_col_location, R.string.feature_settings_csv_col_batch, R.string.feature_settings_csv_col_notes,
    ).map { stringResource(it) }
    check(headers.size == CsvExporter.COLUMN_COUNT)
    return CsvLabels(
        headers = headers,
        forms = MedicineForm.entries.associateWith { it.label() },
        units = QuantityUnit.entries.associateWith { it.label() },
        statuses = MedicineStatus.entries.associateWith { it.label() },
        yes = stringResource(R.string.feature_settings_yes),
        no = stringResource(R.string.feature_settings_no),
    )
}
