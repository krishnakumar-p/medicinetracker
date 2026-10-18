package com.medishelf.feature.dashboard.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.medishelf.feature.dashboard.DashboardScreen
import com.medishelf.feature.dashboard.DashboardTile
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

fun NavController.navigateToDashboard(navOptions: NavOptions? = null) = navigate(DashboardRoute, navOptions)

fun NavGraphBuilder.dashboardScreen(
    onTileClick: (DashboardTile) -> Unit,
    onAddMedicine: () -> Unit,
    onScanBarcode: () -> Unit,
    onMedicineClick: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDoses: () -> Unit,
) {
    composable<DashboardRoute> {
        DashboardScreen(
            onTileClick = onTileClick,
            onAddMedicine = onAddMedicine,
            onScanBarcode = onScanBarcode,
            onMedicineClick = onMedicineClick,
            onSearch = onSearch,
            onOpenSettings = onOpenSettings,
            onOpenDoses = onOpenDoses,
        )
    }
}
