package com.medishelf.feature.family.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.medishelf.feature.family.edit.FamilyMemberEditScreen
import com.medishelf.feature.family.list.FamilyListScreen
import com.medishelf.feature.family.member.FamilyMemberScreen
import kotlinx.serialization.Serializable

@Serializable
data object FamilyRoute

@Serializable
data class FamilyMemberRoute(val id: Long)

/** [id] 0 = new member. */
@Serializable
data class FamilyMemberEditRoute(val id: Long = 0)

fun NavController.navigateToFamily(navOptions: NavOptions? = null) = navigate(FamilyRoute, navOptions)
fun NavController.navigateToFamilyMember(id: Long) = navigate(FamilyMemberRoute(id))
fun NavController.navigateToFamilyMemberEdit(id: Long = 0) = navigate(FamilyMemberEditRoute(id))

fun NavGraphBuilder.familyScreens(
    onNavigateUp: () -> Unit,
    onMemberClick: (Long) -> Unit,
    onAddMember: () -> Unit,
    onEditMember: (Long) -> Unit,
    onMedicineClick: (Long) -> Unit,
    onMemberSaved: (Long, isNew: Boolean) -> Unit,
    onMemberDeleted: () -> Unit,
) {
    composable<FamilyRoute> { FamilyListScreen(onMemberClick = onMemberClick, onAddMember = onAddMember) }
    composable<FamilyMemberRoute> {
        FamilyMemberScreen(onNavigateUp = onNavigateUp, onEdit = onEditMember, onMedicineClick = onMedicineClick)
    }
    composable<FamilyMemberEditRoute> {
        FamilyMemberEditScreen(onNavigateUp = onNavigateUp, onSaved = onMemberSaved, onDeleted = onMemberDeleted)
    }
}
