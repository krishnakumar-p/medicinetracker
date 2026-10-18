package com.medishelf.feature.medicines.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.medishelf.core.model.DoseSchedule
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineEvent
import com.medishelf.core.ui.MemberAvatar
import com.medishelf.core.ui.formatMedium
import com.medishelf.core.ui.formatQuantity
import com.medishelf.core.ui.label
import com.medishelf.core.ui.quantityLabel
import com.medishelf.feature.medicines.R
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun DetailSections(details: MedicineDetails, today: LocalDate, schedules: List<DoseSchedule> = emptyList()) {
    val m = details.medicine
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DetailCard(stringResource(R.string.feature_medicines_section_stock)) {
            DetailRow(stringResource(R.string.feature_medicines_field_quantity), m.quantityLabel())
            m.lowStockThreshold?.let {
                DetailRow(stringResource(R.string.feature_medicines_field_threshold), it.formatQuantity())
            }
        }
        DetailCard(stringResource(R.string.feature_medicines_section_dates)) {
            DetailRow(stringResource(R.string.feature_medicines_field_expiry), m.expiryDate.formatMedium())
            m.openedDate?.let { DetailRow(stringResource(R.string.feature_medicines_field_opened), it.formatMedium()) }
            m.useWithinDaysAfterOpening?.let {
                DetailRow(stringResource(R.string.feature_medicines_field_use_within), stringResource(R.string.feature_medicines_days_value, it))
            }
            if (m.effectiveExpiryDate != m.expiryDate) {
                DetailRow(stringResource(R.string.feature_medicines_field_effective_expiry), m.effectiveExpiryDate.formatMedium())
            }
            m.purchaseDate?.let { DetailRow(stringResource(R.string.feature_medicines_field_purchase), it.formatMedium()) }
        }
        if (details.assignments.isNotEmpty()) {
            DetailCard(stringResource(R.string.feature_medicines_section_people)) {
                details.assignments.forEach { assignment ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MemberAvatar(assignment.member.name, assignment.member.avatarColor, size = 32.dp)
                        Column {
                            Text(assignment.member.name, style = MaterialTheme.typography.bodyLarge)
                            assignment.condition?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        val prescribedFor = listOfNotNull(details.prescribedFor?.name, m.prescribedForReason.takeIf(String::isNotBlank)).joinToString(" · ")
        if (m.isPrescription || m.prescribedBy.isNotBlank() || m.dosageInstructions.isNotBlank() || m.prescriptionDate != null ||
            prescribedFor.isNotEmpty() || m.prescriptionStartDate != null || m.prescriptionEndDate != null || schedules.isNotEmpty()
        ) {
            DetailCard(stringResource(R.string.feature_medicines_section_prescription)) {
                if (m.isPrescription) Text(stringResource(R.string.feature_medicines_prescription_only))
                if (m.prescribedBy.isNotBlank()) DetailRow(stringResource(R.string.feature_medicines_field_prescribed_by), m.prescribedBy)
                if (prescribedFor.isNotEmpty()) DetailRow(stringResource(R.string.feature_medicines_prescribed_for), prescribedFor)
                m.prescriptionDate?.let { DetailRow(stringResource(R.string.feature_medicines_field_prescription_date), it.formatMedium()) }
                m.prescriptionStartDate?.let { DetailRow(stringResource(R.string.feature_medicines_field_prescription_start), it.formatMedium()) }
                m.prescriptionEndDate?.let { DetailRow(stringResource(R.string.feature_medicines_field_prescription_end), it.formatMedium()) }
                schedules.forEach { s ->
                    val label = stringResource(
                        if (s.kind == DoseScheduleKind.CONTINUOUS) R.string.feature_medicines_continuous_dose else R.string.feature_medicines_course_dose,
                    )
                    val amount = stringResource(
                        R.string.feature_medicines_dose_value,
                        s.dosePerIntake.formatQuantity(),
                        m.unit.label(),
                        pluralStringResource(R.plurals.feature_medicines_times_per_day, s.timesPerDay, s.timesPerDay),
                    )
                    val until = s.endDate?.let { stringResource(R.string.feature_medicines_dose_until, it.formatMedium()) }
                    DetailRow(label, listOfNotNull(amount, until).joinToString(" · "))
                }
                if (m.dosageInstructions.isNotBlank()) DetailRow(stringResource(R.string.feature_medicines_field_dosage), m.dosageInstructions)
            }
        }
        DetailCard(stringResource(R.string.feature_medicines_section_details)) {
            DetailRow(stringResource(R.string.feature_medicines_field_form), m.form.label())
            DetailRow(
                stringResource(R.string.feature_medicines_field_location),
                details.location?.let { location ->
                    if (location.requiresRefrigeration) {
                        stringResource(R.string.feature_medicines_location_refrigerated, location.name)
                    } else {
                        location.name
                    }
                } ?: stringResource(com.medishelf.core.ui.R.string.core_ui_unassigned_location),
            )
            if (m.manufacturer.isNotBlank()) DetailRow(stringResource(R.string.feature_medicines_field_manufacturer), m.manufacturer)
            if (m.batchNumber.isNotBlank()) DetailRow(stringResource(R.string.feature_medicines_field_batch), m.batchNumber)
            m.barcode?.let { DetailRow(stringResource(R.string.feature_medicines_field_barcode), it) }
            if (m.notes.isNotBlank()) DetailRow(stringResource(R.string.feature_medicines_field_notes), m.notes)
        }
    }
}

@Composable
internal fun PhotoSection(photos: List<PhotoUi>, onAdd: () -> Unit, onRemove: (PhotoUi) -> Unit) {
    DetailCard(stringResource(R.string.feature_medicines_section_photos)) {
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.photo.id }) { photo ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            AsyncImage(
                                model = File(photo.path),
                                contentDescription = stringResource(R.string.feature_medicines_photo_description, photo.photo.type.label()),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(photo.photo.type.label(), style = MaterialTheme.typography.labelMedium)
                            IconButton(onClick = { onRemove(photo) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.feature_medicines_remove_photo, photo.photo.type.label()),
                                )
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.feature_medicines_add_photo), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
internal fun HistoryHeader() {
    Text(
        stringResource(R.string.feature_medicines_section_history),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
internal fun HistoryRow(event: MedicineEvent) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        val date = event.at.atZone(ZoneId.systemDefault()).toLocalDate().formatMedium()
        Text("${event.type.label()} · $date", style = MaterialTheme.typography.bodyLarge)
        val detail = listOfNotNull(
            event.quantityDelta?.let { stringResource(R.string.feature_medicines_history_delta, (-it).formatQuantity()) },
            if (event.fromStatus != null && event.toStatus != null) "${event.fromStatus!!.label()} → ${event.toStatus!!.label()}" else null,
            event.note,
        ).joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
