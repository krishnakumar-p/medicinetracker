package com.medishelf.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.medishelf.feature.medicines.navigation.MedicineListPreset
import com.medishelf.feature.medicines.navigation.navigateToMedicineList
import com.medishelf.navigation.MediShelfNavHost
import com.medishelf.navigation.TopLevelDestination

@Composable
fun MediShelfApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    openList: MedicineListPreset? = null,
    onOpenListHandled: () -> Unit = {},
) {
    // From a reminder notification: jump to the matching filtered list once the app is unlocked.
    LaunchedEffect(openList) {
        openList?.let {
            navController.navigateToMedicineList(it)
            onOpenListHandled()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Bottom bar only on the five top-level screens; detail and edit screens get the full height.
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isInHierarchy(it) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) NavigationBar {
                // Five tabs share the width: cap label scaling so words never break mid-word.
                val density = LocalDensity.current
                val labelDensity = Density(density.density, fontScale = minOf(density.fontScale, MAX_NAV_LABEL_SCALE))
                TopLevelDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        selected = currentDestination.isInHierarchy(destination),
                        onClick = { navController.navigateToTopLevel(destination) },
                        // Label text already describes the item; icon is decorative.
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = {
                            CompositionLocalProvider(LocalDensity provides labelDensity) {
                                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        modifier = Modifier.testTag("nav_${destination.name}"),
                    )
                }
            }
        },
    ) { padding ->
        MediShelfNavHost(
            navController = navController,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}

private fun NavDestination?.isInHierarchy(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(
        destination.route,
        navOptions {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        },
    )
}

private const val MAX_NAV_LABEL_SCALE = 1.3f
