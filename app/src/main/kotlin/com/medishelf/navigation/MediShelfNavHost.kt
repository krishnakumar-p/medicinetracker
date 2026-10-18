package com.medishelf.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.medishelf.feature.dashboard.DashboardTile
import com.medishelf.feature.dashboard.navigation.DashboardRoute
import com.medishelf.feature.dashboard.navigation.dashboardScreen
import com.medishelf.feature.family.navigation.FamilyMemberEditRoute
import com.medishelf.feature.family.navigation.FamilyMemberRoute
import com.medishelf.feature.family.navigation.FamilyRoute
import com.medishelf.feature.family.navigation.familyScreens
import com.medishelf.feature.family.navigation.navigateToFamilyMember
import com.medishelf.feature.family.navigation.navigateToFamilyMemberEdit
import com.medishelf.feature.locations.navigation.locationScreens
import com.medishelf.feature.locations.navigation.navigateToLocationEdit
import com.medishelf.feature.medicines.navigation.MedicineDetailRoute
import com.medishelf.feature.medicines.navigation.MedicineEditRoute
import com.medishelf.feature.medicines.navigation.MedicineListPreset
import com.medishelf.feature.medicines.navigation.medicinesScreens
import com.medishelf.feature.medicines.navigation.navigateToMedicineDetail
import com.medishelf.feature.medicines.navigation.navigateToMedicineEdit
import com.medishelf.feature.medicines.navigation.navigateToMedicineList
import com.medishelf.feature.settings.navigation.navigateToSettings
import com.medishelf.feature.settings.navigation.settingsScreen
import com.medishelf.feature.doses.navigation.dosesScreens
import com.medishelf.feature.doses.navigation.navigateToScheduleEdit
import com.medishelf.feature.search.navigation.navigateToSearch
import com.medishelf.feature.search.navigation.searchScreen
import com.medishelf.feature.lock.navigation.lockScreens
import com.medishelf.feature.lock.navigation.navigateToAppLockSettings
import com.medishelf.feature.lock.navigation.navigateToPinFlow
import com.medishelf.core.model.ScanSuggestion
import com.medishelf.core.ui.ScanResultTransfer
import com.medishelf.feature.scan.navigation.ScanMode
import com.medishelf.feature.scan.navigation.ScanRoute
import com.medishelf.feature.scan.navigation.navigateToScan
import com.medishelf.feature.scan.navigation.scanScreen
import androidx.navigation.NavDestination.Companion.hasRoute
import com.medishelf.BuildConfig

@Composable
fun MediShelfNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val navigateUp: () -> Unit = { navController.navigateUp() }
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
        modifier = modifier,
    ) {
        dashboardScreen(
            onTileClick = { tile -> navController.navigateToMedicineList(tile.toPreset()) },
            onAddMedicine = { navController.navigateToMedicineEdit() },
            onScanBarcode = { navController.navigateToScan(ScanMode.PACK) },
            onMedicineClick = navController::navigateToMedicineDetail,
            onSearch = navController::navigateToSearch,
            onOpenSettings = { navController.navigateToSettings() },
            onOpenDoses = { navController.navigateToTopLevel(TopLevelDestination.DOSES) },
        )
        searchScreen(
            onNavigateUp = navigateUp,
            onMedicineClick = navController::navigateToMedicineDetail,
            onMemberClick = navController::navigateToFamilyMember,
            onLocationClick = navController::navigateToLocationEdit,
        )
        dosesScreens(
            onNavigateUp = navigateUp,
            onMedicineClick = navController::navigateToMedicineDetail,
            onEditMedicine = { id -> navController.navigateToMedicineEdit(id) },
            onAddSchedule = { navController.navigateToScheduleEdit() },
            onEditSchedule = { id -> navController.navigateToScheduleEdit(id = id) },
        )
        medicinesScreens(
            onNavigateUp = navigateUp,
            onMedicineClick = navController::navigateToMedicineDetail,
            onAddMedicine = { navController.navigateToMedicineEdit() },
            onEditMedicine = { id -> navController.navigateToMedicineEdit(id) },
            onMedicineSaved = { id, isNew ->
                if (isNew) {
                    // Replace the editor with the new medicine's detail so Back returns to where Add was tapped.
                    navController.navigate(MedicineDetailRoute(id)) {
                        popUpTo<MedicineEditRoute> { inclusive = true }
                    }
                } else {
                    navController.popBackStack()
                }
            },
            onScanBarcode = { navController.navigateToScan(ScanMode.BARCODE) },
            onScanPack = { navController.navigateToScan(ScanMode.PACK) },
            onTakeRegularly = { id -> navController.navigateToScheduleEdit(medicineId = id) },
        )
        scanScreen(
            onNavigateUp = navigateUp,
            onResult = { result -> navController.deliverScanResult(result) },
        )
        locationScreens(
            onNavigateUp = navigateUp,
            onMedicineClick = navController::navigateToMedicineDetail,
            onEditLocation = navController::navigateToLocationEdit,
            onAddLocation = { navController.navigateToLocationEdit() },
        )
        familyScreens(
            onNavigateUp = navigateUp,
            onMemberClick = navController::navigateToFamilyMember,
            onAddMember = { navController.navigateToFamilyMemberEdit() },
            onEditMember = navController::navigateToFamilyMemberEdit,
            onMedicineClick = navController::navigateToMedicineDetail,
            onMemberSaved = { id, isNew ->
                if (isNew) {
                    navController.navigate(FamilyMemberRoute(id)) {
                        popUpTo<FamilyMemberEditRoute> { inclusive = true }
                    }
                } else {
                    navController.popBackStack()
                }
            },
            onMemberDeleted = { navController.popBackStack<FamilyRoute>(inclusive = false) },
        )
        settingsScreen(
            onOpenAppLock = navController::navigateToAppLockSettings,
            showDeveloperOptions = BuildConfig.DEBUG,
            versionName = BuildConfig.VERSION_NAME,
            onNavigateUp = navigateUp,
        )
        lockScreens(onNavigateUp = navigateUp, onPinFlow = navController::navigateToPinFlow)
    }
}

/**
 * Opened from the editor: hand the result back to it. Opened from elsewhere (dashboard):
 * replace the scanner with a new editor and hand the result to that.
 */
private fun NavHostController.deliverScanResult(result: ScanSuggestion) {
    val previous = previousBackStackEntry
    if (previous?.destination?.hasRoute<MedicineEditRoute>() == true) {
        ScanResultTransfer.put(previous.savedStateHandle, result)
        popBackStack()
    } else {
        navigate(MedicineEditRoute()) { popUpTo<ScanRoute> { inclusive = true } }
        currentBackStackEntry?.savedStateHandle?.let { ScanResultTransfer.put(it, result) }
    }
}

private fun DashboardTile.toPreset() = when (this) {
    DashboardTile.EXPIRED -> MedicineListPreset.EXPIRED
    DashboardTile.EXPIRING -> MedicineListPreset.EXPIRING
    DashboardTile.LOW_STOCK -> MedicineListPreset.LOW_STOCK
}
