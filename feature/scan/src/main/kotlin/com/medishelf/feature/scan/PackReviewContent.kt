package com.medishelf.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medishelf.core.model.PackQuantity
import com.medishelf.core.ui.SectionHeader
import com.medishelf.core.ui.formatMedium
import com.medishelf.core.ui.formatQuantity
import com.medishelf.core.ui.label

/**
 * Explicit confirmation step for a pack scan: every field shows its candidates plus
 * "Don't fill", and nothing reaches the editor until the user taps "Use selected".
 */
@Composable
internal fun PackReviewContent(
    state: ScanUiState.Review,
    onSelectionChange: ((PackSelection) -> PackSelection) -> Unit,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
) {
    val s = state.suggestions
    val sel = state.selection
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
        Text(
            pluralStringResource(R.plurals.feature_scan_review_intro, state.sides, state.sides),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ChoiceGroup(stringResource(R.string.feature_scan_field_name), s.names, sel.name, { it }) { v -> onSelectionChange { it.copy(name = v) } }
        ChoiceGroup(stringResource(R.string.feature_scan_field_generic), s.genericNames, sel.genericName, { it }) { v ->
            onSelectionChange { it.copy(genericName = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_strength), s.strengths, sel.strength, { it }) { v ->
            onSelectionChange { it.copy(strength = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_form), s.forms, sel.form, { it.label() }) { v -> onSelectionChange { it.copy(form = v) } }
        ChoiceGroup(stringResource(R.string.feature_scan_field_quantity), s.quantities, sel.quantity, { it.display() }) { v ->
            onSelectionChange { it.copy(quantity = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_expiry), s.expiryDates, sel.expiry, { it.formatMedium() }) { v ->
            onSelectionChange { it.copy(expiry = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_batch), s.batchNumbers, sel.batch, { it }) { v -> onSelectionChange { it.copy(batch = v) } }
        ChoiceGroup(stringResource(R.string.feature_scan_field_barcode), listOfNotNull(s.barcode), sel.barcode, { it }) { v ->
            onSelectionChange { it.copy(barcode = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_manufacturer), s.manufacturers, sel.manufacturer, { it }) { v ->
            onSelectionChange { it.copy(manufacturer = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_dosage), s.dosageInstructions, sel.dosage, { it }) { v ->
            onSelectionChange { it.copy(dosage = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_storage), s.storageNotes, sel.storage, { it }) { v ->
            onSelectionChange { it.copy(storage = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_use_within), s.useWithinDays, sel.useWithinDays, {
            pluralStringResource(R.plurals.feature_scan_days, it, it)
        }) { v -> onSelectionChange { it.copy(useWithinDays = v) } }
        ChoiceGroup(stringResource(R.string.feature_scan_field_patient), s.patientNames, sel.patientName, { it }) { v ->
            onSelectionChange { it.copy(patientName = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_condition), s.conditions, sel.condition, { it }) { v ->
            onSelectionChange { it.copy(condition = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_prescriber), s.prescribers, sel.prescribedBy, { it }) { v ->
            onSelectionChange { it.copy(prescribedBy = v) }
        }
        ChoiceGroup(stringResource(R.string.feature_scan_field_prescription_date), s.prescriptionDates, sel.prescriptionDate, { it.formatMedium() }) { v ->
            onSelectionChange { it.copy(prescriptionDate = v) }
        }
        if (s.isPrescription) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = sel.isPrescription, role = Role.Switch, onValueChange = { v -> onSelectionChange { it.copy(isPrescription = v) } })
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.feature_scan_field_prescription), modifier = Modifier.weight(1f))
                Switch(checked = sel.isPrescription, onCheckedChange = null)
            }
        }
        RawText(s.rawText)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.feature_scan_retake)) }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f).testTag("use_selected")) {
                Text(stringResource(R.string.feature_scan_use_selected))
            }
        }
    }
}

/** Everything OCR read, collapsed by default, so the user can see why a field was or wasn't filled. */
@Composable
private fun RawText(text: String) {
    if (text.isBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(stringResource(if (expanded) R.string.feature_scan_hide_raw else R.string.feature_scan_show_raw))
    }
    if (expanded) {
        SelectionContainer {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun PackQuantity.display(): String = "${amount.formatQuantity()} ${unit.label()}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceGroup(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T?) -> Unit,
) {
    if (options.isEmpty()) return
    SectionHeader(title)
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), maxLines = 3, overflow = TextOverflow.Ellipsis) },
            )
        }
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.feature_scan_dont_fill)) },
        )
    }
}
