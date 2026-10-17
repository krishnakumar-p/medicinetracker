package com.medishelf.feature.locations.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.medishelf.feature.locations.edit.LocationEditScreen
import com.medishelf.feature.locations.where.WhereIsItScreen
import kotlinx.serialization.Serializable

@Serializable
data object WhereIsItRoute

/** [id] 0 = new location. */
@Serializable
data class LocationEditRoute(val id: Long = 0)

fun NavController.navigateToWhereIsIt(navOptions: NavOptions? = null) = navigate(WhereIsItRoute, navOptions)
fun NavController.navigateToLocationEdit(id: Long = 0) = navigate(LocationEditRoute(id))

fun NavGraphBuilder.locationScreens(
    onNavigateUp: () -> Unit,
    onMedicineClick: (Long) -> Unit,
    onEditLocation: (Long) -> Unit,
    onAddLocation: () -> Unit,
) {
    composable<WhereIsItRoute> {
        WhereIsItScreen(onMedicineClick = onMedicineClick, onEditLocation = onEditLocation, onAddLocation = onAddLocation)
    }
    composable<LocationEditRoute> { LocationEditScreen(onDone = onNavigateUp, onNavigateUp = onNavigateUp) }
}
