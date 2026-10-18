package com.medishelf.feature.medicines.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.domain.validation.MedicineValidationError
import com.medishelf.core.domain.validation.MedicineValidationError.EXPIRY_NOT_AFTER_PURCHASE
import com.medishelf.core.domain.validation.MedicineValidationError.EXPIRY_REQUIRED
import com.medishelf.core.domain.validation.MedicineValidationError.NAME_REQUIRED
import com.medishelf.core.domain.validation.MedicineValidationError.NAME_TOO_LONG
import com.medishelf.core.domain.validation.MedicineValidationError.OPENED_BEFORE_PURCHASE
import com.medishelf.core.domain.validation.MedicineValidationError.OPENED_IN_FUTURE
import com.medishelf.core.domain.validation.MedicineValidationError.PRESCRIPTION_IN_FUTURE
import com.medishelf.core.domain.validation.MedicineValidationError.PURCHASE_IN_FUTURE
import com.medishelf.core.domain.validation.MedicineValidationError.QUANTITY_NEGATIVE
import com.medishelf.core.domain.validation.MedicineValidationError.QUANTITY_REQUIRED
import com.medishelf.core.domain.validation.MedicineValidationError.THRESHOLD_NEGATIVE
import com.medishelf.core.domain.validation.MedicineValidationError.USE_WITHIN_NOT_POSITIVE
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.ScanSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.medishelf.core.model.QuantityUnit
import com.medishelf.core.ui.DateField
import com.medishelf.core.ui.DropdownField
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.label
import com.medishelf.feature.medicines.R

internal data class MedicineEditActions(
    val onNavigateUp: () -> Unit = {},
    val onFormChange: ((MedicineFormState) -> MedicineFormState) -> Unit = {},
    val onToggleMember: (Long) -> Unit = {},
    val onConditionChange: (Long, String) -> Unit = { _, _ -> },
    val onSave: () -> Unit = {},
    val onScanBarcode: () -> Unit = {},
    val onScanPack: () -> Unit = {},
)

@Composable
internal fun MedicineEditScreen(
    onNavigateUp: () -> Unit,
    onSaved: (Long, Boolean) -> Unit,
    onScanBarcode: () -> Unit = {},
    onScanPack: () -> Unit = {},
    /** Confirmed scans handed back through this destination's back stack entry. */
    scanResults: Flow<ScanSuggestion> = emptyFlow(),
    viewModel: MedicineEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(scanResults) { scanResults.collect(viewModel::onScanResult) }
    LaunchedEffect(state.savedId) { state.savedId?.let { onSaved(it, state.isNew) } }
    MedicineEditContent(
        state = state,
        actions = MedicineEditActions(
            onNavigateUp = onNavigateUp,
            onFormChange = viewModel::onFormChange,
            onToggleMember = viewModel::onToggleMember,
            onConditionChange = viewModel::onConditionChange,
            onSave = viewModel::onSave,
            onScanBarcode = onScanBarcode,
            onScanPack = onScanPack,
        ),
    )
}

