package com.medishelf.feature.lock.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.domain.security.PinProblem
import com.medishelf.core.domain.security.PinRules
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.feature.lock.R
import com.medishelf.feature.lock.components.PinDots
import com.medishelf.feature.lock.components.PinPad
import com.medishelf.feature.lock.navigation.PinFlow

@Composable
internal fun PinFlowScreen(
    onNavigateUp: () -> Unit,
    onDone: () -> Unit,
    viewModel: PinFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.step) { if (state.step == PinStep.DONE) onDone() }
    Scaffold(
        topBar = {
            MediShelfTopBar(
                title = stringResource(
                    when (state.flow) {
                        PinFlow.SET -> R.string.feature_lock_set_title
                        PinFlow.CHANGE -> R.string.feature_lock_change_title
                        PinFlow.DISABLE -> R.string.feature_lock_disable_title
                    },
                ),
                onNavigateUp = onNavigateUp,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(
                    when (state.step) {
                        PinStep.CURRENT -> R.string.feature_lock_step_current
                        PinStep.NEW -> R.string.feature_lock_step_new
                        PinStep.CONFIRM -> R.string.feature_lock_step_confirm
                        PinStep.DONE -> R.string.feature_lock_step_new
                    },
                    PinRules.MIN_LENGTH,
                    PinRules.MAX_LENGTH,
                ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            PinDots(state.pinLength)
            Text(
                text = state.error?.let { errorText(it) }.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
            )
            PinPad(
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                onSubmit = viewModel::onSubmit,
                submitEnabled = state.pinLength >= PinRules.MIN_LENGTH && !state.busy,
                enabled = !state.busy && state.error !is PinFlowError.LockedOut,
            )
        }
    }
}

@Composable
private fun errorText(error: PinFlowError): String = when (error) {
    is PinFlowError.Invalid -> stringResource(
        when (error.problem) {
            PinProblem.TOO_SHORT, PinProblem.TOO_LONG, PinProblem.NOT_DIGITS -> R.string.feature_lock_error_length
            PinProblem.TOO_SIMPLE -> R.string.feature_lock_error_simple
            PinProblem.MISMATCH -> R.string.feature_lock_error_mismatch
        },
        PinRules.MIN_LENGTH,
        PinRules.MAX_LENGTH,
    )
    is PinFlowError.WrongCurrent -> pluralStringResource(R.plurals.feature_lock_wrong_pin, error.attemptsLeft, error.attemptsLeft)
    is PinFlowError.LockedOut -> stringResource(R.string.feature_lock_try_later)
}
