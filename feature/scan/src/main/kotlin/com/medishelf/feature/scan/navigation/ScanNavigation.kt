package com.medishelf.feature.scan.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.medishelf.core.model.ScanSuggestion
import com.medishelf.feature.scan.ScanScreen
import kotlinx.serialization.Serializable

enum class ScanMode { BARCODE, LABEL }

@Serializable
data class ScanRoute(val mode: ScanMode)

fun NavController.navigateToScan(mode: ScanMode) = navigate(ScanRoute(mode))

fun NavGraphBuilder.scanScreen(onNavigateUp: () -> Unit, onResult: (ScanSuggestion) -> Unit) {
    composable<ScanRoute> { ScanScreen(onNavigateUp = onNavigateUp, onResult = onResult) }
}
