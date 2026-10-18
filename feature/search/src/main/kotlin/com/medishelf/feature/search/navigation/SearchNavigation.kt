package com.medishelf.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.medishelf.feature.search.SearchScreen
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute

fun NavController.navigateToSearch() = navigate(SearchRoute) { launchSingleTop = true }

fun NavGraphBuilder.searchScreen(
    onNavigateUp: () -> Unit,
    onMedicineClick: (Long) -> Unit,
    onMemberClick: (Long) -> Unit,
    onLocationClick: (Long) -> Unit,
) {
    composable<SearchRoute> {
        SearchScreen(
            onNavigateUp = onNavigateUp,
            onMedicineClick = onMedicineClick,
            onMemberClick = onMemberClick,
            onLocationClick = onLocationClick,
        )
    }
}
