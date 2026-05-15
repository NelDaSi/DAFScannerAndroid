package com.neldasi.dafscanner.screens

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
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
import com.neldasi.dafscanner.extras.ScanStorage
import com.neldasi.dafscanner.extras.SettingsRepository
import com.neldasi.dafscanner.extras.isRunningOnEmulator
import com.neldasi.dafscanner.extras.parseScannedCode
import com.neldasi.dafscanner.extras.processImageProxy
import com.neldasi.dafscanner.navigation.NavKeys
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import com.neldasi.dafscanner.viewmodels.SearchListViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


data class ScanFeedback(
    val serial: String,
    val isMatch: Boolean,
    val alreadyScanned: Boolean,
    val scanTimestamp: Long? = null
)

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
fun CameraScanScreen(
    navController: NavController,
    isVerifyMode: Boolean = false,
    searchViewModel: SearchListViewModel? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    
    var vibrateEnabled by remember { mutableStateOf(prefs.getBoolean("vibrateEnabled", false)) }
    var continuousScanEnabled by remember { mutableStateOf(prefs.getBoolean("continuousScanEnabled", false)) }
    var screenAlwaysOn by remember { mutableStateOf(prefs.getBoolean("screenAlwaysOn", false)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val allowedTypes = remember { SettingsRepository.loadAllowedTypes(context) }

    // Keep screen on logic
    LaunchedEffect(screenAlwaysOn) {
        val activity = context as? Activity
        if (screenAlwaysOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    var showNotAllowedDialog by remember { mutableStateOf(value = false) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(value = null) }
    var scannedResult by remember { mutableStateOf<String?>(value = null) }
    var cameraError by remember { mutableStateOf<String?>(value = null) }
    var isCameraReady by remember { mutableStateOf(value = false) }
    var lastSerial by remember { mutableStateOf<String?>(value = null) }
    var lastFlashedCode by remember { mutableStateOf<String?>(value = null) }
    var camera: Camera? by remember { mutableStateOf(value = null) }
    var isTorchOn by remember { mutableStateOf(value = false) }

    // State for Feedback and Cooldowns
    var verifyResult by remember { mutableStateOf<ScanFeedback?>(null) }
    var countdown by remember { mutableIntStateOf(0) }
    val verifyScope = rememberCoroutineScope()
    var isPaused by remember { mutableStateOf(false) }
    
    // To prevent a new scan from immediately triggering its own duplicate warning
    var lastProcessedCode by remember { mutableStateOf<String?>(null) }
    var lastProcessedTimestamp by remember { mutableLongStateOf(0L) }

    val existingParts = remember {
        navController.previousBackStackEntry?.savedStateHandle?.get<Map<String, Long>>("EXISTING_PARTS") ?: emptyMap()
    }
    val sessionScanned = remember { mutableStateMapOf<String, Long>() }

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
            // 1. Debounce rapid-fire scans of the exact same code in Continuous Mode
            val now = System.currentTimeMillis()
            if (continuousScanEnabled && value == lastProcessedCode && (now - lastProcessedTimestamp) < 2000) {
                scannedResult = null
                return@let
            }

            lastSerial = extractSerial(value)

            // 2. Flash visual feedback
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

            // 3. Process the scan based on mode
            if (isVerifyMode) {
                val serial = extractSerial(value)
                if (searchViewModel != null) {
                    val currentItems = searchViewModel.searchItems.value
                    val matchItem = currentItems.find { it.serialNumber == serial }
                    val isMatch = matchItem != null
                    val alreadyScanned = matchItem?.scanTimestamp != null
                    searchViewModel.checkScannedCode(context, value)
                    verifyResult = ScanFeedback(serial, isMatch, alreadyScanned, matchItem?.scanTimestamp)
                } else {
                    val serialList = navController.previousBackStackEntry?.savedStateHandle?.get<List<String>>("SERIAL_LIST") ?: emptyList()
                    val scannedSerials = navController.previousBackStackEntry?.savedStateHandle?.get<List<String>>("SCANNED_SERIALS") ?: emptyList()
                    val isMatch = serialList.contains(serial)
                    val alreadyScanned = scannedSerials.contains(serial)
                    verifyResult = ScanFeedback(serial, isMatch, alreadyScanned)
                    if (isMatch && !alreadyScanned) {
                        val updatedScanned = scannedSerials + serial
                        navController.previousBackStackEntry?.savedStateHandle?.set("SCANNED_SERIALS", updatedScanned)
                    }
                }

                isPaused = true
                navController.previousBackStackEntry?.savedStateHandle?.set(NavKeys.SCANNED_RESULT, value)
                
                countdown = 3
                verifyScope.launch {
                    while (countdown > 0) {
                        delay(1000)
                        if (verifyResult?.serial == serial) countdown-- else break
                    }
                    if (verifyResult?.serial == serial) {
                        verifyResult = null
                        isPaused = false
                        scannedResult = null
                    }
                }
            } else {
                // Main Scanning Mode logic
                val sessionTimestamp = sessionScanned[value]
                val existingTimestamp = existingParts[value]
                val finalTimestamp = sessionTimestamp ?: existingTimestamp
                
                if (finalTimestamp != null) {
                    // DUPLICATE DETECTED
                    val serial = extractSerial(value)
                    verifyResult = ScanFeedback(serial, isMatch = false, alreadyScanned = true, scanTimestamp = finalTimestamp)
                    isPaused = true
                    
                    countdown = 3
                    verifyScope.launch {
                        while (countdown > 0) {
                            delay(1000)
                            if (verifyResult?.serial == serial) countdown-- else break
                        }
                        if (verifyResult?.serial == serial) {
                            verifyResult = null
                            isPaused = false
                            scannedResult = null
                        }
                    }
                } else {
                    // NEW SCAN SUCCESS
                    lastProcessedCode = value
                    lastProcessedTimestamp = now
                    
                    if (continuousScanEnabled) {
                        navController.previousBackStackEntry?.savedStateHandle?.set(NavKeys.SCANNED_RESULT, value)
                        appendPendingScan(context, value)
                        sessionScanned[value] = now
                        scannedResult = null
                    } else {
                        navController.previousBackStackEntry?.savedStateHandle?.set(NavKeys.SCANNED_RESULT, value)
                        navController.popBackStack()
                    }
                }
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
        verifyResult = verifyResult,
        countdown = countdown,
        isVerifyMode = isVerifyMode,
        vibrateEnabled = vibrateEnabled,
        continuousScanEnabled = continuousScanEnabled,
        screenAlwaysOn = screenAlwaysOn,
        onToggleVibrate = {
            vibrateEnabled = !vibrateEnabled
            prefs.edit { putBoolean("vibrateEnabled", vibrateEnabled) }
        },
        onToggleContinuous = {
            continuousScanEnabled = !continuousScanEnabled
            prefs.edit { putBoolean("continuousScanEnabled", continuousScanEnabled) }
        },
        onToggleScreenOn = {
            screenAlwaysOn = !screenAlwaysOn
            prefs.edit { putBoolean("screenAlwaysOn", screenAlwaysOn) }
        },
        onToggleTorch = {
            val newState = !isTorchOn
            isTorchOn = newState
            camera?.cameraControl?.enableTorch(newState)
            previewViewRef?.let { requestCenterFocus(it, camera) }
        },
        onClose = {
            searchViewModel?.clearResult()
            navController.popBackStack()
        },
        onDismissVerify = {
            verifyResult = null
            isPaused = false
            scannedResult = null
        },
        onSimulateScan = {
            if (!isPaused) {
                val simulatedCode = "215000188429${(100000..999999).random()}"
                scannedResult = simulatedCode
            }
        }
    ) { ctx ->
        if (isRunningOnEmulator()) {
            // In emulator, we don't start CameraX to avoid errors/black screen
            // or we could let it start if it works, but usually it's just a black screen.
            // Let's just provide a dummy view.
            android.view.View(ctx)
        } else {
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
                        if (!isPaused) {
                            if (scannedValue.take(7) in allowedTypes) {
                                if (scannedResult == null) scannedResult = scannedValue
                            } else {
                                showNotAllowedDialog = true
                            }
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
                    cameraError = context.getString(R.string.camera_error)
                    isCameraReady = false
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    }

    if (isRunningOnEmulator()) {
        isCameraReady = true
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
    verifyResult: ScanFeedback? = null,
    countdown: Int = 0,
    isVerifyMode: Boolean = false,
    vibrateEnabled: Boolean = false,
    continuousScanEnabled: Boolean = false,
    screenAlwaysOn: Boolean = false,
    onToggleVibrate: () -> Unit = {},
    onToggleContinuous: () -> Unit = {},
    onToggleScreenOn: () -> Unit = {},
    onToggleTorch: () -> Unit,
    onClose: () -> Unit,
    onDismissVerify: () -> Unit = {},
    onSimulateScan: () -> Unit = {},
    onAndroidViewFactory: (Context) -> android.view.View,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformableState)
            .then(if (isRunningOnEmulator()) Modifier.clickable(onClick = onSimulateScan) else Modifier)
    ) {
        AndroidView(factory = onAndroidViewFactory, modifier = Modifier.fillMaxSize())

        // Flash overlay
        Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))

        CameraOverlay(isCameraReady = isCameraReady, errorMessage = cameraError)

        TopScannerBar(
            serial = lastSerial,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 24.dp, end = 24.dp)
        )

        if (verifyResult != null) {
            val backgroundColor = when {
                verifyResult.alreadyScanned && !isVerifyMode -> Color.DarkGray
                verifyResult.isMatch -> Color(0xFFD32F2F) // Red for Match
                else -> Color(0xFF388E3C) // Green for No Match
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor.copy(alpha = 0.9f))
                    .clickable { onDismissVerify() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        when {
                            verifyResult.alreadyScanned -> Icons.Rounded.History
                            verifyResult.isMatch -> Icons.Rounded.Warning
                            else -> Icons.Rounded.CheckCircle
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    if (verifyResult.alreadyScanned) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.already_scanned),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            verifyResult.scanTimestamp?.let { ts ->
                                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                                val locale = configuration.locales[0] ?: Locale.getDefault()
                                val dateStr = remember(ts, locale) {
                                    SimpleDateFormat("dd MMM, HH:mm", locale).format(Date(ts))
                                }
                                Text(
                                    text = stringResource(R.string.first_seen, dateStr),
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }

                    Text(
                        text = when {
                            verifyResult.alreadyScanned && !isVerifyMode -> stringResource(R.string.duplicate_code)
                            verifyResult.isMatch -> stringResource(R.string.match_found)
                            else -> stringResource(R.string.no_match)
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${stringResource(R.string.hex_prefix)}${verifyResult.serial}",
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${stringResource(R.string.dec_prefix)}${try { verifyResult.serial.toLong(16).toString() } catch(_:Exception) { "N/A" }}",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(Modifier.height(48.dp))
                    
                    Button(
                        onClick = onDismissVerify,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = backgroundColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(
                            text = if (countdown > 0) stringResource(R.string.dismiss_countdown, countdown) else stringResource(R.string.dismiss),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        if (isRunningOnEmulator()) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(R.string.emulator_mode_desc),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        BottomScannerBar(
            isTorchOn = isTorchOn,
            onToggleTorch = onToggleTorch,
            vibrateEnabled = vibrateEnabled,
            onToggleVibrate = onToggleVibrate,
            continuousScanEnabled = continuousScanEnabled,
            onToggleContinuous = onToggleContinuous,
            screenAlwaysOn = screenAlwaysOn,
            onToggleScreenOn = onToggleScreenOn,
            onClose = onClose,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
        )
    }
}

@Composable
private fun TopScannerBar(
    serial: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = serial != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.8f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = serial ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
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
    isTorchOn: Boolean,
    onToggleTorch: () -> Unit,
    vibrateEnabled: Boolean,
    onToggleVibrate: () -> Unit,
    continuousScanEnabled: Boolean,
    onToggleContinuous: () -> Unit,
    screenAlwaysOn: Boolean,
    onToggleScreenOn: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Torch
                ScannerOptionButton(
                    icon = if (isTorchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    isActive = isTorchOn,
                    activeColor = Color.Yellow,
                    onClick = onToggleTorch
                )
                
                // Continuous Scan
                ScannerOptionButton(
                    icon = Icons.Rounded.SystemUpdateAlt,
                    isActive = continuousScanEnabled,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = onToggleContinuous
                )

                // Vibrate
                ScannerOptionButton(
                    icon = Icons.Rounded.NotificationsActive,
                    isActive = vibrateEnabled,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = onToggleVibrate
                )

                // Screen Always On
                ScannerOptionButton(
                    icon = Icons.Rounded.ScreenLockRotation,
                    isActive = screenAlwaysOn,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onClick = onToggleScreenOn
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ScannerOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(if (isActive) activeColor.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) activeColor else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )
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

@ComposePreview(showBackground = true, name = "Normal Scan", apiLevel = 34)
@Composable
fun CameraScanScreenPreview() {
    JetpackComposeTheme {
        CameraScanScreenContent(
            isCameraReady = true,
            cameraError = null,
            lastSerial = "123456",
            isTorchOn = false,
            flashAlpha = 0f,
            transformableState = rememberTransformableState { _, _, _ -> },
            isVerifyMode = false,
            onToggleTorch = {},
            onClose = {},
            onSimulateScan = {},
        ) { android.view.View(it) }
    }
}

@ComposePreview(showBackground = true, name = "Duplicate Scan", apiLevel = 34)
@Composable
fun CameraScanScreenDuplicatePreview() {
    JetpackComposeTheme {
        CameraScanScreenContent(
            isCameraReady = true,
            cameraError = null,
            lastSerial = "123456",
            isTorchOn = false,
            flashAlpha = 0f,
            transformableState = rememberTransformableState { _, _, _ -> },
            verifyResult = ScanFeedback(
                serial = "01C821",
                isMatch = false,
                alreadyScanned = true,
                scanTimestamp = System.currentTimeMillis() - 3600000
            ),
            isVerifyMode = false,
            onToggleTorch = {},
            onClose = {},
            onSimulateScan = {},
        ) { android.view.View(it) }
    }
}
