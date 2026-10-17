package com.medishelf.feature.lock.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.medishelf.core.domain.security.PinRules
import com.medishelf.feature.lock.R

/** Entered-digit dots; announces the count, never the digits. */
@Composable
internal fun PinDots(length: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.feature_lock_digits_entered, length)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(maxOf(length, PinRules.MIN_LENGTH)) { index ->
            Surface(
                shape = CircleShape,
                color = if (index < length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(16.dp),
            ) {}
        }
    }
}

/** Phone-style keypad with large (72dp) targets for older users. */
@Composable
internal fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled, onDigit) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_lock_delete_digit))
            }
            DigitKey('0', enabled, onDigit)
            FilledTonalIconButton(
                onClick = onSubmit,
                enabled = enabled && submitEnabled,
                modifier = Modifier.size(72.dp).testTag("pin_submit"),
            ) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.feature_lock_submit))
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onDigit: (Char) -> Unit) {
    OutlinedButton(
        onClick = { onDigit(digit) },
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(72.dp).testTag("pin_$digit"),
    ) { Text(digit.toString(), style = MaterialTheme.typography.headlineSmall) }
}

