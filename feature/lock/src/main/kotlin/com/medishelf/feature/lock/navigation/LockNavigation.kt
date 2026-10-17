package com.medishelf.feature.lock.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.medishelf.feature.lock.settings.AppLockSettingsScreen
import com.medishelf.feature.lock.setup.PinFlowScreen
import kotlinx.serialization.Serializable

/** SET: create a PIN and turn the lock on. CHANGE / DISABLE: require the current PIN first. */
enum class PinFlow { SET, CHANGE, DISABLE }

@Serializable
data object AppLockSettingsRoute

@Serializable
data class PinFlowRoute(val flow: PinFlow)

fun NavController.navigateToAppLockSettings() = navigate(AppLockSettingsRoute)
fun NavController.navigateToPinFlow(flow: PinFlow) = navigate(PinFlowRoute(flow))

fun NavGraphBuilder.lockScreens(onNavigateUp: () -> Unit, onPinFlow: (PinFlow) -> Unit) {
    composable<AppLockSettingsRoute> { AppLockSettingsScreen(onNavigateUp = onNavigateUp, onPinFlow = onPinFlow) }
    composable<PinFlowRoute> { PinFlowScreen(onNavigateUp = onNavigateUp, onDone = onNavigateUp) }
}
