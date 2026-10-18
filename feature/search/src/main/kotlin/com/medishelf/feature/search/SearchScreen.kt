package com.medishelf.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.domain.usecase.FieldMatch
import com.medishelf.core.domain.usecase.SearchField
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.MedicineListItem
import com.medishelf.core.ui.MemberAvatar
import com.medishelf.core.ui.SectionHeader

internal data class SearchActions(
    val onNavigateUp: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onScopeChange: (SearchScope) -> Unit = {},
    val onMedicineClick: (Long) -> Unit = {},
    val onMemberClick: (Long) -> Unit = {},
    val onLocationClick: (Long) -> Unit = {},
)

@Composable
internal fun SearchScreen(
    onNavigateUp: () -> Unit,
    onMedicineClick: (Long) -> Unit,
    onMemberClick: (Long) -> Unit,
    onLocationClick: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        state,
        SearchActions(
            onNavigateUp = onNavigateUp,
            onQueryChange = viewModel::onQueryChange,
            onScopeChange = viewModel::onScopeChange,
            onMedicineClick = onMedicineClick,
            onMemberClick = onMemberClick,
            onLocationClick = onLocationClick,
        ),
    )
}

@Composable
internal fun SearchContent(state: SearchUiState, actions: SearchActions, autoFocus: Boolean = true) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(topBar = { MediShelfTopBar(title = stringResource(R.string.feature_search_title), onNavigateUp = actions.onNavigateUp) }) { padding ->
        val results = state.results
        val showMedicines = state.scope == SearchScope.ALL || state.scope == SearchScope.MEDICINES
        val showPeople = state.scope == SearchScope.ALL || state.scope == SearchScope.PEOPLE
        val showPlaces = state.scope == SearchScope.ALL || state.scope == SearchScope.PLACES
        LazyColumn(
            modifier = Modifier.padding(padding).testTag("search_results"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions.onQueryChange,
                    placeholder = { Text(stringResource(R.string.feature_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { actions.onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.feature_search_clear))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search_field"),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchScope.entries) { scope ->
                        FilterChip(
                            selected = state.scope == scope,
                            onClick = { actions.onScopeChange(scope) },
                            label = { Text(scope.label(results.countFor(scope))) },
                        )
                    }
                }
            }
            when {
                state.query.isBlank() -> item {
                    Text(
                        stringResource(R.string.feature_search_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                results.query.isNotBlank() && results.isEmpty -> item {
                    Text(
                        stringResource(R.string.feature_search_nothing, state.query.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            if (showMedicines && results.medicines.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.feature_search_section_medicines), Modifier.padding(top = 8.dp)) }
                items(results.medicines, key = { "m${it.details.medicine.id}" }) { hit ->
                    val lines = hit.matches.filter { it.field != SearchField.NAME }.take(MAX_MATCH_LINES).map { it.describe() }
                    val schedule = if (hit.onSchedule) stringResource(R.string.feature_search_on_schedule) else null
                    MedicineListItem(
                        details = hit.details,
                        today = state.today,
                        onClick = { actions.onMedicineClick(hit.details.medicine.id) },
                        subtitle = (lines + listOfNotNull(schedule)).joinToString(" · ").ifEmpty { null },
                    )
                }
            }
            if (showPeople && results.members.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.feature_search_section_people), Modifier.padding(top = 8.dp)) }
                items(results.members, key = { "p${it.member.id}" }) { hit ->
                    ResultCard(
                        title = hit.member.name,
                        lines = listOf(pluralStringResource(R.plurals.feature_search_medicine_count, hit.medicineCount, hit.medicineCount)) +
                            hit.matches.filter { it.field != SearchField.NAME }.map { it.describe() },
                        onClick = { actions.onMemberClick(hit.member.id) },
                        leading = { MemberAvatar(hit.member.name, hit.member.avatarColor) },
                    )
                }
            }
            if (showPlaces && results.locations.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.feature_search_section_places), Modifier.padding(top = 8.dp)) }
                items(results.locations, key = { "l${it.location.id}" }) { hit ->
                    ResultCard(
                        title = hit.location.name,
                        lines = listOf(pluralStringResource(R.plurals.feature_search_medicine_count, hit.medicineCount, hit.medicineCount)) +
                            hit.matches.filter { it.field != SearchField.NAME }.map { it.describe() },
                        onClick = { actions.onLocationClick(hit.location.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(title: String, lines: List<String>, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                lines.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun com.medishelf.core.domain.usecase.SearchResults.countFor(scope: SearchScope): Int? =
    if (query.isBlank()) {
        null
    } else {
        when (scope) {
            SearchScope.ALL -> total
            SearchScope.MEDICINES -> medicines.size
            SearchScope.PEOPLE -> members.size
            SearchScope.PLACES -> locations.size
        }
    }

@Composable
private fun SearchScope.label(count: Int?): String {
    val name = stringResource(
        when (this) {
            SearchScope.ALL -> R.string.feature_search_scope_all
            SearchScope.MEDICINES -> R.string.feature_search_section_medicines
            SearchScope.PEOPLE -> R.string.feature_search_section_people
            SearchScope.PLACES -> R.string.feature_search_section_places
        },
    )
    return if (count == null) name else stringResource(R.string.feature_search_scope_count, name, count)
}

/** "Doctor: Dr. Hana Saleh" */
@Composable
private fun FieldMatch.describe(): String = stringResource(
    R.string.feature_search_match,
    stringResource(
        when (field) {
            SearchField.NAME -> R.string.feature_search_field_name
            SearchField.COMPOSITION -> R.string.feature_search_field_composition
            SearchField.STRENGTH -> R.string.feature_search_field_strength
            SearchField.CONDITION -> R.string.feature_search_field_condition
            SearchField.PRESCRIBED_FOR -> R.string.feature_search_field_prescribed_for
            SearchField.PERSON -> R.string.feature_search_field_person
            SearchField.DOCTOR -> R.string.feature_search_field_doctor
            SearchField.MANUFACTURER -> R.string.feature_search_field_manufacturer
            SearchField.LOCATION -> R.string.feature_search_field_location
            SearchField.DOSAGE -> R.string.feature_search_field_dosage
            SearchField.BATCH -> R.string.feature_search_field_batch
            SearchField.BARCODE -> R.string.feature_search_field_barcode
            SearchField.NOTES -> R.string.feature_search_field_notes
            SearchField.RELATIONSHIP -> R.string.feature_search_field_relationship
            SearchField.ALLERGIES -> R.string.feature_search_field_allergies
            SearchField.ROOM -> R.string.feature_search_field_room
        },
    ),
    text.take(MAX_SNIPPET),
)

private const val MAX_MATCH_LINES = 3
private const val MAX_SNIPPET = 80
