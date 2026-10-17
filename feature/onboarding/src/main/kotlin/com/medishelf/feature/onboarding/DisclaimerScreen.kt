package com.medishelf.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medishelf.core.domain.repository.SettingsRepository
import com.medishelf.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisclaimerViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun onAccept() = viewModelScope.launch { settings.acceptDisclaimer(UserSettings.CURRENT_DISCLAIMER_VERSION) }
}

/** First-launch gate. The app stays behind this until the user explicitly acknowledges it. */
@Composable
fun DisclaimerScreen(viewModel: DisclaimerViewModel = hiltViewModel()) {
    DisclaimerContent(onAccept = viewModel::onAccept)
}

@Composable
internal fun DisclaimerContent(onAccept: () -> Unit) {
    var understood by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.feature_onboarding_welcome),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.feature_onboarding_intro), style = MaterialTheme.typography.bodyLarge)
            DisclaimerText()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it })
                    .testTag("disclaimer_checkbox"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = understood, onCheckedChange = null)
                Text(stringResource(R.string.feature_onboarding_understand), modifier = Modifier.padding(start = 12.dp))
            }
            Button(
                onClick = onAccept,
                enabled = understood,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("disclaimer_continue"),
            ) { Text(stringResource(R.string.feature_onboarding_continue)) }
        }
    }
}

/** The disclaimer body, also shown from Settings > About. */
@Composable
fun DisclaimerText(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.feature_onboarding_disclaimer_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        listOf(
            R.string.feature_onboarding_point_not_advice,
            R.string.feature_onboarding_point_follow_professionals,
            R.string.feature_onboarding_point_check_labels,
            R.string.feature_onboarding_point_emergency,
            R.string.feature_onboarding_point_private,
        ).forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    }
}
