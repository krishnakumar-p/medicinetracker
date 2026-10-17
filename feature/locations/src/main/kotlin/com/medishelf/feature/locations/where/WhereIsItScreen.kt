package com.medishelf.feature.locations.where

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.usecase.LocationGroup
import com.medishelf.core.domain.usecase.ObserveMedicinesByLocationUseCase
import com.medishelf.core.ui.EmptyState
import com.medishelf.core.ui.LabeledExtendedFab
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.MedicineListItem
import com.medishelf.feature.locations.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class WhereIsItUiState(
    val isLoading: Boolean = true,
    val groups: List<LocationGroup> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
)

@HiltViewModel
class WhereIsItViewModel @Inject constructor(
    observeByLocation: ObserveMedicinesByLocationUseCase,
    time: TimeProvider,
) : ViewModel() {
    val uiState: StateFlow<WhereIsItUiState> = observeByLocation()
        .map { WhereIsItUiState(isLoading = false, groups = it, today = time.today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhereIsItUiState())
}

@Composable
internal fun WhereIsItScreen(
    onMedicineClick: (Long) -> Unit,
    onEditLocation: (Long) -> Unit,
    onAddLocation: () -> Unit,
    viewModel: WhereIsItViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WhereIsItContent(state, onMedicineClick, onEditLocation, onAddLocation)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WhereIsItContent(
    state: WhereIsItUiState,
    onMedicineClick: (Long) -> Unit,
    onEditLocation: (Long) -> Unit,
    onAddLocation: () -> Unit,
) {
    Scaffold(
        topBar = { MediShelfTopBar(title = stringResource(R.string.feature_locations_title)) },
        floatingActionButton = {
            LabeledExtendedFab(
                text = stringResource(R.string.feature_locations_add),
                icon = Icons.Filled.Add,
                onClick = onAddLocation,
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.groups.isEmpty() -> EmptyState(
                title = stringResource(R.string.feature_locations_empty_title),
                body = stringResource(R.string.feature_locations_empty_body),
                actionLabel = stringResource(R.string.feature_locations_add),
                onAction = onAddLocation,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.groups.forEach { group ->
                    stickyHeader(key = "h${group.location?.id ?: "none"}") { GroupHeader(group, onEditLocation) }
                    if (group.medicines.isEmpty()) {
                        item(key = "e${group.location?.id}") {
                            Text(
                                stringResource(R.string.feature_locations_nothing_here),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    items(group.medicines, key = { "m${it.medicine.id}" }) { details ->
                        MedicineListItem(
                            details = details,
                            today = state.today,
                            onClick = { onMedicineClick(details.medicine.id) },
                            subtitle = details.assignments.joinToString(", ") { it.member.name },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: LocationGroup, onEditLocation: (Long) -> Unit) {
    val location = group.location
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                Text(
                    location?.name ?: stringResource(com.medishelf.core.ui.R.string.core_ui_unassigned_location),
                    style = MaterialTheme.typography.titleMedium,
                )
                val details = listOfNotNull(
                    location?.room?.takeIf(String::isNotBlank),
                    if (location?.requiresRefrigeration == true) stringResource(R.string.feature_locations_refrigerated) else null,
                    pluralStringResource(R.plurals.feature_locations_item_count, group.medicines.size, group.medicines.size),
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (location != null) {
                IconButton(onClick = { onEditLocation(location.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.feature_locations_edit_named, location.name))
                }
            }
        }
    }
}
