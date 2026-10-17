package com.medishelf.feature.family.member

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.usecase.MemberMedicine
import com.medishelf.core.domain.usecase.ObserveMedicinesForMemberUseCase
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.ui.EmptyState
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.MedicineListItem
import com.medishelf.core.ui.MemberAvatar
import com.medishelf.core.ui.SectionHeader
import com.medishelf.core.ui.formatMedium
import com.medishelf.feature.family.R
import com.medishelf.feature.family.navigation.FamilyMemberRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

data class FamilyMemberUiState(
    val isLoading: Boolean = true,
    val member: FamilyMember? = null,
    val current: List<MemberMedicine> = emptyList(),
    val past: List<MemberMedicine> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
)

@HiltViewModel
class FamilyMemberViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    members: FamilyMemberRepository,
    observeForMember: ObserveMedicinesForMemberUseCase,
    time: TimeProvider,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<FamilyMemberRoute>().id

    val uiState: StateFlow<FamilyMemberUiState> = combine(members.observe(id), observeForMember(id)) { member, items ->
        val (current, past) = items.partition {
            it.effectiveStatus == MedicineStatus.ACTIVE || it.effectiveStatus == MedicineStatus.EXPIRED
        }
        FamilyMemberUiState(isLoading = false, member = member, current = current, past = past, today = time.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyMemberUiState())
}

@Composable
internal fun FamilyMemberScreen(
    onNavigateUp: () -> Unit,
    onEdit: (Long) -> Unit,
    onMedicineClick: (Long) -> Unit,
    viewModel: FamilyMemberViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FamilyMemberContent(state, onNavigateUp, onEdit, onMedicineClick)
}

@Composable
internal fun FamilyMemberContent(
    state: FamilyMemberUiState,
    onNavigateUp: () -> Unit,
    onEdit: (Long) -> Unit,
    onMedicineClick: (Long) -> Unit,
) {
    val member = state.member
    Scaffold(
        topBar = {
            MediShelfTopBar(
                title = member?.name.orEmpty(),
                onNavigateUp = onNavigateUp,
                actions = {
                    if (member != null) {
                        IconButton(onClick = { onEdit(member.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.feature_family_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (member == null) {
            EmptyState(
                title = stringResource(R.string.feature_family_not_found),
                body = "",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { MemberHeader(member, state.today) }
            item { SectionHeader(stringResource(R.string.feature_family_current)) }
            if (state.current.isEmpty()) {
                item { Text(stringResource(R.string.feature_family_no_current), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.current, key = { "c" + it.details.medicine.id }) { item -> MemberMedicineRow(item, state.today, onMedicineClick) }
            if (state.past.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.feature_family_past)) }
                items(state.past, key = { "p" + it.details.medicine.id }) { item -> MemberMedicineRow(item, state.today, onMedicineClick) }
            }
        }
    }
}

@Composable
private fun MemberHeader(member: FamilyMember, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MemberAvatar(member.name, member.avatarColor, size = 56.dp)
            Column {
                if (member.relationship.isNotBlank()) Text(member.relationship, style = MaterialTheme.typography.titleMedium)
                member.dateOfBirth?.let { dob ->
                    Text(
                        stringResource(R.string.feature_family_age, Period.between(dob, today).years, dob.formatMedium()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        member.allergies?.let { allergies ->
            // Allergies are safety-relevant, so they are shown prominently.
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                    Column {
                        Text(stringResource(R.string.feature_family_allergies), style = MaterialTheme.typography.titleSmall)
                        Text(allergies, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberMedicineRow(item: MemberMedicine, today: LocalDate, onMedicineClick: (Long) -> Unit) {
    MedicineListItem(
        details = item.details,
        today = today,
        onClick = { onMedicineClick(item.details.medicine.id) },
        subtitle = stringResource(
            R.string.feature_family_for_condition,
            item.condition ?: stringResource(R.string.feature_family_no_condition),
        ),
    )
}
