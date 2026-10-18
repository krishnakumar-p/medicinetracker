package com.medishelf.feature.medicines.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicinePhoto
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.PhotoType
import com.medishelf.core.ui.DisposalGuidanceCard
import com.medishelf.core.ui.EmptyState
import com.medishelf.core.ui.ExpiryStatusChip
import com.medishelf.core.ui.LowStockChip
import com.medishelf.core.ui.LoadingState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.core.ui.titleWithStrength
import com.medishelf.feature.medicines.R

internal data class MedicineDetailActions(
    val onNavigateUp: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onTakeRegularly: () -> Unit = {},
    val onUse: (Double?) -> Unit = {},
    val onMarkFinished: () -> Unit = {},
    val onMarkDisposed: (String) -> Unit = {},
    val onRestore: () -> Unit = {},
    val onPickPhoto: (PhotoType) -> Unit = {},
    val onRemovePhoto: (MedicinePhoto) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
internal fun MedicineDetailScreen(
    onNavigateUp: () -> Unit,
    onEdit: (Long) -> Unit,
    onTakeRegularly: (Long) -> Unit = {},
    viewModel: MedicineDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingPhotoType by rememberSaveable { mutableStateOf<PhotoType?>(null) }
    // System photo picker: no storage permission needed and works fully offline.
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val type = pendingPhotoType
        if (uri != null && type != null) viewModel.onAddPhoto(type, uri.toString())
        pendingPhotoType = null
    }
    MedicineDetailContent(
        state = state,
        actions = MedicineDetailActions(
            onNavigateUp = onNavigateUp,
            onEdit = { state.details?.let { onEdit(it.medicine.id) } },
            onTakeRegularly = { state.details?.let { onTakeRegularly(it.medicine.id) } },
            onUse = viewModel::onUse,
            onMarkFinished = viewModel::onMarkFinished,
            onMarkDisposed = viewModel::onMarkDisposed,
            onRestore = viewModel::onRestore,
            onPickPhoto = { type ->
                pendingPhotoType = type
                picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            },
            onRemovePhoto = viewModel::onRemovePhoto,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicineDetailContent(state: MedicineDetailUiState, actions: MedicineDetailActions) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(resources.getString(it.textRes()))
            actions.onMessageShown()
        }
    }
    var dialog by rememberSaveable { mutableStateOf<DetailDialog?>(null) }
    val details = state.details

    Scaffold(
        topBar = {
            MediShelfTopBar(
                title = details?.medicine?.titleWithStrength ?: stringResource(R.string.feature_medicines_detail_title),
                onNavigateUp = actions.onNavigateUp,
                actions = {
                    if (details != null) {
                        IconButton(onClick = actions.onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.feature_medicines_edit))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            details == null -> EmptyState(
                title = stringResource(R.string.feature_medicines_not_found_title),
                body = stringResource(R.string.feature_medicines_not_found_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Header(details, state) }
                if (details.medicine.effectiveStatus(state.today) == MedicineStatus.EXPIRED) {
                    item { DisposalGuidanceCard() }
                }
                item {
                    ActionButtons(
                        details.medicine.status,
                        onDialog = { dialog = it },
                        onRestore = actions.onRestore,
                        onTakeRegularly = actions.onTakeRegularly,
                    )
                }
                item { DetailSections(details, state.today, state.schedules) }
                item {
                    PhotoSection(
                        photos = state.photos,
                        onAdd = { dialog = DetailDialog.PickPhotoType },
                        onRemove = { dialog = DetailDialog.RemovePhoto(it.photo.id) },
                    )
                }
                item { HistoryHeader() }
                items(state.history, key = { it.id }) { HistoryRow(it) }
            }
        }
    }

    DetailDialogs(
        dialog = dialog,
        photos = state.photos,
        actions = actions,
        onDismiss = { dialog = null },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(details: MedicineDetails, state: MedicineDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (details.medicine.genericName.isNotBlank()) {
            Text(details.medicine.genericName, style = MaterialTheme.typography.titleMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpiryStatusChip(details.medicine, state.today)
            if (details.medicine.isLowStock) LowStockChip()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionButtons(
    status: MedicineStatus,
    onDialog: (DetailDialog) -> Unit,
    onRestore: () -> Unit,
    onTakeRegularly: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (status) {
            MedicineStatus.ACTIVE, MedicineStatus.EXPIRED -> {
                Button(onClick = { onDialog(DetailDialog.Use) }) { Text(stringResource(R.string.feature_medicines_mark_used)) }
                OutlinedButton(onClick = { onDialog(DetailDialog.Finish) }) {
                    Text(stringResource(R.string.feature_medicines_mark_finished))
                }
                OutlinedButton(onClick = { onDialog(DetailDialog.Dispose) }) {
                    Text(stringResource(R.string.feature_medicines_mark_disposed))
                }
                OutlinedButton(onClick = onTakeRegularly, modifier = Modifier.testTag("take_regularly")) {
                    Text(stringResource(R.string.feature_medicines_take_regularly))
                }
            }
            MedicineStatus.FINISHED -> {
                OutlinedButton(onClick = onRestore) { Text(stringResource(R.string.feature_medicines_restore)) }
                OutlinedButton(onClick = { onDialog(DetailDialog.Dispose) }) {
                    Text(stringResource(R.string.feature_medicines_mark_disposed))
                }
            }
            MedicineStatus.DISPOSED ->
                OutlinedButton(onClick = onRestore) { Text(stringResource(R.string.feature_medicines_restore)) }
        }
    }
}

private fun DetailMessage.textRes(): Int = when (this) {
    DetailMessage.USED -> R.string.feature_medicines_msg_used
    DetailMessage.FINISHED -> R.string.feature_medicines_msg_finished
    DetailMessage.DISPOSED -> R.string.feature_medicines_msg_disposed
    DetailMessage.RESTORED -> R.string.feature_medicines_msg_restored
    DetailMessage.PHOTO_ADDED -> R.string.feature_medicines_msg_photo_added
    DetailMessage.PHOTO_FAILED -> R.string.feature_medicines_msg_photo_failed
    DetailMessage.INVALID_AMOUNT -> R.string.feature_medicines_msg_invalid_amount
}
