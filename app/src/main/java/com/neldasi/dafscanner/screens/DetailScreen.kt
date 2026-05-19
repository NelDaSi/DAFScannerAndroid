@file:OptIn(ExperimentalMaterial3Api::class)

package com.neldasi.dafscanner.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.neldasi.dafscanner.R
import com.neldasi.dafscanner.extras.parseScannedCode
import com.neldasi.dafscanner.extras.saveImageToInternal
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import com.neldasi.dafscanner.viewmodels.DetailViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("UseKtx")
@Composable
fun DetailScreen(
    navController: NavController,
    fullCode: String,
    timestamp: Long,
    viewModel: DetailViewModel = viewModel(),
) {
    LaunchedEffect(fullCode) {
        viewModel.loadPart(fullCode)
    }

    val part by viewModel.part.collectAsStateWithLifecycle()
    val note = part?.note ?: ""
    val imageUri = part?.imageUri?.toUri()

    DetailScreenContent(
        navController = navController,
        fullCode = fullCode,
        timestamp = timestamp,
        note = note,
        imageUri = imageUri,
        onNoteChange = { viewModel.updateNote(it) },
    ) { viewModel.updateImageForCode(fullCode, it) }
}

@Composable
fun DetailScreenContent(
    navController: NavController,
    fullCode: String,
    timestamp: Long,
    note: String,
    imageUri: Uri?,
    onNoteChange: (String) -> Unit,
    onImageUpdate: (String?) -> Unit,
) {
    val context = LocalContext.current
    val parsed = remember(fullCode) { parseScannedCode(fullCode) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val shareTitle = stringResource(R.string.share_title)
    val shareTypeLabel = stringResource(R.string.share_type)
    val shareSupplierLabel = stringResource(R.string.share_supplier)
    val shareEngineFormatLabel = stringResource(R.string.share_engine_format)
    val shareSerialLabel = stringResource(R.string.share_serial)
    val shareBatchLabel = stringResource(R.string.share_batch)
    val shareScannedAtLabel = stringResource(R.string.share_scanned_at)
    val shareNoteLabel = stringResource(R.string.share_note)
    val shareSubject = stringResource(R.string.share_subject)
    val shareLabel = stringResource(R.string.share)

    var imageFileUri by remember { mutableStateOf<Uri?>(value = null) }
    var showImageSourceDialog by remember { mutableStateOf(value = false) }
    var showImagePreview by remember { mutableStateOf(value = false) }

    val scope = rememberCoroutineScope()

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val file = File(context.filesDir, "img_$fullCode.jpg")
            onImageUpdate(Uri.fromFile(file).toString())
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                val savedUri = saveImageToInternal(context, selectedUri, fullCode)
                onImageUpdate(savedUri)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.part_details), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = { 
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                            val parsedText = buildString {
                                appendLine(shareTitle)
                                appendLine()
                                parsed?.let {
                                    appendLine(shareTypeLabel.format(it.typeCode))
                                    appendLine(shareSupplierLabel.format(it.supplierCode))
                                    appendLine(shareEngineFormatLabel.format(it.format.name))
                                    appendLine(shareSerialLabel.format(it.serialNumber))
                                    appendLine(shareBatchLabel.format(it.batchNumber))
                                    appendLine()
                                }
                                val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                                appendLine(shareScannedAtLabel.format(formattedDate))
                                if (note.isNotBlank()) appendLine(shareNoteLabel.format(note))
                            }
                            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                            putExtra(Intent.EXTRA_TEXT, parsedText)
                            imageUri?.let {
                                val file = File(context.filesDir, "img_$fullCode.jpg")
                                val sharedUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                putExtra(Intent.EXTRA_STREAM, sharedUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                type = "image/jpeg"
                            } ?: run { type = "text/plain" }
                        }
                        val chooser = Intent.createChooser(chooserIntent, shareLabel)
                        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(chooser)
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Background Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(28.dp))
                            .clickable { 
                                if (imageUri != null) showImagePreview = true 
                                else showImageSourceDialog = true 
                            },
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (imageUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(imageUri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Change/Add Overlay
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .clickable { showImageSourceDialog = true },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondary,
                                    tonalElevation = 4.dp
                                ) {
                                    Icon(
                                        Icons.Rounded.AddAPhoto,
                                        contentDescription = stringResource(R.string.edit_image),
                                        modifier = Modifier.padding(10.dp).size(20.dp),
                                        tint = Color.White
                                    )
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        modifier = Modifier.size(72.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    ) {
                                        Icon(
                                            Icons.Rounded.AddAPhoto,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(20.dp).size(32.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.tap_to_add_image),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Notes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.extra_note),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = note,
                                onValueChange = onNoteChange,
                                placeholder = { Text(stringResource(R.string.enter_note_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            val formattedDate = remember(timestamp) {
                                SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                            }
                            
                            parsed?.let {
                                DetailRow(Icons.Rounded.Fingerprint, stringResource(R.string.type), it.typeCode)
                                DetailRow(Icons.Rounded.Business, stringResource(R.string.supplier), it.supplierCode)
                                DetailRow(Icons.Rounded.Category, stringResource(R.string.engine_format), it.format.name)
                                DetailRow(Icons.Rounded.Tag, stringResource(R.string.serial_hex), it.serialNumber)
                                DetailRow(Icons.Rounded.Dialpad, stringResource(R.string.serial_dec), it.decSerial)
                                DetailRow(Icons.Rounded.Category, stringResource(R.string.batch), it.batchNumber)
                            }
                            DetailRow(Icons.Rounded.EventAvailable, stringResource(R.string.scanned_at), formattedDate)
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text(stringResource(R.string.select_image_source), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageSourceItem(Icons.Rounded.CameraAlt, stringResource(R.string.take_photo)) {
                        val file = File(context.filesDir, "img_$fullCode.jpg")
                        // Delete existing file to ensure camera writes to a fresh file
                        if (file.exists()) file.delete()

                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        imageFileUri = uri
                        cameraLauncher.launch(uri)
                        showImageSourceDialog = false
                    }
                    ImageSourceItem(Icons.Rounded.PhotoLibrary, stringResource(R.string.pick_gallery)) {
                        galleryLauncher.launch("image/*")
                        showImageSourceDialog = false
                    }
                    if (imageUri != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        TextButton(onClick = {
                            val file = File(context.filesDir, "img_$fullCode.jpg")
                            if (file.exists()) file.delete()
                            onImageUpdate(null)
                            showImageSourceDialog = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.remove_image), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showImagePreview && (imageUri != null)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ZoomableImage(imageUri)
                
                IconButton(
                    onClick = { showImagePreview = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 16.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ImageSourceItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(label)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Preview(showBackground = true, apiLevel = 36)
@Composable
fun DetailScreenPreview() {
    val mockNavController = rememberNavController()
    JetpackComposeTheme {
        DetailScreenContent(
            navController = mockNavController,
            fullCode = "21500018842993A10000000000K6805",
            timestamp = System.currentTimeMillis(),
            note = "This is a sample note for the preview.",
            imageUri = null,
            onNoteChange = {},
        ) {}
    }
}

@Composable
fun ZoomableImage(uri: Uri) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)

                        val extraWidth = (scale - 1) * maxWidth
                        val extraHeight = (scale - 1) * maxHeight

                        val maxX = (extraWidth / 2).coerceAtLeast(0f)
                        val maxY = (extraHeight / 2).coerceAtLeast(0f)

                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 3f
                            }
                        }
                    )
                }
        ) {
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}
