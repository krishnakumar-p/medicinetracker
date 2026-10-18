package com.medishelf.feature.doses.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.domain.usecase.ScheduleError
import com.medishelf.core.ui.DateField
import com.medishelf.core.ui.DropdownField
import com.medishelf.core.ui.EmptyState
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.label
import com.medishelf.core.ui.quantityLabel
import com.medishelf.feature.doses.R

internal data class ScheduleEditActions(
    val onNavigateUp: () -> Unit = {},
    val onFormChange: ((ScheduleForm) -> ScheduleForm) -> Unit = {},
    val onMedicineSelected: (Long) -> Unit = {},
    val onSave: () -> Unit = {},
)

@Composable
internal fun ScheduleEditScreen(
    onNavigateUp: () -> Unit,
    viewModel: ScheduleEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onNavigateUp() }
    ScheduleEditContent(
        state,
        ScheduleEditActions(
            onNavigateUp = onNavigateUp,
            onFormChange = viewModel::onFormChange,
            onMedicineSelected = viewModel::onMedicineSelected,
            onSave = { viewModel.onSave() },
        ),
    )
}

@Composable
internal fun ScheduleEditContent(state: ScheduleEditUiState, actions: ScheduleEditActions) {
    val form = state.form
    val errors = state.errors
    val update = actions.onFormChange
    Scaffold(
        topBar = {
            MediShelfTopBar(
                title = stringResource(if (state.isNew) R.string.feature_doses_add_continuous else R.string.feature_doses_edit_title),
                onNavigateUp = actions.onNavigateUp,
                actions = {
                    TextButton(
                        onClick = actions.onSave,
                        enabled = !state.isLoading && state.medicines.isNotEmpty(),
                        modifier = Modifier.testTag("save_schedule"),
                    ) { Text(stringResource(com.medishelf.core.ui.R.string.core_ui_save)) }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.medicines.isEmpty() && form.medicineId == null -> EmptyState(
                title = stringResource(R.string.feature_doses_no_medicines_title),
                body = stringResource(R.string.feature_doses_no_medicines_body),
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.feature_doses_continuous_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val selected = state.medicines.find { it.medicine.id == form.medicineId }
                DropdownField(
                    label = stringResource(R.string.feature_doses_field_medicine),
                    options = listOf<Long?>(null) + state.medicines.map { it.medicine.id },
                    selected = form.medicineId,
                    optionLabel = { id ->
                        state.medicines.find { it.medicine.id == id }?.medicine?.let { m ->
                            listOf(m.displayName, m.strength).filter(String::isNotBlank).joinToString(" ")
                        } ?: stringResource(R.string.feature_doses_choose_medicine)
                    },
                    onSelect = { id -> id?.let(actions.onMedicineSelected) },
                    modifier = Modifier.testTag("field_medicine"),
                )
                errors.messageFor(ScheduleError.MEDICINE_REQUIRED)?.let { ErrorText(it) }
                selected?.let {
                    Text(
                        stringResource(R.string.feature_doses_in_stock, it.medicine.quantityLabel()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownField(
                    label = stringResource(R.string.feature_doses_field_person),
                    options = listOf<Long?>(null) + state.members.map { it.id },
                    selected = form.memberId?.takeIf { id -> state.members.any { it.id == id } },
                    optionLabel = { id -> state.members.find { it.id == id }?.name ?: stringResource(R.string.feature_doses_person_none) },
                    onSelect = { v -> update { it.copy(memberId = v) } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberInput(
                        label = stringResource(R.string.feature_doses_field_dose, selected?.medicine?.unit?.label().orEmpty()),
                        value = form.doseText,
                        onValueChange = { v -> update { it.copy(doseText = v) } },
                        error = errors.messageFor(ScheduleError.DOSE_NOT_POSITIVE),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f).testTag("field_dose"),
                    )
                    NumberInput(
                        label = stringResource(R.string.feature_doses_field_times),
                        value = form.timesText,
                        onValueChange = { v -> update { it.copy(timesText = v) } },
                        error = errors.messageFor(ScheduleError.TIMES_PER_DAY_INVALID),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f).testTag("field_times"),
                    )
                }
                DateField(
                    label = stringResource(R.string.feature_doses_field_start),
                    date = form.startDate,
                    onDateChange = { v -> update { it.copy(startDate = v) } },
                    clearable = false,
                )
                DateField(
                    label = stringResource(R.string.feature_doses_field_end),
                    date = form.endDate,
                    onDateChange = { v -> update { it.copy(endDate = v) } },
                    errorText = errors.messageFor(ScheduleError.END_BEFORE_START),
                    supportingText = stringResource(R.string.feature_doses_end_help),
                )
            }
        }
    }
}

@Composable
private fun NumberInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Set<ScheduleError>.messageFor(error: ScheduleError): String? =
    if (error in this) {
        stringResource(
            when (error) {
                ScheduleError.MEDICINE_REQUIRED -> R.string.feature_doses_error_medicine
                ScheduleError.DOSE_NOT_POSITIVE -> R.string.feature_doses_error_dose
                ScheduleError.TIMES_PER_DAY_INVALID -> R.string.feature_doses_error_times
                ScheduleError.END_BEFORE_START -> R.string.feature_doses_error_end
            },
        )
    } else {
        null
    }
