package com.medishelf.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medishelf.core.model.MedicineDetails
import java.time.LocalDate

/**
 * Inventory row used by the list, dashboard, per-person and location screens.
 * [subtitle] overrides the default "people · location" line (e.g. to show a condition).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicineListItem(
    details: MedicineDetails,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val medicine = details.medicine
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Button },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = medicine.titleWithStrength,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val secondary = listOf(medicine.genericName, medicine.form.label(), medicine.quantityLabel())
                .filter(String::isNotBlank)
                .joinToString(" · ")
            Text(secondary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val context = subtitle ?: listOfNotNull(
                details.assignments.joinToString(", ") { it.member.name }.takeIf(String::isNotEmpty),
                details.location?.name,
            ).joinToString(" · ")
            if (context.isNotEmpty()) {
                Text(context, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ExpiryStatusChip(medicine, today)
                if (medicine.isLowStock) LowStockChip()
            }
        }
    }
}
