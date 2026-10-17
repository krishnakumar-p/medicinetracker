package com.medishelf

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Google's Accessibility Test Framework on a real device/emulator (contrast, touch targets,
 * labels, duplicate descriptions). Expects a fresh install of the debug build:
 *   adb shell pm clear com.medishelf.debug && ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun audit() {
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun everyMainScreenPassesAtf() {
        compose.enableAccessibilityChecks()
        waitForText("Welcome to MediShelf")
        audit()
        tickDisclaimer()
        compose.onNodeWithTag("disclaimer_continue").performClick()

        waitForText("Add medicine")
        compose.onNodeWithTag("nav_SETTINGS").performClick()
        compose.onNodeWithText("Demo data").performScrollTo().performClick()
        compose.onNodeWithTag("nav_MEDICINES").performClick()
        waitForText("Augmentin")

        listOf("nav_HOME", "nav_MEDICINES", "nav_WHERE", "nav_FAMILY", "nav_SETTINGS").forEach {
            compose.onNodeWithTag(it).performClick()
            audit()
        }
        compose.onNodeWithTag("nav_MEDICINES").performClick()
        compose.onAllNodesWithText("Augmentin", substring = true).onFirst().performClick()
        audit()
        compose.onNodeWithContentDescription("Edit medicine").performClick()
        audit()
        compose.onNodeWithContentDescription("Navigate up").performClick()
        compose.onNodeWithContentDescription("Navigate up").performClick()
        compose.onNodeWithTag("nav_FAMILY").performClick()
        compose.onNodeWithText("Sara").performClick()
        audit()
    }

    /** Scrolls the disclaimer so the checkbox is on screen, then ticks it. */
    private fun tickDisclaimer() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("I understand", substring = true))
        compose.onAllNodesWithText("I understand", substring = true).onFirst().performClick()
    }
}
