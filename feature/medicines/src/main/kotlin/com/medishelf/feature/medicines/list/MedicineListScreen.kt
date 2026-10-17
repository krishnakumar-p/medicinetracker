package com.medishelf.feature.medicines.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.model.MedicineSort
import com.medishelf.core.ui.EmptyState
import com.medishelf.core.ui.LabeledExtendedFab
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.MedicineListItem
import com.medishelf.core.ui.label
import com.medishelf.feature.medicines.R

@Composable
internal fun MedicineListScreen(
    onMedicineClick: (Long) -> Unit,
    onAddMedicine: () -> Unit,
    viewModel: MedicineListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MedicineListContent(
        state = state,
        actions = MedicineListActions(
            onMedicineClick = onMedicineClick,
            onAddMedicine = onAddMedicine,
            onQueryChange = viewModel::onQueryChange,
            onToggleMember = viewModel::onToggleMember,
            onToggleLocation = viewModel::onToggleLocation,
            onToggleStatus = viewModel::onToggleStatus,
            onToggleForm = viewModel::onToggleForm,
            onExpiryWindowChange = viewModel::onExpiryWindowChange,
            onLowStockOnlyChange = viewModel::onLowStockOnlyChange,
            onSortChange = viewModel::onSortChange,
            onClearFilters = viewModel::onClearFilters,
        ),
    )
}

@Composable
internal fun MedicineListContent(state: MedicineListUiState, actions: MedicineListActions) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { MediShelfTopBar(title = stringResource(R.string.feature_medicines_title)) },
        floatingActionButton = {
            LabeledExtendedFab(
                text = stringResource(R.string.feature_medicines_add),
                icon = Icons.Filled.Add,
                onClick = actions.onAddMedicine,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = actions.onQueryChange,
                placeholder = { Text(stringResource(R.string.feature_medicines_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filter.query.isNotEmpty()) {
                        IconButton(onClick = { actions.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.feature_medicines_clear_search))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("medicine_search"),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val count = state.filter.activeFilterCount
                FilterChip(
                    selected = count > 0,
                    onClick = { showFilters = true },
                    label = {
                        Text(
                            if (count > 0) {
                                stringResource(R.string.feature_medicines_filters_count, count)
                            } else {
                                stringResource(R.string.feature_medicines_filters)
                            },
                        )
                    },
                )
                MedicineSort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.filter.sort == sort,
                        onClick = { actions.onSortChange(sort) },
                        label = { Text(stringResource(R.string.feature_medicines_sort_by, sort.label())) },
                    )
                }
            }
            when {
                state.isLoading -> LoadingState()
                state.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.feature_medicines_empty_title),
                    body = stringResource(
                        if (state.filter.query.isNotEmpty() || state.filter.activeFilterCount > 0) {
                            R.string.feature_medicines_empty_filtered
                        } else {
                            R.string.feature_medicines_empty_body
                        },
                    ),
                    actionLabel = stringResource(R.string.feature_medicines_add),
                    onAction = actions.onAddMedicine,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("medicine_list"),
                ) {
                    items(state.items, key = { it.medicine.id }) { details ->
                        MedicineListItem(details, state.today, onClick = { actions.onMedicineClick(details.medicine.id) })
                    }
                }
            }
        }
    }
    if (showFilters) {
        MedicineFilterSheet(state = state, actions = actions, onDismiss = { showFilters = false })
    }
}
