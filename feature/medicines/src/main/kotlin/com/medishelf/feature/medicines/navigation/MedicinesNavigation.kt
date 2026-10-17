package com.medishelf.feature.medicines.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
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

/**
 * [id] 0 = new medicine. The other fields carry a scan result when the editor is opened from a
 * scan (e.g. the dashboard's "Scan barcode"): [barcode] also prefills from a previously saved
 * medicine with the same code, and [expiry] is ISO-8601.
 */
@Serializable
data class MedicineEditRoute(
    val id: Long = 0,
    val barcode: String? = null,
    val name: String? = null,
    val strength: String? = null,
    val expiry: String? = null,
    val batch: String? = null,
)

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
    onReadLabel: () -> Unit,
) {
    composable<MedicineListRoute> {
        MedicineListScreen(onMedicineClick = onMedicineClick, onAddMedicine = onAddMedicine)
    }
    composable<MedicineDetailRoute> {
        MedicineDetailScreen(onNavigateUp = onNavigateUp, onEdit = onEditMedicine)
    }
    composable<MedicineEditRoute> {
        MedicineEditScreen(
            onNavigateUp = onNavigateUp,
            onSaved = onMedicineSaved,
            onScanBarcode = onScanBarcode,
            onReadLabel = onReadLabel,
        )
    }
}
