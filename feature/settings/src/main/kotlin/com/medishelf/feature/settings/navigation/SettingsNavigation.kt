package com.medishelf.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.medishelf.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsRoute, navOptions)

/** [showDeveloperOptions] is BuildConfig.DEBUG from the app: demo data never ships in release UI. */
fun NavGraphBuilder.settingsScreen(
    onOpenAppLock: () -> Unit,
    showDeveloperOptions: Boolean,
    versionName: String,
    onNavigateUp: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onOpenAppLock = onOpenAppLock,
            showDeveloperOptions = showDeveloperOptions,
            versionName = versionName,
            onNavigateUp = onNavigateUp,
        )
    }
}
