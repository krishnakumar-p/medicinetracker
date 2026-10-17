package com.medishelf.feature.medicines

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.medishelf.core.designsystem.theme.MediShelfTheme
import com.medishelf.core.testing.testMedicine
import com.medishelf.feature.medicines.edit.MedicineEditScreen
import com.medishelf.feature.medicines.list.MedicineListScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MedicineScreensTest {
    @get:Rule val compose = createComposeRule()
    private val f = MedicineTestFixture()

    @Test
    fun list_searchFiltersVisibleMedicines() {
        f.medicines.put(testMedicine(brandName = "Panadol", genericName = "Paracetamol"))
        f.medicines.put(testMedicine(brandName = "Ventolin", genericName = "Salbutamol"))
        val vm = f.listViewModel()
        compose.setContent { MediShelfTheme { MedicineListScreen(onMedicineClick = {}, onAddMedicine = {}, viewModel = vm) } }

        compose.onNodeWithText("Panadol").assertExists()
        compose.onNodeWithTag("medicine_search").performTextInput("salbu")

        compose.onNodeWithText("Ventolin").assertExists()
        compose.onAllNodesWithText("Panadol").assertCountEquals(0)
    }

    @Test
    fun list_empty_showsCallToAction() {
        var added = false
        compose.setContent {
            MediShelfTheme { MedicineListScreen(onMedicineClick = {}, onAddMedicine = { added = true }, viewModel = f.listViewModel()) }
        }
        compose.onNodeWithText("No medicines here").assertExists()
        compose.onAllNodesWithText("Add medicine").onFirst().performClick()
        assert(added)
    }

    @Test
    fun edit_saveWithoutExpiry_showsFieldError_andDoesNotNavigate() {
        var savedId: Long? = null
        compose.setContent {
            MediShelfTheme {
                MedicineEditScreen(onNavigateUp = {}, onSaved = { id, _ -> savedId = id }, viewModel = f.editViewModel())
            }
        }
        compose.onNodeWithTag("field_brand").performTextInput("Panadol")
        compose.onNodeWithTag("field_quantity").performTextInput("10")
        compose.onNodeWithTag("save").performClick()

        compose.onNodeWithText("Please fix the highlighted fields.").assertExists()
        compose.onNodeWithText("Enter the expiry date").performScrollTo().assertExists()
        assert(savedId == null)
    }
}
