package com.medishelf.feature.medicines.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.medishelf.core.model.ExpiryWindow
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.ui.SectionHeader
import com.medishelf.core.ui.label
import com.medishelf.feature.medicines.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MedicineFilterSheet(
    state: MedicineListUiState,
    actions: MedicineListActions,
    onDismiss: () -> Unit,
) {
    val filter = state.filter
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_medicines_filters),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onClearFilters) { Text(stringResource(R.string.feature_medicines_clear_filters)) }
            }

            SectionHeader(stringResource(R.string.feature_medicines_filter_status))
            ChipRow {
                MedicineStatus.entries.forEach { status ->
                    FilterChip(
                        selected = status in filter.statuses,
                        onClick = { actions.onToggleStatus(status) },
                        label = { Text(status.label()) },
                    )
                }
            }

            SectionHeader(stringResource(R.string.feature_medicines_filter_expiry))
            ChipRow {
                ExpiryWindow.entries.forEach { window ->
                    FilterChip(
                        selected = filter.expiryWindow == window,
                        onClick = { actions.onExpiryWindowChange(window) },
                        label = { Text(window.label()) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp).toggleable(
                        value = filter.lowStockOnly,
                        role = Role.Switch,
                        onValueChange = actions.onLowStockOnlyChange,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.feature_medicines_filter_low_stock), modifier = Modifier.weight(1f))
                Switch(checked = filter.lowStockOnly, onCheckedChange = null)
            }

            if (state.members.isNotEmpty()) {
                SectionHeader(stringResource(R.string.feature_medicines_filter_people))
                ChipRow {
                    state.members.forEach { member ->
                        FilterChip(
                            selected = member.id in filter.memberIds,
                            onClick = { actions.onToggleMember(member.id) },
                            label = { Text(member.name) },
                        )
                    }
                }
            }
            if (state.locations.isNotEmpty()) {
                SectionHeader(stringResource(R.string.feature_medicines_filter_locations))
                ChipRow {
                    state.locations.forEach { location ->
                        FilterChip(
                            selected = location.id in filter.locationIds,
                            onClick = { actions.onToggleLocation(location.id) },
                            label = { Text(location.name) },
                        )
                    }
                }
            }
            SectionHeader(stringResource(R.string.feature_medicines_filter_form))
            ChipRow {
                MedicineForm.entries.forEach { form ->
                    FilterChip(
                        selected = form in filter.forms,
                        onClick = { actions.onToggleForm(form) },
                        label = { Text(form.label()) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
