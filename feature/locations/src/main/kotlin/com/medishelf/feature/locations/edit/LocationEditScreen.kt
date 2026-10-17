package com.medishelf.feature.locations.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.domain.usecase.SaveResult
import com.medishelf.core.domain.usecase.SaveStorageLocationUseCase
import com.medishelf.core.domain.validation.StorageLocationValidationError
import com.medishelf.core.model.StorageLocation
import com.medishelf.core.ui.ConfirmDialog
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.feature.locations.R
import com.medishelf.feature.locations.navigation.LocationEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val location: StorageLocation = StorageLocation(name = ""),
    val errors: Set<StorageLocationValidationError> = emptySet(),
    val done: Boolean = false,
)

@HiltViewModel
class LocationEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locations: StorageLocationRepository,
    private val saveLocation: SaveStorageLocationUseCase,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<LocationEditRoute>().id
    private val state = MutableStateFlow(LocationEditUiState(isNew = id == 0L))
    val uiState: StateFlow<LocationEditUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            if (id != 0L) locations.observe(id).first()?.let { existing -> state.update { it.copy(location = existing) } }
            state.update { it.copy(isLoading = false) }
        }
    }

    fun onChange(transform: (StorageLocation) -> StorageLocation) =
        state.update { it.copy(location = transform(it.location), errors = emptySet()) }

    fun onSave() = viewModelScope.launch {
        when (val result = saveLocation(state.value.location)) {
            is SaveResult.Saved -> state.update { it.copy(done = true) }
            is SaveResult.Invalid -> state.update { it.copy(errors = result.errors) }
        }
    }

    fun onDelete() = viewModelScope.launch {
        locations.delete(id)
        state.update { it.copy(done = true) }
    }
}

@Composable
internal fun LocationEditScreen(
    onDone: () -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: LocationEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    LocationEditContent(state, onNavigateUp, viewModel::onChange, { viewModel.onSave() }, { viewModel.onDelete() })
}

@Composable
internal fun LocationEditContent(
    state: LocationEditUiState,
    onNavigateUp: () -> Unit,
    onChange: ((StorageLocation) -> StorageLocation) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val location = state.location
    Scaffold(
        topBar = {
            MediShelfTopBar(
                title = stringResource(if (state.isNew) R.string.feature_locations_add else R.string.feature_locations_edit),
                onNavigateUp = onNavigateUp,
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(com.medishelf.core.ui.R.string.core_ui_delete))
                        }
                    }
                    TextButton(onClick = onSave, enabled = !state.isLoading) {
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
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val nameError = StorageLocationValidationError.NAME_REQUIRED in state.errors
            OutlinedTextField(
                value = location.name,
                onValueChange = { v -> onChange { it.copy(name = v) } },
                label = { Text(stringResource(R.string.feature_locations_field_name)) },
                placeholder = { Text(stringResource(R.string.feature_locations_name_hint)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.feature_locations_error_name)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = location.room,
                onValueChange = { v -> onChange { it.copy(room = v) } },
                label = { Text(stringResource(R.string.feature_locations_field_room)) },
                placeholder = { Text(stringResource(R.string.feature_locations_room_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = location.requiresRefrigeration,
                    role = Role.Switch,
                    onValueChange = { v -> onChange { it.copy(requiresRefrigeration = v) } },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.feature_locations_field_refrigerated), modifier = Modifier.weight(1f))
                Switch(checked = location.requiresRefrigeration, onCheckedChange = null)
            }
            OutlinedTextField(
                value = location.notes,
                onValueChange = { v -> onChange { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.feature_locations_field_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.feature_locations_delete_title, location.name),
            text = stringResource(R.string.feature_locations_delete_body),
            confirmLabel = stringResource(com.medishelf.core.ui.R.string.core_ui_delete),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
