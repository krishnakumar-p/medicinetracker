package com.medishelf.feature.doses.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.domain.usecase.DueDose
import com.medishelf.core.domain.usecase.ScheduleOverview
import com.medishelf.core.domain.usecase.ScheduleState
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.Medicine
import com.medishelf.core.ui.ConfirmDialog
import com.medishelf.core.ui.EmptyState
import com.medishelf.core.ui.LabeledExtendedFab
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.SectionHeader
import com.medishelf.core.ui.formatMedium
import com.medishelf.core.ui.formatQuantity
import com.medishelf.core.ui.label
import com.medishelf.feature.doses.R

internal data class DosesActions(
    val onToggle: (DueDose) -> Unit = {},
    val onStop: (Long) -> Unit = {},
    val onMedicineClick: (Long) -> Unit = {},
    val onEditMedicine: (Long) -> Unit = {},
    val onAddSchedule: () -> Unit = {},
    val onEditSchedule: (Long) -> Unit = {},
)

@Composable
internal fun DosesScreen(
    onMedicineClick: (Long) -> Unit,
    onEditMedicine: (Long) -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (Long) -> Unit,
    viewModel: DosesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    val snackbar = remember { SnackbarHostState() }
    val text = state.message?.let {
        stringResource(
            when (it) {
                DoseMessage.OUT_OF_STOCK -> R.string.feature_doses_out_of_stock_message
                DoseMessage.PACK_FINISHED -> R.string.feature_doses_finished_message
                DoseMessage.STOPPED -> R.string.feature_doses_stopped_message
            },
        )
    }
    LaunchedEffect(text) {
        text?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }
    DosesContent(
        state = state,
        snackbar = snackbar,
        actions = DosesActions(
            onToggle = viewModel::onToggle,
            onStop = viewModel::onStop,
            onMedicineClick = onMedicineClick,
            onEditMedicine = onEditMedicine,
            onAddSchedule = onAddSchedule,
            onEditSchedule = onEditSchedule,
        ),
    )
}

@Composable
internal fun DosesContent(
    state: DosesUiState,
    actions: DosesActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmStop by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = { MediShelfTopBar(title = stringResource(R.string.feature_doses_title)) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            LabeledExtendedFab(
                text = stringResource(R.string.feature_doses_add_continuous),
                icon = Icons.Filled.Add,
                onClick = actions.onAddSchedule,
                modifier = Modifier.testTag("add_continuous"),
            )
        },
    ) { padding ->
        val day = state.day
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            day.schedules.isEmpty() -> EmptyState(
                title = stringResource(R.string.feature_doses_empty_title),
                body = stringResource(R.string.feature_doses_empty_body),
                actionLabel = stringResource(R.string.feature_doses_add_continuous),
                onAction = actions.onAddSchedule,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).testTag("doses_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SectionHeader(
                        if (day.due.isEmpty()) {
                            stringResource(R.string.feature_doses_today_none)
                        } else {
                            stringResource(R.string.feature_doses_today_progress, day.takenCount, day.due.size)
                        },
                    )
                }
                items(day.due, key = { "${it.schedule.id}-${it.slot}" }) { dose -> DoseRow(dose, actions.onToggle) }
                val continuous = day.schedules.filter { it.schedule.kind == DoseScheduleKind.CONTINUOUS }
                val courses = day.schedules.filter { it.schedule.kind == DoseScheduleKind.PRESCRIPTION }
                if (continuous.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.feature_doses_section_continuous), Modifier.padding(top = 16.dp)) }
                    items(continuous, key = { "s${it.schedule.id}" }) { overview ->
                        ScheduleCard(
                            overview = overview,
                            onOpen = { actions.onMedicineClick(overview.details.medicine.id) },
                            onEdit = { actions.onEditSchedule(overview.schedule.id) },
                            onStop = { confirmStop = overview.schedule.id }.takeIf { overview.state != ScheduleState.ENDED },
                        )
                    }
                }
                if (courses.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.feature_doses_section_courses), Modifier.padding(top = 16.dp)) }
                    items(courses, key = { "s${it.schedule.id}" }) { overview ->
                        // A course is defined by its prescription, so it is edited in the medicine editor.
                        ScheduleCard(
                            overview = overview,
                            onOpen = { actions.onMedicineClick(overview.details.medicine.id) },
                            onEdit = { actions.onEditMedicine(overview.details.medicine.id) },
                            onStop = null,
                        )
                    }
                }
            }
        }
    }
    confirmStop?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.feature_doses_stop_title),
            text = stringResource(R.string.feature_doses_stop_body),
            confirmLabel = stringResource(R.string.feature_doses_stop),
            onConfirm = {
                actions.onStop(id)
                confirmStop = null
            },
            onDismiss = { confirmStop = null },
        )
    }
}

@Composable
private fun DoseRow(dose: DueDose, onToggle: (DueDose) -> Unit) {
    val medicine = dose.details.medicine
    val enabled = dose.taken || !dose.outOfStock
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = dose.taken, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(dose) })
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("dose_${dose.schedule.id}_${dose.slot}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = dose.taken, onCheckedChange = null, enabled = enabled)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(medicine.titleWithStrength(), style = MaterialTheme.typography.titleSmall)
                val details = listOfNotNull(
                    doseAmount(dose.schedule, medicine),
                    if (dose.schedule.timesPerDay > 1) {
                        stringResource(R.string.feature_doses_slot, dose.slot + 1, dose.schedule.timesPerDay)
                    } else {
                        null
                    },
                    dose.member?.name,
                ).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    dose.outOfStock -> Warning(stringResource(R.string.feature_doses_not_enough_stock))
                    dose.expired -> Warning(stringResource(R.string.feature_doses_expired_warning))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(
    overview: ScheduleOverview,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onStop: (() -> Unit)?,
) {
    val schedule = overview.schedule
    val medicine = overview.details.medicine
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = if (overview.state == ScheduleState.ENDED) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(medicine.titleWithStrength(), style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    doseAmount(schedule, medicine) + ", " +
                        pluralStringResource(R.plurals.feature_doses_times_per_day, schedule.timesPerDay, schedule.timesPerDay),
                    overview.member?.name,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                period(schedule, overview.state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (overview.state != ScheduleState.ENDED) {
                overview.daysOfStockLeft?.let { days ->
                    val stock = pluralStringResource(R.plurals.feature_doses_days_left, days, days)
                    if (overview.runsLow) Warning(stock) else Text(stock, style = MaterialTheme.typography.bodySmall)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.feature_doses_edit)) }
                if (onStop != null) TextButton(onClick = onStop) { Text(stringResource(R.string.feature_doses_stop)) }
            }
        }
    }
}

@Composable
private fun period(schedule: DoseSchedule, state: ScheduleState): String {
    val end = schedule.endDate
    return when {
        state == ScheduleState.UPCOMING -> stringResource(R.string.feature_doses_starts, schedule.startDate.formatMedium())
        state == ScheduleState.ENDED && end != null -> stringResource(R.string.feature_doses_ended, end.formatMedium())
        end != null -> stringResource(R.string.feature_doses_until, end.formatMedium())
        else -> stringResource(R.string.feature_doses_ongoing_since, schedule.startDate.formatMedium())
    }
}

@Composable
private fun doseAmount(schedule: DoseSchedule, medicine: Medicine): String =
    stringResource(R.string.feature_doses_amount, schedule.dosePerIntake.formatQuantity(), medicine.unit.label())

private fun Medicine.titleWithStrength(): String = listOf(displayName, strength).filter(String::isNotBlank).joinToString(" ")

@Composable
private fun Warning(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
