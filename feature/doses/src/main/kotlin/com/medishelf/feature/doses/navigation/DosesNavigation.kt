package com.medishelf.feature.doses.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.medishelf.feature.doses.edit.ScheduleEditScreen
import com.medishelf.feature.doses.today.DosesScreen
import kotlinx.serialization.Serializable

@Serializable
data object DosesRoute

/** [id] 0 = new continuous medication; [medicineId] preselects the medicine. */
@Serializable
data class ScheduleEditRoute(val id: Long = 0, val medicineId: Long = 0)

fun NavController.navigateToScheduleEdit(id: Long = 0, medicineId: Long = 0) = navigate(ScheduleEditRoute(id, medicineId))

fun NavGraphBuilder.dosesScreens(
    onNavigateUp: () -> Unit,
    onMedicineClick: (Long) -> Unit,
    onEditMedicine: (Long) -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (Long) -> Unit,
) {
    composable<DosesRoute> {
        DosesScreen(
            onMedicineClick = onMedicineClick,
            onEditMedicine = onEditMedicine,
            onAddSchedule = onAddSchedule,
            onEditSchedule = onEditSchedule,
        )
    }
    composable<ScheduleEditRoute> { ScheduleEditScreen(onNavigateUp = onNavigateUp) }
}
