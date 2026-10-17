package com.medishelf.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.medishelf.core.designsystem.theme.MediShelfThemeExt
import com.medishelf.core.model.Medicine
import com.medishelf.core.model.MedicineStatus
import java.time.LocalDate
import kotlin.math.abs

/** Days within which an active medicine is flagged as "expiring". */
const val EXPIRING_SOON_DAYS = 30L

/**
 * Expiry/status pill. Always shows an icon and text, so status never relies on colour alone.
 */
@Composable
fun ExpiryStatusChip(medicine: Medicine, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = MediShelfThemeExt.statusColors
    val days = medicine.daysUntilExpiry(today)
    val (icon, container, content, text) = when (medicine.effectiveStatus(today)) {
        MedicineStatus.EXPIRED -> ChipStyle(
            Icons.Filled.Warning,
            colors.expiredContainer,
            colors.onExpiredContainer,
            if (days < 0) {
                pluralStringResource(R.plurals.core_ui_expired_days_ago, abs(days).toInt(), abs(days).toInt())
            } else {
                stringResource(R.string.core_ui_status_expired)
            },
        )
        MedicineStatus.FINISHED -> ChipStyle(
            Icons.Filled.Done,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.core_ui_status_finished),
        )
        MedicineStatus.DISPOSED -> ChipStyle(
            Icons.Filled.Delete,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.core_ui_status_disposed),
        )
        MedicineStatus.ACTIVE -> when {
            days == 0L -> ChipStyle(
                Icons.Filled.DateRange,
                colors.expiringContainer,
                colors.onExpiringContainer,
                stringResource(R.string.core_ui_expires_today),
            )
            days <= EXPIRING_SOON_DAYS -> ChipStyle(
                Icons.Filled.DateRange,
                colors.expiringContainer,
                colors.onExpiringContainer,
                pluralStringResource(R.plurals.core_ui_expires_in_days, days.toInt(), days.toInt()),
            )
            else -> ChipStyle(
                Icons.Filled.CheckCircle,
                colors.okContainer,
                colors.onOkContainer,
                stringResource(R.string.core_ui_expires_on, medicine.effectiveExpiryDate.formatMedium()),
            )
        }
    }
    StatusPill(icon, container, content, text, modifier)
}

@Composable
fun LowStockChip(modifier: Modifier = Modifier) {
    val colors = MediShelfThemeExt.statusColors
    StatusPill(
        Icons.Filled.ShoppingCart,
        colors.lowStockContainer,
        colors.onLowStockContainer,
        stringResource(R.string.core_ui_low_stock),
        modifier,
    )
}

@Composable
private fun StatusPill(icon: ImageVector, container: Color, content: Color, text: String, modifier: Modifier) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private data class ChipStyle(val icon: ImageVector, val container: Color, val content: Color, val text: String)
