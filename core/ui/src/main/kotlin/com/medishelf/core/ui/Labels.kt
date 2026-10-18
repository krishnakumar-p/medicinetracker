package com.medishelf.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.medishelf.core.model.ExpiryWindow
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineSort
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.PhotoType
import com.medishelf.core.model.QuantityUnit

@Composable
fun MedicineForm.label(): String = stringResource(
    when (this) {
        MedicineForm.TABLET -> R.string.core_ui_form_tablet
        MedicineForm.CAPSULE -> R.string.core_ui_form_capsule
        MedicineForm.SYRUP -> R.string.core_ui_form_syrup
        MedicineForm.DROPS -> R.string.core_ui_form_drops
        MedicineForm.INHALER -> R.string.core_ui_form_inhaler
        MedicineForm.INJECTION -> R.string.core_ui_form_injection
        MedicineForm.CREAM -> R.string.core_ui_form_cream
        MedicineForm.OTHER -> R.string.core_ui_form_other
    },
)

@Composable
fun QuantityUnit.label(): String = stringResource(
    when (this) {
        QuantityUnit.TABLETS -> R.string.core_ui_unit_tablets
        QuantityUnit.CAPSULES -> R.string.core_ui_unit_capsules
        QuantityUnit.ML -> R.string.core_ui_unit_ml
        QuantityUnit.DOSES -> R.string.core_ui_unit_doses
        QuantityUnit.GRAMS -> R.string.core_ui_unit_grams
        QuantityUnit.PIECES -> R.string.core_ui_unit_pieces
        QuantityUnit.OTHER -> R.string.core_ui_unit_other
    },
)

@Composable
fun MedicineStatus.label(): String = stringResource(
    when (this) {
        MedicineStatus.ACTIVE -> R.string.core_ui_status_active
        MedicineStatus.EXPIRED -> R.string.core_ui_status_expired
        MedicineStatus.FINISHED -> R.string.core_ui_status_finished
        MedicineStatus.DISPOSED -> R.string.core_ui_status_disposed
    },
)

@Composable
fun PhotoType.label(): String = stringResource(
    when (this) {
        PhotoType.BOX -> R.string.core_ui_photo_box
        PhotoType.STRIP -> R.string.core_ui_photo_strip
        PhotoType.PRESCRIPTION -> R.string.core_ui_photo_prescription
    },
)

@Composable
fun MedicineEventType.label(): String = stringResource(
    when (this) {
        MedicineEventType.CREATED -> R.string.core_ui_event_created
        MedicineEventType.EDITED -> R.string.core_ui_event_edited
        MedicineEventType.QUANTITY_USED -> R.string.core_ui_event_quantity_used
        MedicineEventType.STATUS_CHANGED -> R.string.core_ui_event_status_changed
        MedicineEventType.AUTO_EXPIRED -> R.string.core_ui_event_auto_expired
        MedicineEventType.DOSE_TAKEN -> R.string.core_ui_event_dose_taken
        MedicineEventType.DOSE_UNDONE -> R.string.core_ui_event_dose_undone
    },
)

@Composable
fun ExpiryWindow.label(): String = stringResource(
    when (this) {
        ExpiryWindow.ANY -> R.string.core_ui_window_any
        ExpiryWindow.EXPIRED -> R.string.core_ui_window_expired
        ExpiryWindow.WITHIN_7_DAYS -> R.string.core_ui_window_7
        ExpiryWindow.WITHIN_30_DAYS -> R.string.core_ui_window_30
        ExpiryWindow.WITHIN_90_DAYS -> R.string.core_ui_window_90
    },
)

@Composable
fun MedicineSort.label(): String = stringResource(
    when (this) {
        MedicineSort.EXPIRY_ASCENDING -> R.string.core_ui_sort_expiry
        MedicineSort.NAME_ASCENDING -> R.string.core_ui_sort_name
    },
)
