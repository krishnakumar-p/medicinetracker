package com.medishelf.feature.medicines.detail

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medishelf.core.model.PhotoType
import com.medishelf.core.ui.ConfirmDialog
import com.medishelf.core.ui.DisposalGuidanceCard
import com.medishelf.core.ui.label
import com.medishelf.core.ui.parseLocalizedNumber
import com.medishelf.feature.medicines.R
import kotlinx.parcelize.Parcelize

internal sealed interface DetailDialog : Parcelable {
    @Parcelize data object Use : DetailDialog
    @Parcelize data object Finish : DetailDialog
    @Parcelize data object Dispose : DetailDialog
    @Parcelize data object PickPhotoType : DetailDialog
    @Parcelize data class RemovePhoto(val photoId: Long) : DetailDialog
}

@Composable
internal fun DetailDialogs(
    dialog: DetailDialog?,
    photos: List<PhotoUi>,
    actions: MedicineDetailActions,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        DetailDialog.Use -> UseDialog(onConfirm = { actions.onUse(it); onDismiss() }, onDismiss = onDismiss)
        DetailDialog.Finish -> ConfirmDialog(
            title = stringResource(R.string.feature_medicines_finish_title),
            text = stringResource(R.string.feature_medicines_finish_body),
            confirmLabel = stringResource(R.string.feature_medicines_mark_finished),
            onConfirm = { actions.onMarkFinished(); onDismiss() },
            onDismiss = onDismiss,
        )
        DetailDialog.Dispose -> DisposeDialog(onConfirm = { actions.onMarkDisposed(it); onDismiss() }, onDismiss = onDismiss)
        DetailDialog.PickPhotoType -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.feature_medicines_photo_type_title)) },
            text = {
                Column {
                    PhotoType.entries.forEach { type ->
                        TextButton(onClick = { onDismiss(); actions.onPickPhoto(type) }, modifier = Modifier.fillMaxWidth()) {
                            Text(type.label())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.medishelf.core.ui.R.string.core_ui_cancel)) } },
        )
        is DetailDialog.RemovePhoto -> {
            val photo = photos.find { it.photo.id == dialog.photoId }
            if (photo == null) {
                onDismiss()
            } else {
                ConfirmDialog(
                    title = stringResource(R.string.feature_medicines_remove_photo_title),
                    text = stringResource(R.string.feature_medicines_remove_photo_body),
                    confirmLabel = stringResource(com.medishelf.core.ui.R.string.core_ui_delete),
                    onConfirm = { actions.onRemovePhoto(photo.photo); onDismiss() },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun UseDialog(onConfirm: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("1") }
    val amount = parseLocalizedNumber(text)
    val valid = amount != null && amount > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_medicines_use_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.feature_medicines_use_amount)) },
                isError = !valid,
                supportingText = if (!valid) {
                    { Text(stringResource(R.string.feature_medicines_msg_invalid_amount)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(amount) }, enabled = valid) {
                Text(stringResource(R.string.feature_medicines_mark_used))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.medishelf.core.ui.R.string.core_ui_cancel)) } },
    )
}

@Composable
private fun DisposeDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_medicines_dispose_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DisposalGuidanceCard()
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.feature_medicines_dispose_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note) }) { Text(stringResource(R.string.feature_medicines_mark_disposed)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.medishelf.core.ui.R.string.core_ui_cancel)) } },
    )
}
