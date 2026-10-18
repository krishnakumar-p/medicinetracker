package com.medishelf.feature.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.medishelf.core.domain.repository.SettingsRepository
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.medishelf.core.common.TimeProvider
import com.medishelf.core.designsystem.theme.MediShelfThemeExt
import com.medishelf.core.domain.usecase.ObserveDashboardCountsUseCase
import com.medishelf.core.domain.usecase.ObserveDoseDayUseCase
import com.medishelf.core.domain.usecase.ObserveMedicinesUseCase
import com.medishelf.core.model.DashboardCounts
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineFilter
import com.medishelf.core.ui.EXPIRING_SOON_DAYS
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.MedicineListItem
import com.medishelf.core.ui.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

enum class DashboardTile { EXPIRED, EXPIRING, LOW_STOCK }

data class DashboardUiState(
    val isLoading: Boolean = true,
    val counts: DashboardCounts = DashboardCounts(),
    val needsAttention: List<MedicineDetails> = emptyList(),
    val hasAnyMedicine: Boolean = false,
    val remindersEnabled: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val dosesDue: Int = 0,
    val dosesTaken: Int = 0,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeCounts: ObserveDashboardCountsUseCase,
    observeMedicines: ObserveMedicinesUseCase,
    settings: SettingsRepository,
    observeDoses: ObserveDoseDayUseCase,
    time: TimeProvider,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = combine(
        observeCounts(EXPIRING_SOON_DAYS),
        observeMedicines(flowOf(MedicineFilter())),
        settings.settings,
        observeDoses(),
    ) { counts, inCabinet, s, doses ->
        val today = time.today()
        DashboardUiState(
            isLoading = false,
            counts = counts,
            needsAttention = inCabinet.filter { it.medicine.daysUntilExpiry(today) <= EXPIRING_SOON_DAYS }.take(MAX_ATTENTION),
            hasAnyMedicine = inCabinet.isNotEmpty(),
            remindersEnabled = s.reminders.enabled,
            today = today,
            dosesDue = doses.due.size,
            dosesTaken = doses.takenCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private companion object {
        const val MAX_ATTENTION = 5
    }
}

@Composable
internal fun DashboardScreen(
    onTileClick: (DashboardTile) -> Unit,
    onAddMedicine: () -> Unit,
    onMedicineClick: (Long) -> Unit,
    onScanBarcode: () -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDoses: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(state, onTileClick, onAddMedicine, onMedicineClick, onScanBarcode, onSearch, onOpenSettings, onOpenDoses)
}

@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    onTileClick: (DashboardTile) -> Unit,
    onAddMedicine: () -> Unit,
    onMedicineClick: (Long) -> Unit,
    onScanBarcode: () -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDoses: () -> Unit = {},
) {
    val colors = MediShelfThemeExt.statusColors
    Scaffold(
        topBar = {
            MediShelfTopBar(
                title = stringResource(R.string.feature_dashboard_title),
                actions = {
                    IconButton(onClick = onSearch, modifier = Modifier.testTag("open_search")) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.feature_dashboard_search))
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.feature_dashboard_settings))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).testTag("dashboard_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.remindersEnabled) item { NotificationPermissionCard() }
            item {
                // Side by side normally; stacked full-width when large text would squeeze them.
                val tiles: @Composable (Modifier) -> Unit = { tileModifier ->
                    StatTile(
                        count = state.counts.expired,
                        label = stringResource(R.string.feature_dashboard_expired),
                        container = colors.expiredContainer,
                        content = colors.onExpiredContainer,
                        onClick = { onTileClick(DashboardTile.EXPIRED) },
                        modifier = tileModifier.testTag("tile_expired"),
                    )
                    StatTile(
                        count = state.counts.expiringSoon,
                        label = stringResource(R.string.feature_dashboard_expiring, EXPIRING_SOON_DAYS.toInt()),
                        container = colors.expiringContainer,
                        content = colors.onExpiringContainer,
                        onClick = { onTileClick(DashboardTile.EXPIRING) },
                        modifier = tileModifier.testTag("tile_expiring"),
                    )
                    StatTile(
                        count = state.counts.lowStock,
                        label = stringResource(R.string.feature_dashboard_low_stock),
                        container = colors.lowStockContainer,
                        content = colors.onLowStockContainer,
                        onClick = { onTileClick(DashboardTile.LOW_STOCK) },
                        modifier = tileModifier.testTag("tile_low_stock"),
                    )
                }
                if (LocalDensity.current.fontScale > STACK_TILES_FONT_SCALE) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tiles(Modifier.fillMaxWidth()) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                        tiles(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            item {
                Column {
                Button(onClick = onAddMedicine, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.feature_dashboard_add), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = onScanBarcode,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.feature_dashboard_scan)) }
                }
            }
            if (state.dosesDue > 0) {
                item {
                    Card(onClick = onOpenDoses, modifier = Modifier.fillMaxWidth().testTag("doses_summary")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.feature_dashboard_doses_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (state.dosesTaken == state.dosesDue) {
                                    stringResource(R.string.feature_dashboard_doses_done)
                                } else {
                                    stringResource(R.string.feature_dashboard_doses_progress, state.dosesTaken, state.dosesDue)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
            if (!state.hasAnyMedicine) {
                item {
                    Text(
                        stringResource(R.string.feature_dashboard_welcome),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                item { SectionHeader(stringResource(R.string.feature_dashboard_attention), Modifier.padding(top = 8.dp)) }
                if (state.needsAttention.isEmpty()) {
                    item { Text(stringResource(R.string.feature_dashboard_all_good), style = MaterialTheme.typography.bodyLarge) }
                }
                items(state.needsAttention, key = { it.medicine.id }) { details ->
                    MedicineListItem(details, state.today, onClick = { onMedicineClick(details.medicine.id) })
                }
            }
        }
    }
}

/** Android 13+: reminders are on by default but need the notification permission. */
@Composable
private fun NotificationPermissionCard() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose {}
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.feature_dashboard_notifications_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.feature_dashboard_notifications_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { request.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.feature_dashboard_notifications_allow))
            }
        }
    }
}

@Composable
private fun StatTile(
    count: Int,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = "$count $label"
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Column(Modifier.padding(12.dp).heightIn(min = 72.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val STACK_TILES_FONT_SCALE = 1.3f
