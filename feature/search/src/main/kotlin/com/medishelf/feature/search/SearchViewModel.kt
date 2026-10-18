package com.medishelf.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.domain.usecase.ObserveGlobalSearchUseCase
import com.medishelf.core.domain.usecase.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

enum class SearchScope { ALL, MEDICINES, PEOPLE, PLACES }

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val results: SearchResults = SearchResults(),
    val today: LocalDate = LocalDate.MIN,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    search: ObserveGlobalSearchUseCase,
    time: TimeProvider,
) : ViewModel() {
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val scope = savedStateHandle.getStateFlow(KEY_SCOPE, SearchScope.ALL)

    val uiState: StateFlow<SearchUiState> = combine(query, scope, search(query)) { q, s, results ->
        SearchUiState(query = q, scope = s, results = results, today = time.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) { savedStateHandle[KEY_QUERY] = value.take(MAX_QUERY) }

    fun onScopeChange(value: SearchScope) { savedStateHandle[KEY_SCOPE] = value }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_SCOPE = "scope"
        const val MAX_QUERY = 100
    }
}
