package com.medishelf.feature.family.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.usecase.SaveFamilyMemberUseCase
import com.medishelf.core.domain.usecase.SaveResult
import com.medishelf.core.domain.validation.FamilyMemberValidationError
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.ui.AvatarColors
import com.medishelf.core.ui.ConfirmDialog
import com.medishelf.core.ui.DateField
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.feature.family.R
import com.medishelf.feature.family.navigation.FamilyMemberEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilyMemberEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val member: FamilyMember = FamilyMember(name = "", avatarColor = AvatarColors.first()),
    val errors: Set<FamilyMemberValidationError> = emptySet(),
    val savedId: Long? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class FamilyMemberEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val members: FamilyMemberRepository,
    private val saveMember: SaveFamilyMemberUseCase,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<FamilyMemberEditRoute>().id
    private val state = MutableStateFlow(FamilyMemberEditUiState(isNew = id == 0L))
    val uiState: StateFlow<FamilyMemberEditUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            if (id != 0L) {
                members.observe(id).first()?.let { existing -> state.update { it.copy(member = existing) } }
            } else {
                // Rotate default colours so siblings don't all start the same.
                val count = members.observeAll().first().size
                state.update { it.copy(member = it.member.copy(avatarColor = AvatarColors[count % AvatarColors.size])) }
            }
            state.update { it.copy(isLoading = false) }
        }
    }

    fun onChange(transform: (FamilyMember) -> FamilyMember) =
        state.update { it.copy(member = transform(it.member), errors = emptySet()) }

    fun onSave() = viewModelScope.launch {
        when (val result = saveMember(state.value.member)) {
            is SaveResult.Saved -> state.update { it.copy(savedId = result.id) }
            is SaveResult.Invalid -> state.update { it.copy(errors = result.errors) }
        }
    }

    fun onDelete() = viewModelScope.launch {
        members.delete(id)
        state.update { it.copy(deleted = true) }
    }
}

@Composable
internal fun FamilyMemberEditScreen(
    onNavigateUp: () -> Unit,
    onSaved: (Long, Boolean) -> Unit,
    onDeleted: () -> Unit,
    viewModel: FamilyMemberEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) { state.savedId?.let { onSaved(it, state.isNew) } }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    FamilyMemberEditContent(state, onNavigateUp, viewModel::onChange, { viewModel.onSave() }, { viewModel.onDelete() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FamilyMemberEditContent(
    state: FamilyMemberEditUiState,
    onNavigateUp: () -> Unit,
    onChange: ((FamilyMember) -> FamilyMember) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val member = state.member
    Scaffold(
        topBar = {
            MediShelfTopBar(
                title = stringResource(if (state.isNew) R.string.feature_family_add else R.string.feature_family_edit),
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
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val nameError = FamilyMemberValidationError.NAME_REQUIRED in state.errors
            OutlinedTextField(
                value = member.name,
                onValueChange = { v -> onChange { it.copy(name = v) } },
                label = { Text(stringResource(R.string.feature_family_field_name)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.feature_family_error_name)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = member.relationship,
                onValueChange = { v -> onChange { it.copy(relationship = v) } },
                label = { Text(stringResource(R.string.feature_family_field_relationship)) },
                placeholder = { Text(stringResource(R.string.feature_family_relationship_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = stringResource(R.string.feature_family_field_dob),
                date = member.dateOfBirth,
                onDateChange = { v -> onChange { it.copy(dateOfBirth = v) } },
                errorText = if (FamilyMemberValidationError.DATE_OF_BIRTH_IN_FUTURE in state.errors) {
                    stringResource(R.string.feature_family_error_dob)
                } else {
                    null
                },
            )
            OutlinedTextField(
                value = member.allergies.orEmpty(),
                onValueChange = { v -> onChange { it.copy(allergies = v) } },
                label = { Text(stringResource(R.string.feature_family_field_allergies)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.feature_family_field_color), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarColors.forEachIndexed { index, color ->
                    val selected = member.avatarColor == color
                    val description = stringResource(R.string.feature_family_color_n, index + 1)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(color), CircleShape)
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange { it.copy(avatarColor = color) } })
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.feature_family_delete_title, member.name),
            text = stringResource(R.string.feature_family_delete_body),
            confirmLabel = stringResource(com.medishelf.core.ui.R.string.core_ui_delete),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
