package com.medishelf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medishelf.AppGate
import com.medishelf.MainUiState
import com.medishelf.R
import com.medishelf.core.ui.ConfirmDialog
import com.medishelf.core.ui.LoadingState
import com.medishelf.feature.lock.unlock.LockScreen
import com.medishelf.feature.onboarding.DisclaimerScreen

/**
 * Top-level gate. The app's NavHost is only composed when READY, so nothing behind the
 * disclaimer or lock is ever rendered or reachable by deep link.
 */
@Composable
fun MediShelfRoot(state: MainUiState, onStartFresh: () -> Unit, onOpenListHandled: () -> Unit = {}) {
    when (state.gate) {
        AppGate.LOADING -> Surface(Modifier.fillMaxSize()) { LoadingState() }
        AppGate.DATABASE_UNAVAILABLE -> DatabaseRecoveryScreen(onStartFresh)
        AppGate.DISCLAIMER -> DisclaimerScreen()
        AppGate.LOCKED -> LockScreen()
        AppGate.READY -> MediShelfApp(openList = state.openList, onOpenListHandled = onOpenListHandled)
    }
}

@Composable
private fun DatabaseRecoveryScreen(onStartFresh: () -> Unit) {
    // Two confirmations: this permanently deletes records that can't be read anyway.
    var confirmStep by rememberSaveable { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.recovery_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.recovery_body), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.recovery_restore_hint), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { confirmStep = 1 }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recovery_start_fresh))
            }
        }
    }
    when (confirmStep) {
        1 -> ConfirmDialog(
            title = stringResource(R.string.recovery_confirm_title),
            text = stringResource(R.string.recovery_confirm_body),
            confirmLabel = stringResource(R.string.recovery_continue),
            onConfirm = { confirmStep = 2 },
            onDismiss = { confirmStep = 0 },
        )
        2 -> ConfirmDialog(
            title = stringResource(R.string.recovery_final_title),
            text = stringResource(R.string.recovery_final_body),
            confirmLabel = stringResource(R.string.recovery_delete),
            onConfirm = {
                confirmStep = 0
                onStartFresh()
            },
            onDismiss = { confirmStep = 0 },
        )
    }
}