@Composable
internal fun MedicineEditContent(state: MedicineEditUiState, actions: MedicineEditActions) {
    val form = state.form
    val errors = state.errors
    val update = actions.onFormChange
    Scaffold(
        topBar = {
            MediShelfTopBar(
                title = stringResource(if (state.isNew) R.string.feature_medicines_add else R.string.feature_medicines_edit),
                onNavigateUp = actions.onNavigateUp,
                actions = {
                    TextButton(onClick = actions.onSave, enabled = !state.isSaving && !state.isLoading, modifier = Modifier.testTag("save")) {
                        Text(stringResource(com.medishelf.core.ui.R.string.core_ui_save))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScanCard(state, actions)
            if (errors.isNotEmpty()) {
                Text(
                    stringResource(R.string.feature_medicines_fix_errors),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            FormCard(stringResource(R.string.feature_medicines_section_medicine)) {
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_brand),
                    value = form.brandName,
                    onValueChange = { v -> update { it.copy(brandName = v) } },
                    error = errors.messageFor(NAME_REQUIRED, NAME_TOO_LONG),
                    supporting = scannedNote(ScannedField.NAME in state.scannedFields),
                    tag = "field_brand",
                )
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_generic),
                    value = form.genericName,
                    onValueChange = { v -> update { it.copy(genericName = v) } },
                    supporting = scannedNote(ScannedField.GENERIC in state.scannedFields),
                )
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_strength),
                    value = form.strength,
                    onValueChange = { v -> update { it.copy(strength = v) } },
                    supporting = scannedNote(ScannedField.STRENGTH in state.scannedFields),
                )
                DropdownField(
                    label = stringResource(R.string.feature_medicines_field_form),
                    options = MedicineForm.entries,
                    selected = form.form,
                    optionLabel = { it.label() },
                    onSelect = { v -> update { it.copy(form = v) } },
                )
                scannedNote(ScannedField.FORM in state.scannedFields)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_manufacturer),
                    value = form.manufacturer,
                    onValueChange = { v -> update { it.copy(manufacturer = v) } },
                    supporting = scannedNote(ScannedField.MANUFACTURER in state.scannedFields),
                )
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_batch),
                    value = form.batchNumber,
                    onValueChange = { v -> update { it.copy(batchNumber = v) } },
                    supporting = scannedNote(ScannedField.BATCH in state.scannedFields),
                    tag = "field_batch",
                )
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_barcode),
                    value = form.barcode,
                    onValueChange = { v -> update { it.copy(barcode = v) } },
                    keyboardType = KeyboardType.Number,
                    supporting = scannedNote(ScannedField.BARCODE in state.scannedFields),
                )
            }

            FormCard(stringResource(R.string.feature_medicines_section_stock)) {
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_quantity),
                    value = form.quantityText,
                    onValueChange = { v -> update { it.copy(quantityText = v) } },
                    error = errors.messageFor(QUANTITY_REQUIRED, QUANTITY_NEGATIVE),
                    supporting = scannedNote(ScannedField.QUANTITY in state.scannedFields),
                    keyboardType = KeyboardType.Decimal,
                    tag = "field_quantity",
                )
                DropdownField(
                    label = stringResource(R.string.feature_medicines_field_unit),
                    options = QuantityUnit.entries,
                    selected = form.unit,
                    optionLabel = { it.label() },
                    onSelect = { v -> update { it.copy(unit = v) } },
                )
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_threshold),
                    value = form.thresholdText,
                    onValueChange = { v -> update { it.copy(thresholdText = v) } },
                    error = errors.messageFor(THRESHOLD_NEGATIVE),
                    supporting = stringResource(R.string.feature_medicines_threshold_help),
                    keyboardType = KeyboardType.Decimal,
                )
            }

            FormCard(stringResource(R.string.feature_medicines_section_dates)) {
                DateField(
                    label = stringResource(R.string.feature_medicines_field_expiry),
                    date = form.expiryDate,
                    onDateChange = { v -> update { it.copy(expiryDate = v) } },
                    errorText = errors.messageFor(EXPIRY_REQUIRED, EXPIRY_NOT_AFTER_PURCHASE),
                    supportingText = scannedNote(ScannedField.EXPIRY in state.scannedFields),
                    modifier = Modifier.testTag("field_expiry"),
                )
                DateField(
                    label = stringResource(R.string.feature_medicines_field_purchase),
                    date = form.purchaseDate,
                    onDateChange = { v -> update { it.copy(purchaseDate = v) } },
                    errorText = errors.messageFor(PURCHASE_IN_FUTURE),
                    supportingText = scannedNote(ScannedField.PURCHASE_DATE in state.scannedFields),
                )
                DateField(
                    label = stringResource(R.string.feature_medicines_field_opened),
                    date = form.openedDate,
                    onDateChange = { v -> update { it.copy(openedDate = v) } },
                    errorText = errors.messageFor(OPENED_BEFORE_PURCHASE, OPENED_IN_FUTURE),
                )
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_use_within),
                    value = form.useWithinText,
                    onValueChange = { v -> update { it.copy(useWithinText = v) } },
                    error = errors.messageFor(USE_WITHIN_NOT_POSITIVE),
                    supporting = scannedNote(ScannedField.USE_WITHIN in state.scannedFields)
                        ?: stringResource(R.string.feature_medicines_use_within_help),
                    keyboardType = KeyboardType.Number,
                )
            }

            FormCard(stringResource(R.string.feature_medicines_section_people)) {
                scannedNote(ScannedField.PEOPLE in state.scannedFields)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.members.isEmpty()) {
                    Text(stringResource(R.string.feature_medicines_no_members), style = MaterialTheme.typography.bodyMedium)
                }
                state.members.forEach { member ->
                    val checked = member.id in form.assignments
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = { actions.onToggleMember(member.id) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(member.name, modifier = Modifier.padding(start = 12.dp))
                    }
                    if (checked) {
                        ConditionInput(
                            memberName = member.name,
                            value = form.assignments.getValue(member.id),
                            suggestions = state.conditionSuggestions,
                            onValueChange = { actions.onConditionChange(member.id, it) },
                        )
                    }
                }
            }

            PrescriptionSection(state, update)

            FormCard(stringResource(R.string.feature_medicines_section_storage)) {
                val locationOptions = listOf(null) + state.locations.map { it.id }
                DropdownField(
                    label = stringResource(R.string.feature_medicines_field_location),
                    options = locationOptions,
                    selected = form.storageLocationId?.takeIf { id -> state.locations.any { it.id == id } },
                    optionLabel = { id ->
                        state.locations.find { it.id == id }?.name
                            ?: stringResource(com.medishelf.core.ui.R.string.core_ui_unassigned_location)
                    },
                    onSelect = { v -> update { it.copy(storageLocationId = v) } },
                )
                scannedNote(ScannedField.LOCATION in state.scannedFields)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_notes),
                    value = form.notes,
                    onValueChange = { v -> update { it.copy(notes = v) } },
                    supporting = scannedNote(ScannedField.NOTES in state.scannedFields),
                    singleLine = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanCard(state: MedicineEditUiState, actions: MedicineEditActions) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    when {
                        state.scannedFields.isNotEmpty() -> R.string.feature_medicines_scanned_notice
                        state.prefilledFromBarcode -> R.string.feature_medicines_prefilled_notice
                        else -> R.string.feature_medicines_scan_intro
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("scan_notice"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onScanPack, modifier = Modifier.testTag("scan_pack")) {
                    Text(stringResource(R.string.feature_medicines_scan_pack))
                }
                OutlinedButton(onClick = actions.onScanBarcode) { Text(stringResource(R.string.feature_medicines_scan_barcode)) }
            }
        }
    }
}

