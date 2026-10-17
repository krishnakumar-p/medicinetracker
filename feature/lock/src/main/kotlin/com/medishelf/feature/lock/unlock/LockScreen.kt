package com.medishelf.feature.lock.unlock

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.security.Biometrics
import com.medishelf.core.security.DeviceCredential
import com.medishelf.core.security.findFragmentActivity
import com.medishelf.feature.lock.R
import com.medishelf.feature.lock.components.PinDots
import com.medishelf.feature.lock.components.PinPad

/** Full-screen lock shown above the whole app while it is locked. */
@Composable
fun LockScreen(viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canUseBiometric = state.biometricEnabled && Biometrics.isAvailable(context)
    val promptTitle = stringResource(R.string.feature_lock_biometric_title)
    val usePin = stringResource(R.string.feature_lock_use_pin)
    val showPrompt = {
        context.findFragmentActivity()?.let { activity ->
            Biometrics.prompt(activity, promptTitle, usePin, onSuccess = viewModel::onBiometricSuccess, onFallback = {})
        }
    }
    // Offer biometrics once automatically; afterwards only on request.
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric && !autoPrompted) {
            autoPrompted = true
            showPrompt()
        }
    }
    val recoverTitle = stringResource(R.string.feature_lock_recover_title)
    val recoverBody = stringResource(R.string.feature_lock_recover_body)
    var showNoScreenLock by rememberSaveable { mutableStateOf(false) }
    val confirmCredential = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.onForgotPinVerified()
    }
    val onForgotPin: () -> Unit = {
        val activity = context.findFragmentActivity()
        when {
            !DeviceCredential.isDeviceSecure(context) -> showNoScreenLock = true
            DeviceCredential.supportsPrompt && activity != null ->
                DeviceCredential.prompt(activity, recoverTitle, onSuccess = { viewModel.onForgotPinVerified() })
            else -> DeviceCredential.confirmCredentialIntent(context, recoverTitle, recoverBody)?.let(confirmCredential::launch)
        }
    }
    if (showNoScreenLock) {
        AlertDialog(
            onDismissRequest = { showNoScreenLock = false },
            title = { Text(stringResource(R.string.feature_lock_no_screen_lock_title)) },
            text = { Text(stringResource(R.string.feature_lock_no_screen_lock_body)) },
            confirmButton = { TextButton(onClick = { showNoScreenLock = false }) { Text(stringResource(com.medishelf.core.ui.R.string.core_ui_ok)) } },
        )
    }
    LockContent(
        state = state,
        canUseBiometric = canUseBiometric,
        onForgotPin = onForgotPin,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onSubmit = viewModel::onSubmit,
        onBiometric = { showPrompt() },
    )
}

@Composable
internal fun LockContent(
    state: LockUiState,
    canUseBiometric: Boolean,
    onForgotPin: () -> Unit = {},
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onBiometric: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.feature_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.feature_lock_enter_pin), style = MaterialTheme.typography.bodyLarge)
            PinDots(state.pinLength)
            val message = when {
                state.lockedOutSeconds > 0 -> pluralStringResource(
                    R.plurals.feature_lock_locked_out,
                    state.lockedOutSeconds.toInt(),
                    state.lockedOutSeconds.toInt(),
                )
                state.attemptsLeft != null -> pluralStringResource(
                    R.plurals.feature_lock_wrong_pin,
                    state.attemptsLeft,
                    state.attemptsLeft,
                )
                else -> ""
            }
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            PinPad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                onSubmit = onSubmit,
                submitEnabled = state.canSubmit,
                enabled = state.lockedOutSeconds == 0L && !state.verifying,
            )
            if (canUseBiometric) {
                TextButton(onClick = onBiometric) { Text(stringResource(R.string.feature_lock_use_biometric)) }
            }
            TextButton(onClick = onForgotPin) { Text(stringResource(R.string.feature_lock_forgot)) }
        }
    }
}
