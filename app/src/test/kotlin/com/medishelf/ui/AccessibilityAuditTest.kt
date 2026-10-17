package com.medishelf.ui

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.medishelf.HiltTestActivity
import com.medishelf.core.designsystem.theme.MediShelfTheme
import com.medishelf.core.domain.repository.AssignmentInput
import com.medishelf.core.testing.FakeFamilyMemberRepository
import com.medishelf.core.testing.FakeMedicineRepository
import com.medishelf.core.testing.FakeStorageLocationRepository
import com.medishelf.core.testing.testLocation
import com.medishelf.core.testing.testMedicine
import com.medishelf.core.testing.testMember
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import javax.inject.Inject

/**
 * Walks every main screen with realistic data and checks clickables for labels and 48dp
 * touch targets. (Full ATF checks incl. contrast run on a device: see androidTest.)
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AccessibilityAuditTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var medicines: FakeMedicineRepository
    @Inject lateinit var members: FakeFamilyMemberRepository
    @Inject lateinit var locations: FakeStorageLocationRepository

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking {
            val sara = members.save(testMember(name = "Sara"))
            val fridge = locations.save(testLocation(name = "Fridge door"))
            medicines.put(testMedicine(brandName = "Old syrup", expiryDate = LocalDate.of(2025, 12, 1), storageLocationId = fridge), listOf(AssignmentInput(sara, "Cough")))
            medicines.put(testMedicine(brandName = "Eye drops", expiryDate = LocalDate.of(2026, 1, 20), quantity = 1.0, lowStockThreshold = 2.0))
        }
    }

    private fun launchApp() = compose.setContent { MediShelfTheme { MediShelfApp() } }

    /** Audits the current screen, then each further viewport while scrolling down. */
    private fun auditScrolling(scrolls: Int = 5) {
        audit()
        repeat(scrolls) {
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.3f) }
            compose.waitForIdle()
            audit()
        }
    }

    private var step = 0
    private fun audit() {
        // Let enter animations (FAB text, screen transitions) finish so we audit the settled UI.
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.assertClickablesAccessible("step ${++step}", compose.density)
    }

    @Test
    fun topLevelScreens() {
        launchApp()
        auditScrolling(2) // Home
        listOf("nav_MEDICINES", "nav_WHERE", "nav_FAMILY", "nav_SETTINGS").forEach {
            compose.onNodeWithTag(it).performClick()
            auditScrolling(2)
        }
    }

    @Test
    fun detailEditorAndPersonScreens() {
        launchApp()
        compose.onNodeWithTag("nav_MEDICINES").performClick()
        compose.onNodeWithText("Old syrup").performClick()
        auditScrolling(4) // detail
        compose.onRoot().performTouchInput { swipeDown(startY = bottom * 0.3f, endY = bottom * 0.9f) }
        compose.onRoot().performTouchInput { swipeDown(startY = bottom * 0.3f, endY = bottom * 0.9f) }
        compose.onNodeWithContentDescription("Edit medicine").performClick()
        auditScrolling(10) // editor
        compose.onNodeWithContentDescription("Navigate up").performClick()
        compose.onNodeWithContentDescription("Navigate up").performClick()
        compose.onNodeWithTag("nav_FAMILY").performClick()
        compose.onNodeWithText("Sara").performClick()
        audit() // per-person
        compose.onNodeWithContentDescription("Navigate up").performClick()
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onAllNodesWithText("App lock").onFirst().performClick()
        audit()
    }

    @Test
    fun disclaimerScreen() {
        compose.setContent { MediShelfTheme { com.medishelf.feature.onboarding.DisclaimerScreen() } }
        auditScrolling(3)
    }

    @Test
    fun lockScreen() {
        compose.setContent { MediShelfTheme { com.medishelf.feature.lock.unlock.LockScreen() } }
        auditScrolling(2)
    }
}