@Composable
internal fun scannedNote(scanned: Boolean): String? =
    if (scanned) stringResource(R.string.feature_medicines_scanned_field) else null

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionInput(
    memberName: String,
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
) {
    Column(Modifier.padding(start = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextInput(
            label = stringResource(R.string.feature_medicines_field_condition_for, memberName),
            value = value,
            onValueChange = onValueChange,
        )
        val matches = suggestions
            .filter { it.startsWith(value.trim(), ignoreCase = true) && !it.equals(value.trim(), ignoreCase = true) }
            .take(MAX_SUGGESTIONS)
        if (matches.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                matches.forEach { suggestion ->
                    SuggestionChip(onClick = { onValueChange(suggestion) }, label = { Text(suggestion) })
                }
            }
        }
    }
}

@Composable
internal fun FormCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun TextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
    tag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    )
}

@Composable
internal fun Set<MedicineValidationError>.messageFor(vararg candidates: MedicineValidationError): String? =
    candidates.firstOrNull { it in this }?.let { stringResource(it.messageRes()) }

private fun MedicineValidationError.messageRes(): Int = when (this) {
    NAME_REQUIRED -> R.string.feature_medicines_error_name_required
    NAME_TOO_LONG -> R.string.feature_medicines_error_name_too_long
    EXPIRY_REQUIRED -> R.string.feature_medicines_error_expiry_required
    QUANTITY_REQUIRED -> R.string.feature_medicines_error_quantity_required
    QUANTITY_NEGATIVE -> R.string.feature_medicines_error_quantity_negative
    THRESHOLD_NEGATIVE -> R.string.feature_medicines_error_threshold_negative
    EXPIRY_NOT_AFTER_PURCHASE -> R.string.feature_medicines_error_expiry_after_purchase
    PURCHASE_IN_FUTURE -> R.string.feature_medicines_error_purchase_future
    OPENED_BEFORE_PURCHASE -> R.string.feature_medicines_error_opened_before_purchase
    OPENED_IN_FUTURE -> R.string.feature_medicines_error_opened_future
    USE_WITHIN_NOT_POSITIVE -> R.string.feature_medicines_error_use_within
    PRESCRIPTION_IN_FUTURE -> R.string.feature_medicines_error_prescription_future
    MedicineValidationError.PRESCRIPTION_END_BEFORE_START -> R.string.feature_medicines_error_end_before_start
    MedicineValidationError.DOSE_NOT_POSITIVE -> R.string.feature_medicines_error_dose
    MedicineValidationError.DOSES_PER_DAY_INVALID -> R.string.feature_medicines_error_doses_per_day
    MedicineValidationError.DOSE_INCOMPLETE -> R.string.feature_medicines_error_dose_incomplete
}

private const val MAX_SUGGESTIONS = 5
