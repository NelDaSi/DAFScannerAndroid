package com.neldasi.jetpackcompose.screens

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.neldasi.jetpackcompose.extras.SettingsRepository
import com.neldasi.jetpackcompose.extras.processImageProxy
import com.neldasi.jetpackcompose.navigation.NavKeys
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


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

    Box(Modifier.fillMaxSize()) {
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

                cameraProviderFuture.addListener({
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val analyzer = buildImageAnalyzer(cameraExecutor, context, vibrateEnabled) { scannedValue ->
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

                        isCameraReady = true
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
            title = { Text("Unsupported Code") },
            text = { Text("The scanned code is not supported and will not be saved.") },
            confirmButton = {
                Button(onClick = { showNotAllowedDialog = false }) {
                    Text("OK")
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
    val barcodeScanner = BarcodeScanning.getClient()
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
                Icon(icon, contentDescription = if (isTorchOn) "Turn flashlight off" else "Turn flashlight on", tint = Color.Yellow)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = serial ?: "Scanner",
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
                Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = Color(0xFFFF6B6B))
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
