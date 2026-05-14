@file:OptIn(ExperimentalMaterial3Api::class)

package com.neldasi.dafscanner.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.neldasi.dafscanner.viewmodels.DetailViewModel
import java.io.File
import java.io.FileOutputStream
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
    ) { viewModel.updateImage(it) }
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
    val shareSerialLabel = stringResource(R.string.share_serial)
    val shareBatchLabel = stringResource(R.string.share_batch)
    val shareScannedAtLabel = stringResource(R.string.share_scanned_at)
    val shareNoteLabel = stringResource(R.string.share_note)
    val shareSubject = stringResource(R.string.share_subject)
    val shareLabel = stringResource(R.string.share)

    var imageFileUri by remember { mutableStateOf<Uri?>(value = null) }
    var showImageSourceDialog by remember { mutableStateOf(value = false) }
    var showImagePreview by remember { mutableStateOf(value = false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && (imageFileUri != null)) {
            onImageUpdate(imageFileUri.toString())
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            val file = File(context.filesDir, "img_$fullCode.jpg")
            if (file.exists()) file.delete()
            context.contentResolver.openInputStream(selectedUri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
                onImageUpdate(Uri.fromFile(file).toString())
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
                    IconButton(onClick = { navController.popBackStack() }) {
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
                                    appendLine(shareSerialLabel.format(it.serialNumber))
                                    appendLine(shareBatchLabel.format(it.batchNumber))
                                    appendLine()
                                }
                                val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
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
                        Icon(Icons.Rounded.IosShare, contentDescription = stringResource(R.string.share))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.5f)
                        .clip(RoundedCornerShape(24.dp))
                        .combinedClickable(
                            onClick = { showImageSourceDialog = true },
                            onLongClick = { if (imageUri != null) showImagePreview = true }
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (imageUri != null) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Rounded.AddAPhoto,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.tap_to_add_image),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.extra_note),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        placeholder = { Text(stringResource(R.string.extra_note)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.title_scanned_items),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        val formattedDate = remember(timestamp) {
                            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
                        }
                        parsed?.let {
                            DetailRow(Icons.Rounded.Numbers, stringResource(R.string.type), it.typeCode)
                            DetailRow(Icons.Rounded.Business, stringResource(R.string.supplier), it.supplierCode)
                            DetailRow(Icons.Rounded.Tag, "Serial (HEX)", it.serialNumber)
                            DetailRow(Icons.Rounded.Tag, "Serial (DEC)", it.decSerial)
                            DetailRow(Icons.Rounded.Layers, stringResource(R.string.batch), it.batchNumber)
                        }
                        DetailRow(Icons.Rounded.Event, stringResource(R.string.scanned_at), formattedDate)
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text(stringResource(R.string.select_image_source), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (imageUri != null) {
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
                    } else {
                        ImageSourceItem(Icons.Rounded.CameraAlt, stringResource(R.string.take_photo)) {
                            val file = File(context.filesDir, "img_$fullCode.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            imageFileUri = uri
                            cameraLauncher.launch(uri)
                            showImageSourceDialog = false
                        }
                        ImageSourceItem(Icons.Rounded.PhotoLibrary, stringResource(R.string.pick_gallery)) {
                            galleryLauncher.launch("image/*")
                            showImageSourceDialog = false
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showImagePreview && (imageUri != null)) {
        AlertDialog(
            onDismissRequest = { showImagePreview = false },
            confirmButton = {},
            text = { ZoomableImage(imageUri) }
        )
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

@Preview(showBackground = true)
@Composable
fun DetailScreenPreview() {
    val mockNavController = rememberNavController()
    MaterialTheme {
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
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale *= zoomChange
        offset += offsetChange
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer(
                scaleX = scale.coerceIn(1f, 5f),
                scaleY = scale.coerceIn(1f, 5f),
                translationX = offset.x,
                translationY = offset.y
            )
            .transformable(state = state)
            .clipToBounds()
    ) {
        Image(
            painter = rememberAsyncImagePainter(uri),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
