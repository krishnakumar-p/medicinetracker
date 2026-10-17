package com.medishelf.feature.onboarding

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.designsystem.theme.MediShelfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DisclaimerTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun continueRequiresExplicitAcknowledgement() {
        var accepted = false
        compose.setContent { MediShelfTheme { DisclaimerContent(onAccept = { accepted = true }) } }

        compose.onNodeWithTag("disclaimer_continue").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("disclaimer_checkbox").performScrollTo().performClick()
        compose.onNodeWithTag("disclaimer_continue").performScrollTo().assertIsEnabled().performClick()

        assertThat(accepted).isTrue()
    }
}
