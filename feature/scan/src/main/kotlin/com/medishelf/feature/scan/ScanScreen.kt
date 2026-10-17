package com.medishelf.feature.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medishelf.core.model.ScanSuggestion
import com.medishelf.core.ui.EmptyState
import com.medishelf.core.ui.MediShelfTopBar
import com.medishelf.feature.scan.camera.BarcodeAnalyzer
import com.medishelf.feature.scan.camera.CameraPreview
import com.medishelf.feature.scan.camera.captureAndReadText
import com.medishelf.feature.scan.camera.createBarcodeScanner
import com.medishelf.feature.scan.navigation.ScanMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
internal fun ScanScreen(
    onNavigateUp: () -> Unit,
    onResult: (ScanSuggestion) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) {
        (state as? ScanUiState.Done)?.let {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onResult(it.result)
        }
    }
    val title = stringResource(if (viewModel.mode == ScanMode.BARCODE) R.string.feature_scan_barcode_title else R.string.feature_scan_label_title)
    Scaffold(topBar = { MediShelfTopBar(title = title, onNavigateUp = onNavigateUp) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            CameraPermissionGate {
                when (val s = state) {
                    is ScanUiState.LabelReview -> LabelReviewContent(
                        state = s,
                        onSelectionChange = viewModel::onSelectionChange,
                        onConfirm = viewModel::onConfirmLabel,
                        onRetake = viewModel::onRetry,
                    )
                    ScanUiState.NothingRead -> EmptyState(
                        title = stringResource(R.string.feature_scan_nothing_title),
                        body = stringResource(R.string.feature_scan_nothing_body),
                        actionLabel = stringResource(R.string.feature_scan_try_again),
                        onAction = viewModel::onRetry,
                    )
                    else -> CameraContent(viewModel, isReading = s == ScanUiState.Reading)
                }
            }
        }
    }
}

@Composable
private fun CameraContent(viewModel: ScanViewModel, isReading: Boolean) {
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { createBarcodeScanner() }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val useCases = remember(viewModel.mode) {
        when (viewModel.mode) {
            ScanMode.BARCODE -> listOf(
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply { setAnalyzer(executor, BarcodeAnalyzer(scanner, viewModel::onBarcode)) },
            )
            ScanMode.LABEL -> listOf(imageCapture)
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (unavailable) {
        EmptyState(
            title = stringResource(R.string.feature_scan_no_camera_title),
            body = stringResource(R.string.feature_scan_no_camera_body),
        )
        return
    }
    Box(Modifier.fillMaxSize()) {
        CameraPreview(
            useCases = useCases,
            onCamera = { camera = it },
            onCameraUnavailable = { unavailable = true },
            modifier = Modifier.fillMaxSize(),
        )
        // Framing guide: wide for barcodes, tall-ish for box labels.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .aspectRatio(if (viewModel.mode == ScanMode.BARCODE) 1.6f else 1.2f)
                .border(3.dp, Color.White, RoundedCornerShape(16.dp)),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    text = stringResource(
                        when {
                            isReading -> R.string.feature_scan_reading
                            viewModel.mode == ScanMode.BARCODE -> R.string.feature_scan_barcode_hint
                            else -> R.string.feature_scan_label_hint
                        },
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                FilledIconToggleButton(
                    checked = torchOn,
                    onCheckedChange = {
                        torchOn = it
                        camera?.cameraControl?.enableTorch(it)
                    },
                ) {
                    Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.feature_scan_torch))
                }
            }
            if (viewModel.mode == ScanMode.LABEL) {
                if (isReading) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = {
                        viewModel.onLabelCaptureStarted()
                        scope.launch {
                            try {
                                viewModel.onLabelRead(captureAndReadText(imageCapture, executor))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                viewModel.onLabelFailed()
                            }
                        }
                    }) { Text(stringResource(R.string.feature_scan_capture)) }
                }
            }
        }
    }
}

/** Requests CAMERA on first show; explains and links to Settings if it was denied. */
@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(granted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!hasPermission && !asked) launcher.launch(Manifest.permission.CAMERA) }

    if (hasPermission) {
        content()
    } else if (asked) {
        EmptyState(
            title = stringResource(R.string.feature_scan_permission_title),
            body = stringResource(R.string.feature_scan_permission_body),
            actionLabel = stringResource(R.string.feature_scan_open_settings),
            onAction = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
    }
}
