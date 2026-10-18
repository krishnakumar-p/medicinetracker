package com.medishelf.feature.search

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.medishelf.core.designsystem.theme.MediShelfTheme
import com.medishelf.core.domain.repository.AssignmentInput
import com.medishelf.core.domain.usecase.ObserveGlobalSearchUseCase
import com.medishelf.core.testing.FakeDoseRepository
import com.medishelf.core.testing.FakeFamilyMemberRepository
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.FakeStorageLocationRepository
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.core.testing.testMedicine
import com.medishelf.core.testing.testMember
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchTest {
    @get:Rule val compose = createComposeRule()

    private val time = TestTimeProvider()
    private val members = FakeFamilyMemberRepository()
    private val locations = FakeStorageLocationRepository()
    private val medicines = FakeMedicineRepository(members, locations)

    private fun viewModel() = SearchViewModel(
        SavedStateHandle(),
        ObserveGlobalSearchUseCase(medicines, members, locations, FakeDoseRepository(medicines), time),
        time,
    )

    @Test
    fun typingDoctorName_findsMedicine_andSaysWhereItMatched() {
        val sara = runBlocking { members.save(testMember(name = "Sara")) }
        medicines.put(
            testMedicine(brandName = "Augmentin", genericName = "Amoxicillin").copy(
                prescribedBy = "Dr. Hana Saleh", prescribedForMemberId = sara, prescribedForReason = "Ear infection",
            ),
            listOf(AssignmentInput(sara, "Ear infection")),
        )
        medicines.put(testMedicine(brandName = "Panadol"))
        compose.setContent { MediShelfTheme { SearchScreen({}, {}, {}, {}, viewModel = viewModel()) } }

        compose.onNodeWithTag("search_field").performTextInput("hana")
        compose.waitForIdle()

        compose.onNodeWithText("Augmentin", substring = true).assertExists()
        compose.onNodeWithText("Doctor: Dr. Hana Saleh", substring = true).assertExists()
        compose.onNodeWithText("Panadol", substring = true).assertDoesNotExist()
    }

    @Test
    fun noMatch_saysSo_andPeopleAreSearchable() {
        runBlocking { members.save(testMember(name = "Omar")) }
        compose.setContent { MediShelfTheme { SearchScreen({}, {}, {}, {}, viewModel = viewModel()) } }

        compose.onNodeWithTag("search_field").performTextInput("omar")
        compose.waitForIdle()
        compose.onNodeWithText("People").assertExists()

        compose.onNodeWithTag("search_field").performTextInput("zzz")
        compose.waitForIdle()
        compose.onNodeWithText("Nothing matches", substring = true).assertExists()
    }
}
