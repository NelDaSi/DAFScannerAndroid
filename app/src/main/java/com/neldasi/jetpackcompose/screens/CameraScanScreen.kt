package com.neldasi.jetpackcompose.screens

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.neldasi.jetpackcompose.navigation.NavKeys
import com.neldasi.jetpackcompose.extras.SettingsRepository
import com.neldasi.jetpackcompose.extras.processImageProxy
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
    val recentScans = remember { mutableStateListOf<String>() }
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

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            cameraProvider?.unbindAll()
        }
    }

    // Trigger navigation or stay in scanner based on continuousScanEnabled
    LaunchedEffect(scannedResult) {
        scannedResult?.let { value ->
            if (continuousScanEnabled) {
                // Stay in scanner mode and show recent results
                if (!recentScans.contains(value)) {
                    recentScans.add(0, value)
                    if (recentScans.size > 5) recentScans.lastIndex
                }
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
                        provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analyzer)

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

        if (continuousScanEnabled && recentScans.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Recent scans:",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    for (code in recentScans) {
                        Text(
                            text = code,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        CameraOverlay(
            isCameraReady = isCameraReady,
            errorMessage = cameraError,
            onBackPressed = { navController.popBackStack() }
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
private fun CameraOverlay(isCameraReady: Boolean, errorMessage: String?, onBackPressed: () -> Unit) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        IconButton(
            onClick = onBackPressed,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

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
    prefs.edit().putString("pending_scans", array.toString()).apply()
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

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun CameraScanScreenPreview() {
    MaterialTheme {
        // Since we can't preview the actual camera, we can preview the overlay
        // with different states.
        CameraOverlay(isCameraReady = true, errorMessage = null, onBackPressed = {})
    }
}
