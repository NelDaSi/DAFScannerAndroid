@file:OptIn(ExperimentalMaterial3Api::class)

package com.neldasi.jetpackcompose.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.neldasi.jetpackcompose.R
import com.neldasi.jetpackcompose.extras.parseScannedCode
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
    timestamp: Long
) {
    val parsed = parseScannedCode(fullCode)

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // Persisted note
    var note by remember {
        mutableStateOf(prefs.getString("${fullCode}_note", "") ?: "")
    }
    // Persisted image URI
    var imageUri by remember {
        mutableStateOf(
            prefs.getString("${fullCode}_imageUri", null)
                ?.toUri()
        )
    }
    var imageFileUri by remember { mutableStateOf<Uri?>(null) }

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showImagePreview by remember { mutableStateOf(false) }

    fun deleteImageForCode() {
        val file = File(context.filesDir, "img_$fullCode.jpg")
        if (file.exists()) file.delete()
        prefs.edit().remove("${fullCode}_imageUri").apply()
        imageUri = null
    }

    // Share launcher with formatted details
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* no-op */ }

    val shareIntent = remember(fullCode, imageUri, note) {
        Intent(Intent.ACTION_SEND).apply {
            val parsedText = buildString {
                appendLine(context.getString(R.string.share_title))
                appendLine()
                parsed?.let {
                    appendLine(context.getString(R.string.share_type, it.typeCode))
                    appendLine(context.getString(R.string.share_supplier, it.supplierCode))
                    appendLine(context.getString(R.string.share_serial, it.serialNumber))
                    appendLine(context.getString(R.string.share_batch, it.batchNumber))
                    appendLine()
                }
                val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
                appendLine(context.getString(R.string.share_scanned_at, formattedDate))
                if (note.isNotBlank()) {
                    appendLine(context.getString(R.string.share_note, note))
                }
            }

            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, parsedText)

            imageUri?.let {
                val file = File(context.filesDir, "img_$fullCode.jpg")
                val sharedUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                putExtra(Intent.EXTRA_STREAM, sharedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "image/jpeg"
            } ?: run {
                type = "text/plain"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.part_details)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(
                            R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val chooserIntent = Intent.createChooser(shareIntent, context.getString(R.string.share))
                        // Grant temporary read permission to the content URI for the chosen app
                        chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        shareLauncher.launch(chooserIntent)
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share))
                    }
                }
            )
        }
    ) { innerPadding ->

        // Launchers
        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success && imageFileUri != null) {
                prefs.edit().putString("${fullCode}_imageUri", imageFileUri.toString()).apply()
                imageUri = imageFileUri
            }
        }

        val galleryLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { selectedUri ->
                deleteImageForCode()
                val file = File(context.filesDir, "img_$fullCode.jpg")
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                    val savedUri = Uri.fromFile(file)
                    prefs.edit().putString("${fullCode}_imageUri", savedUri.toString()).apply()
                    imageUri = savedUri
                }
            }
        }

        if (showImageSourceDialog) {
            AlertDialog(
                onDismissRequest = { showImageSourceDialog = false },
                title = { Text(stringResource(R.string.select_image_source)) },
                text = {
                    Column {
                        if (imageUri != null) {
                            TextButton(onClick = {
                                deleteImageForCode()
                                showImageSourceDialog = false
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.remove_image), modifier = Modifier.padding(end = 8.dp))
                                    Text(stringResource(R.string.remove_image))
                                }
                            }
                        } else {
                            TextButton(onClick = {
                                val file = File(context.filesDir, "img_$fullCode.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                imageFileUri = uri
                                cameraLauncher.launch(uri)
                                showImageSourceDialog = false
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(
                                        R.string.take_photo), modifier = Modifier.padding(end = 8.dp))
                                    Text(stringResource(R.string.take_photo))
                                }
                            }
                            TextButton(onClick = {
                                galleryLauncher.launch("image/*")
                                showImageSourceDialog = false
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(
                                        R.string.pick_gallery), modifier = Modifier.padding(end = 8.dp))
                                    Text(stringResource(R.string.pick_gallery))
                                }
                            }
                        }
                    }
                },
                confirmButton = {} // No confirm button needed as actions are direct
            )
        }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Image
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = { showImageSourceDialog = true },
                                onLongClick = { if (imageUri != null) showImagePreview = true }
                            ),
                        colors = CardDefaults.cardColors(containerColor = if (imageUri != null) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().clipToBounds(),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                imageUri != null -> Image(
                                    painter = rememberAsyncImagePainter(imageUri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                else -> Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = stringResource(R.string.tap_to_add_image),
                                        tint = Color.Gray,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.tap_to_add_image),
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                // 3) Note field
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = {
                            note = it
                            prefs.edit().putString("${fullCode}_note", it).apply()
                        },
                        label = { Text(stringResource(R.string.extra_note)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)

                    )
                }
            }

            item {
                HorizontalDivider()
            }

            item {
                // 4) Parsed info and scanned date
                val formattedDate = remember(timestamp) {
                    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                        .format(Date(timestamp))
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        parsed?.let {
                            InfoRow(stringResource(R.string.type), it.typeCode)
                            InfoRow(stringResource(R.string.supplier), it.supplierCode)
                            InfoRow(stringResource(R.string.serial), it.serialNumber)
                            InfoRow(stringResource(R.string.batch), it.batchNumber)
                        }
                        InfoRow(stringResource(R.string.scanned_at), formattedDate)
                    }
                }
            }
        }
    }
    if (showImagePreview && imageUri != null) {
        AlertDialog(
            onDismissRequest = { showImagePreview = false },
            confirmButton = {},
            text = {
                ZoomableImage(imageUri!!)
            }
        )
    }
}
@Composable
fun InfoRow(label: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DetailScreenPreview() {
    // Mock NavController for preview purposes
    val mockNavController = NavController(LocalContext.current)

    // Sample data for preview
    val sampleFullCode = "21500018842993A10000000000K6805"
    val sampleTimestamp = System.currentTimeMillis()

    DetailScreen(
        navController = mockNavController,
        fullCode = sampleFullCode,
        timestamp = sampleTimestamp
    )
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
    ) {
        Image(
            painter = rememberAsyncImagePainter(uri),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
