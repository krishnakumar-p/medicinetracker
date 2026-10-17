package com.medishelf.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medishelf.core.ui.SectionHeader
import com.medishelf.core.ui.formatMedium

/**
 * Explicit confirmation step for OCR: every field shows its candidates plus "Don't fill",
 * and nothing reaches the editor until the user taps "Use selected".
 */
@Composable
internal fun LabelReviewContent(
    state: ScanUiState.LabelReview,
    onSelectionChange: ((LabelSelection) -> LabelSelection) -> Unit,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
) {
    val suggestions = state.suggestions
    val selection = state.selection
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
        Text(
            stringResource(R.string.feature_scan_review_intro),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ChoiceGroup(
            title = stringResource(R.string.feature_scan_field_name),
            options = suggestions.names,
            selected = selection.name,
            label = { it },
            onSelect = { v -> onSelectionChange { it.copy(name = v) } },
        )
        ChoiceGroup(
            title = stringResource(R.string.feature_scan_field_strength),
            options = suggestions.strengths,
            selected = selection.strength,
            label = { it },
            onSelect = { v -> onSelectionChange { it.copy(strength = v) } },
        )
        ChoiceGroup(
            title = stringResource(R.string.feature_scan_field_expiry),
            options = suggestions.expiryDates,
            selected = selection.expiry,
            label = { it.formatMedium() },
            onSelect = { v -> onSelectionChange { it.copy(expiry = v) } },
        )
        ChoiceGroup(
            title = stringResource(R.string.feature_scan_field_batch),
            options = suggestions.batchNumbers,
            selected = selection.batch,
            label = { it },
            onSelect = { v -> onSelectionChange { it.copy(batch = v) } },
        )
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.feature_scan_retake)) }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f).testTag("use_selected")) {
                Text(stringResource(R.string.feature_scan_use_selected))
            }
        }
    }
}

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
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.feature_scan_dont_fill)) },
        )
    }
}
