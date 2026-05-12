package com.neldasi.dafscanner.screens

import android.content.Context
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.neldasi.dafscanner.R
import com.neldasi.dafscanner.extras.SettingsRepository
import com.neldasi.dafscanner.extras.parseScannedCode
import com.neldasi.dafscanner.extras.processImageProxy
import com.neldasi.dafscanner.navigation.NavKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


// Helper to trigger autofocus at center of preview
private fun requestCenterFocus(previewView: PreviewView, camera: Camera?) {
    try {
        if (camera == null) return

        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(),
            previewView.height.toFloat(),
        )
        val centerPoint = factory.createPoint(
            previewView.width / 2f,
            previewView.height / 2f,
        )

        val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(2, TimeUnit.SECONDS)
            .build()

        camera.cameraControl.startFocusAndMetering(action)
    } catch (_: Exception) {}
}

@Composable
fun CameraScanScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val vibrateEnabled = remember { prefs.getBoolean("vibrateEnabled", false) }
    val continuousScanEnabled = remember { prefs.getBoolean("continuousScanEnabled", false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val allowedTypes = remember { SettingsRepository.loadAllowedTypes(context) }
    
    var showNotAllowedDialog by remember { mutableStateOf(value = false) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(value = null) }
    var scannedResult by remember { mutableStateOf<String?>(value = null) }
    var cameraError by remember { mutableStateOf<String?>(value = null) }
    var isCameraReady by remember { mutableStateOf(value = false) }
    var lastSerial by remember { mutableStateOf<String?>(value = null) }
    var lastFlashedCode by remember { mutableStateOf<String?>(value = null) }
    var camera: Camera? by remember { mutableStateOf(value = null) }
    var isTorchOn by remember { mutableStateOf(value = false) }

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

    LaunchedEffect(scannedResult) {
        scannedResult?.let { value ->
            if (value != lastFlashedCode) {
                lastFlashedCode = value
                flashScope.launch {
                    try {
                        flashAlpha.snapTo(0f)
                        flashAlpha.animateTo(0.4f, tween(80))
                        flashAlpha.animateTo(0f, tween(180))
                    } catch (_: Exception) {}
                }
            }
            if (continuousScanEnabled) {
                lastSerial = extractSerial(value)
                navController.previousBackStackEntry?.savedStateHandle?.set(NavKeys.SCANNED_RESULT, value)
                appendPendingScan(context, value)
                scannedResult = null
            } else {
                navController.previousBackStackEntry?.savedStateHandle?.set(NavKeys.SCANNED_RESULT, value)
                navController.popBackStack()
            }
        }
    }

    CameraScanScreenContent(
        isCameraReady = isCameraReady,
        cameraError = cameraError,
        lastSerial = lastSerial,
        isTorchOn = isTorchOn,
        flashAlpha = flashAlpha.value,
        transformableState = transformableState,
        onToggleTorch = {
            val newState = !isTorchOn
            isTorchOn = newState
            camera?.cameraControl?.enableTorch(newState)
            previewViewRef?.let { requestCenterFocus(it, camera) }
        },
        onClose = { navController.popBackStack() }
    ) { ctx ->
        val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            previewViewRef = previewView
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder()
                        .setResolutionSelector(ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO))
                            .build())
                        .build().also { it.surfaceProvider = previewView.surfaceProvider }

                    val analyzer = buildImageAnalyzer(cameraExecutor, context, vibrateEnabled) { scannedValue ->
                        if (scannedValue.take(7) in allowedTypes) {
                            if (scannedResult == null) scannedResult = scannedValue
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
                    requestCenterFocus(previewView, boundCamera)
                } catch (_: Exception) {
                    cameraError = "Failed to open camera."
                    isCameraReady = false
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }

    LaunchedEffect(isCameraReady, camera) {
        if (!isCameraReady || (camera == null)) return@LaunchedEffect
        while (isActive && isCameraReady && (camera != null)) {
            previewViewRef?.let { requestCenterFocus(it, camera) }
            delay(2000)
        }
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
fun CameraScanScreenContent(
    isCameraReady: Boolean,
    cameraError: String?,
    lastSerial: String?,
    isTorchOn: Boolean,
    flashAlpha: Float,
    transformableState: androidx.compose.foundation.gestures.TransformableState,
    onToggleTorch: () -> Unit,
    onClose: () -> Unit,
    onAndroidViewFactory: (Context) -> android.view.View
) {
    Box(Modifier.fillMaxSize().transformable(state = transformableState)) {
        AndroidView(factory = onAndroidViewFactory, modifier = Modifier.fillMaxSize())

        // Flash overlay
        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))

        CameraOverlay(isCameraReady = isCameraReady, errorMessage = cameraError)

        BottomScannerBar(
            serial = lastSerial,
            isTorchOn = isTorchOn,
            onToggleTorch = onToggleTorch,
            onClose = onClose,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
        )
    }
}

@Composable
private fun CameraOverlay(isCameraReady: Boolean, errorMessage: String?) {
    Box(Modifier.fillMaxSize()) {
        if (!isCameraReady && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }

        if (isCameraReady && errorMessage == null) {
            // Darken the area outside the scanner frame
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                val frameSize = 260.dp.toPx()
                val left = (size.width - frameSize) / 2
                val top = (size.height - frameSize) / 2
                
                // Draw semi-transparent background
                drawRect(Color.Black.copy(alpha = 0.5f))

                // Cut out the center
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = Size(frameSize, frameSize),
                    blendMode = BlendMode.Clear
                )
            }
            
            // Draw the frame corners and scanning line
            ScannerFrame(modifier = Modifier.size(260.dp).align(Alignment.Center))
        }

        errorMessage?.let {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.8f)
            ) {
                Text(
                    text = it,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ScannerFrame(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scannerLine")
    val lineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "lineOffset"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val cornerLen = 40.dp.toPx()
        val color = Color.White

        // 1. Darken surroundings (Simple approach for preview)
        // Note: For a true cutout effect, we'd use BlendMode.Clear on a layer, but let's stick to simple corners for now.
        
        // 2. Draw 4 corners
        // Top Left
        drawLine(color, Offset(0f, 0f), Offset(cornerLen, 0f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(0f, 0f), Offset(0f, cornerLen), strokeWidth, StrokeCap.Round)
        // Top Right
        drawLine(color, Offset(size.width, 0f), Offset(size.width - cornerLen, 0f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(size.width, cornerLen), strokeWidth, StrokeCap.Round)
        // Bottom Left
        drawLine(color, Offset(0f, size.height), Offset(cornerLen, size.height), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(0f, size.height), Offset(0f, size.height - cornerLen), strokeWidth, StrokeCap.Round)
        // Bottom Right
        drawLine(color, Offset(size.width, size.height), Offset(size.width - cornerLen, size.height), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - cornerLen), strokeWidth, StrokeCap.Round)

        // 3. Scanning line
        val y = size.height * lineOffset
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(10.dp.toPx(), y),
            end = Offset(size.width - 10.dp.toPx(), y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
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
    Surface(
        modifier = modifier.navigationBarsPadding(),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(32.dp),
        color = Color.Black.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleTorch,
                modifier = Modifier.size(52.dp).background(if (isTorchOn) Color.Yellow.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = null,
                    tint = if (isTorchOn) Color.Yellow else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = serial ?: stringResource(R.string.scanner_default),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    softWrap = true
                )
                if (serial != null) {
                    Text(
                        text = "Last Scanned",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun appendPendingScan(context: Context, code: String) {
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    val existing = prefs.getString("pending_scans", null)
    val array = try {
        if (existing.isNullOrBlank()) org.json.JSONArray() else org.json.JSONArray(existing)
    } catch (_: Exception) { org.json.JSONArray() }
    array.put(code)
    prefs.edit { putString("pending_scans", array.toString()) }
}

private fun buildImageAnalyzer(
    cameraExecutor: ExecutorService,
    context: Context,
    vibrateEnabled: Boolean,
    onScannedValue: (String) -> Unit
): ImageAnalysis {
    val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX).build()
    val barcodeScanner = BarcodeScanning.getClient(options)
    return ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        .also { analysis ->
            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(barcodeScanner, imageProxy, context, vibrateEnabled, onScannedValue)
            }
        }
}

private fun extractSerial(fullCode: String): String {
    return parseScannedCode(fullCode)?.serialNumber ?: (if (fullCode.length >= 18) fullCode.substring(12, 18) else fullCode.take(6))
}

@ComposePreview(showBackground = true)
@Composable
fun CameraScanScreenPreview() {
    MaterialTheme {
        CameraScanScreenContent(
            isCameraReady = true,
            cameraError = null,
            lastSerial = "123456",
            isTorchOn = false,
            flashAlpha = 0f,
            transformableState = rememberTransformableState { _, _, _ -> },
            onToggleTorch = {},
            onClose = {},
        ) { android.view.View(it) }
    }
}
