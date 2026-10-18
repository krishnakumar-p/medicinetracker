package com.medishelf.feature.medicines.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.compose.runtime.remember
import androidx.navigation.compose.composable
import com.medishelf.core.ui.ScanResultTransfer
import com.medishelf.feature.medicines.detail.MedicineDetailScreen
import com.medishelf.feature.medicines.edit.MedicineEditScreen
import com.medishelf.feature.medicines.list.MedicineListScreen
import kotlinx.serialization.Serializable

/** Starting filters when the list is opened from a dashboard tile. */
enum class MedicineListPreset { EXPIRED, EXPIRING, LOW_STOCK }

@Serializable
data class MedicineListRoute(val preset: MedicineListPreset? = null)

@Serializable
data class MedicineDetailRoute(val id: Long)

/** [id] 0 = new medicine. Scan results reach the editor through ScanResultTransfer. */
@Serializable
data class MedicineEditRoute(val id: Long = 0)

fun NavController.navigateToMedicineList(preset: MedicineListPreset? = null, navOptions: NavOptions? = null) =
    navigate(MedicineListRoute(preset), navOptions)

fun NavController.navigateToMedicineDetail(id: Long) = navigate(MedicineDetailRoute(id))

fun NavController.navigateToMedicineEdit(id: Long = 0) = navigate(MedicineEditRoute(id))

fun NavGraphBuilder.medicinesScreens(
    onNavigateUp: () -> Unit,
    onMedicineClick: (Long) -> Unit,
    onAddMedicine: () -> Unit,
    onEditMedicine: (Long) -> Unit,
    onMedicineSaved: (Long, isNew: Boolean) -> Unit,
    onScanBarcode: () -> Unit,
    onScanPack: () -> Unit,
    onTakeRegularly: (Long) -> Unit = {},
) {
    composable<MedicineListRoute> {
        MedicineListScreen(onMedicineClick = onMedicineClick, onAddMedicine = onAddMedicine)
    }
    composable<MedicineDetailRoute> {
        MedicineDetailScreen(onNavigateUp = onNavigateUp, onEdit = onEditMedicine, onTakeRegularly = onTakeRegularly)
    }
    composable<MedicineEditRoute> { entry ->
        // The scanner puts its result in this entry's SavedStateHandle (it survives process death).
        val scanResults = remember(entry) { ScanResultTransfer.results(entry.savedStateHandle) }
        MedicineEditScreen(
            onNavigateUp = onNavigateUp,
            onSaved = onMedicineSaved,
            onScanBarcode = onScanBarcode,
            onScanPack = onScanPack,
            scanResults = scanResults,
        )
    }
}
