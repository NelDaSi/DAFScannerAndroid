package com.neldasi.jetpackcompose

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService



@Composable
fun CameraScanScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

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

    // Trigger navigation when scanned result is ready
    LaunchedEffect(scannedResult) {
        scannedResult?.let { value ->
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(NavKeys.SCANNED_RESULT, value)
            navController.popBackStack()
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

                        val analyzer = buildImageAnalyzer(cameraExecutor) { scannedValue ->
                            if (scannedResult == null) {
                                scannedResult = scannedValue
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

        CameraOverlay(
            isCameraReady = isCameraReady,
            errorMessage = cameraError,
            onBackPressed = { navController.popBackStack() }
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

// Extract image analyzer builder
private fun buildImageAnalyzer(
    cameraExecutor: ExecutorService,
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
                    onScannedValue
                )
            }
        }
}