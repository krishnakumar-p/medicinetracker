package com.medishelf.feature.lock.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.medishelf.core.domain.repository.AppLockCredentials
import com.medishelf.core.domain.repository.SettingsRepository
import com.medishelf.core.model.AppLockSettings
import com.medishelf.core.security.Biometrics
import com.medishelf.core.ui.DropdownField
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.feature.lock.R
import com.medishelf.feature.lock.navigation.PinFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockSettingsUiState(
    val isLoading: Boolean = true,
    val active: Boolean = false,
    val settings: AppLockSettings = AppLockSettings(),
)

@HiltViewModel
class AppLockSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    credentials: AppLockCredentials,
) : ViewModel() {
    val uiState: StateFlow<AppLockSettingsUiState> = combine(settings.settings, credentials.hasPin) { s, hasPin ->
        AppLockSettingsUiState(isLoading = false, active = s.appLock.enabled && hasPin, settings = s.appLock)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockSettingsUiState())

    fun onBiometricChange(enabled: Boolean) = viewModelScope.launch {
        settings.updateAppLock { it.copy(biometricEnabled = enabled) }
    }

    fun onTimeoutChange(seconds: Int) = viewModelScope.launch {
        settings.updateAppLock { it.copy(autoLockAfterSeconds = seconds) }
    }
}

@Composable
internal fun AppLockSettingsScreen(
    onNavigateUp: () -> Unit,
    onPinFlow: (PinFlow) -> Unit,
    viewModel: AppLockSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val biometricAvailable = Biometrics.isAvailable(LocalContext.current)
    Scaffold(topBar = { MediShelfTopBar(stringResource(R.string.feature_lock_settings_title), onNavigateUp) }) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.feature_lock_settings_intro), style = MaterialTheme.typography.bodyLarge)
            if (!state.active) {
                Button(onClick = { onPinFlow(PinFlow.SET) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feature_lock_turn_on))
                }
                return@Column
            }
            if (biometricAvailable) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = state.settings.biometricEnabled,
                        role = Role.Switch,
                        onValueChange = viewModel::onBiometricChange,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.feature_lock_biometric_setting))
                        Text(
                            stringResource(R.string.feature_lock_biometric_setting_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.settings.biometricEnabled, onCheckedChange = null)
                }
            }
            DropdownField(
                label = stringResource(R.string.feature_lock_timeout),
                options = AppLockSettings.AUTO_LOCK_CHOICES,
                selected = state.settings.autoLockAfterSeconds,
                optionLabel = { seconds ->
                    if (seconds < 60) {
                        pluralStringResource(R.plurals.feature_lock_after_seconds, seconds, seconds)
                    } else {
                        pluralStringResource(R.plurals.feature_lock_after_minutes, seconds / 60, seconds / 60)
                    }
                },
                onSelect = viewModel::onTimeoutChange,
            )
            OutlinedButton(onClick = { onPinFlow(PinFlow.CHANGE) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_lock_change_title))
            }
            OutlinedButton(onClick = { onPinFlow(PinFlow.DISABLE) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_lock_disable_title))
            }
        }
    }
}
