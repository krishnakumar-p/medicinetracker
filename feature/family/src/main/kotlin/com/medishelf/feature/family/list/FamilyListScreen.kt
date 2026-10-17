package com.medishelf.feature.family.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.MedicineRepository
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.ui.EmptyState
import com.medishelf.core.ui.LabeledExtendedFab
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.MemberAvatar
import com.medishelf.feature.family.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MemberRow(val member: FamilyMember, val inCabinetCount: Int)

data class FamilyListUiState(val isLoading: Boolean = true, val rows: List<MemberRow> = emptyList())

@HiltViewModel
class FamilyListViewModel @Inject constructor(
    members: FamilyMemberRepository,
    medicines: MedicineRepository,
    time: TimeProvider,
) : ViewModel() {
    val uiState: StateFlow<FamilyListUiState> = combine(members.observeAll(), medicines.observeAll()) { members, all ->
        val today = time.today()
        val counts = all
            .filter { it.medicine.effectiveStatus(today).let { s -> s == MedicineStatus.ACTIVE || s == MedicineStatus.EXPIRED } }
            .flatMap { details -> details.assignments.map { it.member.id } }
            .groupingBy { it }
            .eachCount()
        FamilyListUiState(isLoading = false, rows = members.map { MemberRow(it, counts[it.id] ?: 0) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyListUiState())
}

@Composable
internal fun FamilyListScreen(
    onMemberClick: (Long) -> Unit,
    onAddMember: () -> Unit,
    viewModel: FamilyListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FamilyListContent(state, onMemberClick, onAddMember)
}

@Composable
internal fun FamilyListContent(state: FamilyListUiState, onMemberClick: (Long) -> Unit, onAddMember: () -> Unit) {
    Scaffold(
        topBar = { MediShelfTopBar(title = stringResource(R.string.feature_family_title)) },
        floatingActionButton = {
            LabeledExtendedFab(
                text = stringResource(R.string.feature_family_add),
                icon = Icons.Filled.Add,
                onClick = onAddMember,
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.rows.isEmpty() -> EmptyState(
                title = stringResource(R.string.feature_family_empty_title),
                body = stringResource(R.string.feature_family_empty_body),
                actionLabel = stringResource(R.string.feature_family_add),
                onAction = onAddMember,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.rows, key = { it.member.id }) { row ->
                    Card(onClick = { onMemberClick(row.member.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            MemberAvatar(row.member.name, row.member.avatarColor)
                            Column(Modifier.weight(1f)) {
                                Text(row.member.name, style = MaterialTheme.typography.titleMedium)
                                val subtitle = listOf(
                                    row.member.relationship,
                                    pluralStringResource(R.plurals.feature_family_medicine_count, row.inCabinetCount, row.inCabinetCount),
                                ).filter(String::isNotBlank).joinToString(" · ")
                                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
