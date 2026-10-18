package com.medishelf.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.medishelf.HiltTestActivity
import com.medishelf.core.designsystem.theme.MediShelfTheme
import com.medishelf.core.model.ScanSuggestion
import com.medishelf.feature.scan.navigation.ScanMode
import com.medishelf.feature.scan.navigation.navigateToScan
import com.medishelf.navigation.deliverScanResult
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

/**
 * The real hand-off: scanner -> navigation back stack -> editor ViewModel (Hilt), as on a phone.
 * Unit tests of the ViewModel alone can't catch a result left in the wrong SavedStateHandle.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ScanToEditorTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var nav: NavHostController

    private val scan = ScanSuggestion(
        name = "Augmentin",
        genericName = "Amoxicillin / clavulanic acid",
        strength = "625 mg",
        manufacturer = "GSK",
        batchNumber = "B12345",
        expiryDate = LocalDate.of(2027, 5, 31),
        prescribedBy = "Dr. Hana Saleh",
        condition = "Ear infection",
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        compose.setContent {
            nav = rememberNavController()
            MediShelfTheme { MediShelfApp(navController = nav) }
        }
    }

    private fun assertEditorFilled() {
        compose.waitForIdle()
        compose.onNodeWithTag("field_brand").performScrollTo().assertTextContains("Augmentin")
        compose.onNodeWithTag("field_batch").performScrollTo().assertTextContains("B12345")
        compose.onNodeWithTag("field_prescribed_for_reason").performScrollTo().assertTextContains("Ear infection")
    }

    @Test
    fun scanStartedFromEditor_fillsThatEditor() {
        compose.onAllNodesWithText("Add medicine")[0].performClick()
        compose.waitForIdle()
        compose.runOnUiThread { nav.navigateToScan(ScanMode.PACK) }
        compose.waitForIdle()
        compose.runOnUiThread { nav.deliverScanResult(scan) }
        assertEditorFilled()
    }

    @Test
    fun scanStartedFromHome_opensFilledEditor() {
        compose.runOnUiThread { nav.navigateToScan(ScanMode.PACK) }
        compose.waitForIdle()
        compose.runOnUiThread { nav.deliverScanResult(scan) }
        assertEditorFilled()
    }
}
