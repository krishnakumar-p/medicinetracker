package com.medishelf.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.medishelf.HiltTestActivity
import com.medishelf.core.designsystem.theme.MediShelfTheme
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.testMedicine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MediShelfAppNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var medicines: FakeMedicineRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun launch() = composeRule.setContent { MediShelfTheme { MediShelfApp() } }

    @Test
    fun startsOnHome() {
        launch()
        composeRule.onNodeWithTag("nav_HOME").assertIsSelected()
    }

    @Test
    fun bottomBarNavigatesBetweenTopLevelDestinations() {
        launch()
        composeRule.onNodeWithTag("nav_MEDICINES").performClick()
        composeRule.onNodeWithTag("nav_MEDICINES").assertIsSelected()

        composeRule.onNodeWithTag("nav_WHERE").performClick()
        composeRule.onNodeWithText("Where is it?").assertExists()

        composeRule.onNodeWithTag("nav_FAMILY").performClick()
        composeRule.onNodeWithText("No family members yet").assertExists()
    }

    @Test
    fun addMedicine_opensEditor_withoutBottomBar_andBackReturnsHome() {
        launch()
        composeRule.onAllNodesWithText("Add medicine")[0].performClick()

        composeRule.onNodeWithTag("save").assertExists()
        composeRule.onNodeWithTag("nav_HOME").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        composeRule.onNodeWithTag("nav_HOME").assertIsSelected()
    }

    @Test
    fun dashboardTile_opensFilteredList_thenDetail() {
        medicines.put(testMedicine(brandName = "Old syrup", expiryDate = LocalDate.of(2025, 12, 1)))
        medicines.put(testMedicine(brandName = "Fresh", expiryDate = LocalDate.of(2027, 12, 1)))
        launch()

        composeRule.onNodeWithTag("tile_expired").performClick()
        composeRule.onNodeWithText("Old syrup").assertExists()
        composeRule.onNodeWithText("Fresh").assertDoesNotExist()

        composeRule.onNodeWithText("Old syrup").performClick()
        composeRule.onNodeWithText("Dispose of medicines safely").assertExists()
    }
}
