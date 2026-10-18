package com.medishelf.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.navOptions
import com.medishelf.R
import com.medishelf.feature.dashboard.navigation.DashboardRoute
import com.medishelf.feature.family.navigation.FamilyRoute
import com.medishelf.feature.locations.navigation.WhereIsItRoute
import com.medishelf.feature.medicines.navigation.MedicineListRoute
import com.medishelf.feature.doses.navigation.DosesRoute
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val route: Any,
    val routeClass: KClass<*>,
) {
    HOME(Icons.Filled.Home, R.string.nav_home, DashboardRoute, DashboardRoute::class),
    MEDICINES(Icons.AutoMirrored.Filled.List, R.string.nav_medicines, MedicineListRoute(), MedicineListRoute::class),
    DOSES(Icons.Filled.DateRange, R.string.nav_doses, DosesRoute, DosesRoute::class),
    WHERE(Icons.Filled.Place, R.string.nav_where, WhereIsItRoute, WhereIsItRoute::class),
    FAMILY(Icons.Filled.Person, R.string.nav_family, FamilyRoute, FamilyRoute::class),
}

/** Switches tab like the bottom bar does: one copy per tab, each keeping its own back stack. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(
        destination.route,
        navOptions {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        },
    )
}
