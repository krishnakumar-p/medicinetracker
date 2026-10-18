package com.medishelf.feature.medicines.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medishelf.core.domain.validation.MedicineValidationError.DOSES_PER_DAY_INVALID
import com.medishelf.core.domain.validation.MedicineValidationError.DOSE_INCOMPLETE
import com.medishelf.core.domain.validation.MedicineValidationError.DOSE_NOT_POSITIVE
import com.medishelf.core.domain.validation.MedicineValidationError.PRESCRIPTION_END_BEFORE_START
import com.medishelf.core.domain.validation.MedicineValidationError.PRESCRIPTION_IN_FUTURE
import com.medishelf.core.ui.DateField
import com.medishelf.core.ui.DropdownField
import com.medishelf.core.ui.label
import com.medishelf.feature.medicines.R

/**
 * Prescription details: doctor, who and what it's for, the course dates, and the course dose.
 * With a dose and doses per day, the doses section asks to confirm each dose until the end date.
 */
@Composable
internal fun PrescriptionSection(
    state: MedicineEditUiState,
    update: ((MedicineFormState) -> MedicineFormState) -> Unit,
) {
    val form = state.form
    val errors = state.errors
    FormCard(stringResource(R.string.feature_medicines_section_prescription)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp).toggleable(
                    value = form.isPrescription,
                    role = Role.Switch,
                    onValueChange = { v -> update { it.copy(isPrescription = v) } },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.feature_medicines_field_is_prescription))
                scannedNote(ScannedField.PRESCRIPTION in state.scannedFields)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = form.isPrescription, onCheckedChange = null)
        }
        TextInput(
            label = stringResource(R.string.feature_medicines_field_prescribed_by),
            value = form.prescribedBy,
            onValueChange = { v -> update { it.copy(prescribedBy = v) } },
            supporting = scannedNote(ScannedField.PRESCRIBER in state.scannedFields),
            capitalization = KeyboardCapitalization.Words,
        )
        TextInput(
            label = stringResource(R.string.feature_medicines_field_prescribed_for_reason),
            value = form.prescribedForReason,
            onValueChange = { v -> update { it.copy(prescribedForReason = v) } },
            supporting = scannedNote(ScannedField.PRESCRIBED_FOR in state.scannedFields)
                ?: stringResource(R.string.feature_medicines_prescribed_for_reason_help),
            tag = "field_prescribed_for_reason",
        )
        // Picking the person needs family members; without any, the reason field above is enough.
        if (state.members.isNotEmpty()) {
            val memberOptions = listOf(null) + state.members.map { it.id }
            DropdownField(
                label = stringResource(R.string.feature_medicines_field_prescribed_for_person),
                options = memberOptions,
                selected = form.prescribedForMemberId?.takeIf { id -> state.members.any { it.id == id } },
                optionLabel = { id ->
                    state.members.find { it.id == id }?.name ?: stringResource(R.string.feature_medicines_prescribed_for_nobody)
                },
                onSelect = { v -> update { it.copy(prescribedForMemberId = v) } },
                modifier = Modifier.testTag("field_prescribed_for_person"),
            )
        }
        DateField(
            label = stringResource(R.string.feature_medicines_field_prescription_date),
            date = form.prescriptionDate,
            onDateChange = { v -> update { it.copy(prescriptionDate = v) } },
            errorText = errors.messageFor(PRESCRIPTION_IN_FUTURE),
            supportingText = scannedNote(ScannedField.PRESCRIPTION_DATE in state.scannedFields),
        )
        DateField(
            label = stringResource(R.string.feature_medicines_field_prescription_start),
            date = form.prescriptionStartDate,
            onDateChange = { v -> update { it.copy(prescriptionStartDate = v) } },
            modifier = Modifier.testTag("field_prescription_start"),
        )
        DateField(
            label = stringResource(R.string.feature_medicines_field_prescription_end),
            date = form.prescriptionEndDate,
            onDateChange = { v -> update { it.copy(prescriptionEndDate = v) } },
            errorText = errors.messageFor(PRESCRIPTION_END_BEFORE_START),
            supportingText = stringResource(R.string.feature_medicines_prescription_end_help),
            modifier = Modifier.testTag("field_prescription_end"),
        )
        TextInput(
            label = stringResource(R.string.feature_medicines_field_dosage),
            value = form.dosageInstructions,
            onValueChange = { v -> update { it.copy(dosageInstructions = v) } },
            supporting = scannedNote(ScannedField.DOSAGE in state.scannedFields),
            singleLine = false,
        )
        Text(stringResource(R.string.feature_medicines_track_doses), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.feature_medicines_track_doses_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_dose_amount, form.unit.label()),
                    value = form.doseAmountText,
                    onValueChange = { v -> update { it.copy(doseAmountText = v) } },
                    error = errors.messageFor(DOSE_NOT_POSITIVE, DOSE_INCOMPLETE),
                    keyboardType = KeyboardType.Decimal,
                    tag = "field_dose_amount",
                )
            }
            Column(Modifier.weight(1f)) {
                TextInput(
                    label = stringResource(R.string.feature_medicines_field_doses_per_day),
                    value = form.dosesPerDayText,
                    onValueChange = { v -> update { it.copy(dosesPerDayText = v) } },
                    error = errors.messageFor(DOSES_PER_DAY_INVALID),
                    keyboardType = KeyboardType.Number,
                    tag = "field_doses_per_day",
                )
            }
        }
    }
}
