package com.neldasi.dafscanner.screens

import android.content.Context
import android.util.Log
import androidx.camera.core.AspectRatio
import android.view.ViewGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import java.util.concurrent.TimeUnit
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.neldasi.dafscanner.extras.SettingsRepository
import com.neldasi.dafscanner.R
import com.neldasi.dafscanner.extras.processImageProxy
import com.neldasi.dafscanner.navigation.NavKeys
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


// Helper to trigger autofocus at center of preview
private fun requestCenterFocus(previewView: PreviewView, camera: Camera?) {
    try {
        if (camera == null) return

        // Create a metering point at the center of the PreviewView
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(),
            previewView.height.toFloat()
        )
        val centerPoint = factory.createPoint(
            previewView.width / 2f,
            previewView.height / 2f
        )

        val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(2, TimeUnit.SECONDS) // let it refocus again later
            .build()

        camera.cameraControl.startFocusAndMetering(action)
    } catch (_: Exception) {
        // ignore focus errors from devices that don't support it
    }
}

@Composable
fun CameraScanScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    val vibrateEnabled by remember {
        mutableStateOf(prefs.getBoolean("vibrateEnabled", false))
    }
    val continuousScanEnabled by remember {
        mutableStateOf(prefs.getBoolean("continuousScanEnabled", false))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    val allowedTypes = remember {
        SettingsRepository.loadAllowedTypes(context)
    }
    var showNotAllowedDialog by remember { mutableStateOf(false) }

    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var scannedResult by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isCameraReady by remember { mutableStateOf(false) }
    var lastSerial by remember { mutableStateOf<String?>(null) }
    var lastFlashedCode by remember { mutableStateOf<String?>(null) }

    var camera: Camera? by remember { mutableStateOf(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    var zoomRatio by remember { mutableFloatStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        val newZoom = (zoomRatio * zoomChange).coerceIn(1f, 10f)
        zoomRatio = newZoom
        camera?.cameraControl?.setZoomRatio(newZoom)
    }

    val flashAlpha = remember { Animatable(0f) }
    val flashScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            lastFlashedCode = null
            cameraExecutor.shutdown()
            cameraProvider?.unbindAll()
        }
    }

    // Trigger navigation or stay in scanner based on continuousScanEnabled
    LaunchedEffect(scannedResult) {
        scannedResult?.let { value ->
            // Flash only on the first time we see this code (per session)
            if (value != lastFlashedCode) {
                lastFlashedCode = value
                flashScope.launch {
                    try {
                        flashAlpha.snapTo(0f)
                        flashAlpha.animateTo(0.5f, tween(durationMillis = 80))
                        flashAlpha.animateTo(0f, tween(durationMillis = 180))
                    } catch (_: Exception) {}
                }
            }
            if (continuousScanEnabled) {
                // Show only the serial number in the bottom status bar
                lastSerial = extractSerial(value)
                // Send it back to main list without leaving
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(NavKeys.SCANNED_RESULT, value)
                appendPendingScan(context, value)
                scannedResult = null // reset so next scan can trigger
            } else {
                // Default behavior: close scanner
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(NavKeys.SCANNED_RESULT, value)
                navController.popBackStack()
            }
        }
    }

    Box(Modifier.fillMaxSize().transformable(state = transformableState)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                // keep a reference so we can refocus later from Compose scope
                previewViewRef = previewView

                cameraProviderFuture.addListener({
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider

                        val preview = Preview.Builder()
                            .setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setAspectRatioStrategy(
                                        AspectRatioStrategy(
                                            AspectRatio.RATIO_16_9,
                                            AspectRatioStrategy.FALLBACK_RULE_AUTO
                                        )
                                    )
                                    .build()
                            )
                            .build()
                            .also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                        val analyzer = buildImageAnalyzer(
                            cameraExecutor,
                            context,
                            vibrateEnabled
                        ) { scannedValue ->
                            Log.d("CameraScanScreen", "Scanned code: $scannedValue")
                            val type = scannedValue.take(7)
                            if (type in allowedTypes) {
                                if (scannedResult == null) {
                                    scannedResult = scannedValue
                                }
                            } else {
                                showNotAllowedDialog = true
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        provider.unbindAll()
                        val boundCamera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analyzer)
                        camera = boundCamera
                        try { boundCamera.cameraControl.enableTorch(isTorchOn) } catch (_: Exception) {}
                        try { boundCamera.cameraControl.setExposureCompensationIndex(2) } catch (_: Exception) {}

                        isCameraReady = true
                        // Kick off an initial autofocus pass on the center of the preview
                        requestCenterFocus(previewView, boundCamera)
                        // also store latest ref (safety if ref changes)
                        previewViewRef = previewView
                    } catch (exc: Exception) {
                        Log.e("CameraScanScreen", "Camera binding failed", exc)
                        cameraError = "Failed to open camera."
                        isCameraReady = false
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        LaunchedEffect(isCameraReady, camera) {
            if (!isCameraReady || camera == null) return@LaunchedEffect
            // Continuous gentle refocus loop while the screen is active
            while (isActive && isCameraReady && camera != null) {
                val pv = previewViewRef
                if (pv != null) {
                    requestCenterFocus(pv, camera)
                }
                delay(2000)
            }
        }

        // Flash overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha.value))
        )

        BottomScannerBar(
            serial = lastSerial,
            isTorchOn = isTorchOn,
            onToggleTorch = {
                val newState = !isTorchOn
                isTorchOn = newState
                camera?.cameraControl?.enableTorch(newState)
                // After toggling torch, try to refocus in the center with the extra light
                val pv = previewViewRef
                if (pv != null && camera != null) {
                    requestCenterFocus(pv, camera)
                }
            },
            onClose = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )

        CameraOverlay(
            isCameraReady = isCameraReady,
            errorMessage = cameraError
        )
    }

    if (showNotAllowedDialog) {
        AlertDialog(
            onDismissRequest = { showNotAllowedDialog = false },
            title = { Text(stringResource(R.string.unsupported_code_title)) },
            text = { Text(stringResource(R.string.unsupported_code_text)) },
            confirmButton = {
                Button(onClick = { showNotAllowedDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun CameraOverlay(isCameraReady: Boolean, errorMessage: String?) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {

        if (!isCameraReady && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (isCameraReady && errorMessage == null) {
            // Subtle aiming box to help user center the code
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .align(Alignment.Center)
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
        }

        errorMessage?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp)
            )
        }
    }
}

private fun appendPendingScan(context: Context, code: String) {
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    val existing = prefs.getString("pending_scans", null)
    val array = try {
        if (existing.isNullOrBlank()) org.json.JSONArray() else org.json.JSONArray(existing)
    } catch (_: Exception) {
        org.json.JSONArray()
    }
    array.put(code)
    prefs.edit { putString("pending_scans", array.toString()) }
}

// Extract image analyzer builder
private fun buildImageAnalyzer(
    cameraExecutor: ExecutorService,
    context: Context,
    vibrateEnabled: Boolean,
    onScannedValue: (String) -> Unit
): ImageAnalysis {
    // Configure ML Kit to look specifically for Data Matrix codes.
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX)
        .build()
    val barcodeScanner = BarcodeScanning.getClient(options)

    return ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { analysis ->
            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(
                    barcodeScanner,
                    imageProxy,
                    context,
                    vibrateEnabled,
                    onScannedValue
                )
            }
        }
}

@Composable
private fun BottomScannerBar(
    serial: String?,
    isTorchOn: Boolean,
    onToggleTorch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleTorch) {
                val icon = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff
                Icon(
                    icon,
                    contentDescription = if (isTorchOn) stringResource(R.string.flashlight_on_desc) else stringResource(R.string.flashlight_off_desc),
                    tint = Color.Yellow
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = serial ?: stringResource(R.string.scanner_default),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .padding(horizontal = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_scanner), tint = Color(0xFFFF6B6B))
            }
        }
    }
}

private fun extractSerial(fullCode: String): String {
    // Type = 7 chars, Supplier = next 5 chars, Serial = next 6 chars
    val typeLen = 7
    val supplierLen = 5
    val serialLen = 6
    val start = typeLen + supplierLen // 12
    val end = start + serialLen        // 18
    return if (fullCode.length >= end) {
        fullCode.substring(start, end)
    } else {
        // Graceful fallback for short codes: take what we can after type+supplier
        fullCode.drop(typeLen + supplierLen).take(serialLen)
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun CameraScanScreenPreview() {
    MaterialTheme {
        // Since we can't preview the actual camera, we can preview the overlay
        // with different states.
        CameraOverlay(isCameraReady = true, errorMessage = null)
    }
}
