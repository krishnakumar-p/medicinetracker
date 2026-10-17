package com.medishelf.feature.dashboard

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.designsystem.theme.MediShelfTheme
import com.medishelf.core.domain.usecase.ObserveDashboardCountsUseCase
import com.medishelf.core.domain.usecase.ObserveMedicinesUseCase
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.core.testing.testMedicine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardTest {
    @get:Rule val compose = createComposeRule()
    private val time = TestTimeProvider()
    private val medicines = FakeMedicineRepository()
    private fun viewModel() = DashboardViewModel(
        ObserveDashboardCountsUseCase(medicines, time),
        ObserveMedicinesUseCase(medicines, time),
        com.medishelf.core.testing.FakeSettingsRepository(),
        time,
    )

    @Test
    fun showsCounts_attentionList_andTileNavigation() {
        medicines.put(testMedicine(brandName = "Old syrup", expiryDate = time.today.minusDays(2)))
        medicines.put(testMedicine(brandName = "Eye drops", expiryDate = time.today.plusDays(5), quantity = 1.0, lowStockThreshold = 2.0))
        medicines.put(testMedicine(brandName = "Panadol", expiryDate = time.today.plusYears(1)))
        medicines.put(testMedicine(brandName = "Gone", status = MedicineStatus.DISPOSED))
        var tapped: DashboardTile? = null

        compose.setContent {
            MediShelfTheme {
                DashboardScreen(onTileClick = { tapped = it }, onAddMedicine = {}, onMedicineClick = {}, viewModel = viewModel())
            }
        }

        compose.onNodeWithContentDescription("1 Expired").assertExists()
        compose.onNodeWithContentDescription("1 Expiring in 30 days").assertExists()
        compose.onNodeWithContentDescription("1 Low stock").assertExists()
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Old syrup"))
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Eye drops"))
        compose.onNodeWithText("Panadol").assertDoesNotExist()

        compose.onNodeWithTag("dashboard_list").performScrollToIndex(0)
        compose.onNodeWithTag("tile_low_stock").performClick()
        assertThat(tapped).isEqualTo(DashboardTile.LOW_STOCK)
    }

    @Test
    fun emptyInventory_showsWelcome() {
        compose.setContent {
            MediShelfTheme { DashboardScreen(onTileClick = {}, onAddMedicine = {}, onMedicineClick = {}, viewModel = viewModel()) }
        }
        compose.onNodeWithTag("dashboard_list").performScrollToNode(hasText("Start by adding", substring = true))
    }
}
