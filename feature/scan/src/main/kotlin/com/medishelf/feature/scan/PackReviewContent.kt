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
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.feature_scan_retake)) }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f).testTag("use_selected")) {
                Text(stringResource(R.string.feature_scan_use_selected))
            }
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
