package com.medishelf.feature.medicines.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.repository.FamilyMemberRepository
import com.medishelf.core.domain.repository.StorageLocationRepository
import com.medishelf.core.domain.usecase.ObserveMedicinesUseCase
import com.medishelf.core.model.ExpiryWindow
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineFilter
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineSort
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.StorageLocation
import com.medishelf.feature.medicines.navigation.MedicineListPreset
import com.medishelf.feature.medicines.navigation.MedicineListRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

data class MedicineListUiState(
    val isLoading: Boolean = true,
    val items: List<MedicineDetails> = emptyList(),
    val filter: MedicineFilter = MedicineFilter(),
    val members: List<FamilyMember> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val today: LocalDate = LocalDate.MIN,
)

@HiltViewModel
class MedicineListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeMedicines: ObserveMedicinesUseCase,
    members: FamilyMemberRepository,
    locations: StorageLocationRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val filter = MutableStateFlow(savedStateHandle.toRoute<MedicineListRoute>().preset.toFilter())

    val uiState: StateFlow<MedicineListUiState> = combine(
        observeMedicines(filter),
        filter,
        members.observeAll(),
        locations.observeAll(),
    ) { items, filter, members, locations ->
        MedicineListUiState(
            isLoading = false,
            items = items,
            filter = filter,
            members = members,
            locations = locations,
            today = time.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicineListUiState(filter = filter.value))

    fun onQueryChange(query: String) = filter.update { it.copy(query = query) }
    fun onToggleMember(id: Long) = filter.update { it.copy(memberIds = it.memberIds.toggle(id)) }
    fun onToggleLocation(id: Long) = filter.update { it.copy(locationIds = it.locationIds.toggle(id)) }
    fun onToggleStatus(status: MedicineStatus) = filter.update { it.copy(statuses = it.statuses.toggle(status)) }
    fun onToggleForm(form: MedicineForm) = filter.update { it.copy(forms = it.forms.toggle(form)) }
    fun onExpiryWindowChange(window: ExpiryWindow) = filter.update { it.copy(expiryWindow = window) }
    fun onLowStockOnlyChange(enabled: Boolean) = filter.update { it.copy(lowStockOnly = enabled) }
    fun onSortChange(sort: MedicineSort) = filter.update { it.copy(sort = sort) }
    fun onClearFilters() = filter.update { MedicineFilter(query = it.query, sort = it.sort) }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
}

internal fun MedicineListPreset?.toFilter(): MedicineFilter = when (this) {
    null -> MedicineFilter()
    MedicineListPreset.EXPIRED -> MedicineFilter(expiryWindow = ExpiryWindow.EXPIRED)
    MedicineListPreset.EXPIRING -> MedicineFilter(
        statuses = setOf(MedicineStatus.ACTIVE),
        expiryWindow = ExpiryWindow.WITHIN_30_DAYS,
    )
    MedicineListPreset.LOW_STOCK -> MedicineFilter(lowStockOnly = true)
}
