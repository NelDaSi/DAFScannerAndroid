@file:OptIn(ExperimentalMaterial3Api::class)

package com.neldasi.jetpackcompose

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap.CompressFormat
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
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

    var showDeleteDialog by remember { mutableStateOf(false) }

    fun deleteImageForCode() {
        val file = File(context.filesDir, "img_$fullCode.jpg")
        if (file.exists()) file.delete()
        prefs.edit().remove("${fullCode}_imageUri").apply()
        imageUri = null
    }

    // Share launcher
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* no-op */ }
    val shareIntent = remember(fullCode) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, fullCode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Part Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (imageUri != null) {
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        deleteImageForCode()
                                        showDeleteDialog = false
                                    }) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                                title = { Text("Remove Image?") },
                                text = { Text("Are you sure you want to remove this image?") }
                            )
                        }

                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Remove image")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->

        // Launchers
        val cameraLauncher = rememberLauncherForActivityResult(
            TakePicturePreview()
        ) { bitmap ->
            bitmap?.let {
                // Save bitmap to internal storage
                val file = File(context.filesDir, "img_$fullCode.jpg")
                FileOutputStream(file).use { out ->
                    it.compress(CompressFormat.JPEG, 95, out)
                }
                val uri = Uri.fromFile(file)
                prefs.edit().putString("${fullCode}_imageUri", uri.toString()).apply()
                imageUri = uri
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

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Image
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .background(Color.DarkGray)
                        .clipToBounds()
                        .clickable { /* no-op or open image */ },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        imageUri != null -> Image(
                            painter = rememberAsyncImagePainter(imageUri),
                            contentDescription = "Selected image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        else -> Text("Tap camera or gallery below", color = Color.LightGray)
                    }
                }

                // Action buttons
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { shareLauncher.launch(Intent.createChooser(shareIntent, "Share via")) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { cameraLauncher.launch(null) }) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Take photo")
                    }
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "Pick from gallery")
                    }
                }
            }

            // 3) Note field
            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it
                    prefs.edit().putString("${fullCode}_note", it).apply()
                },
                label = { Text("Extra note") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            // 4) Parsed info
            parsed?.let {
                Text("Type: ${it.typeCode}", style = MaterialTheme.typography.bodyLarge)
                Text("Supplier: ${it.supplierCode}", style = MaterialTheme.typography.bodyLarge)
                Text("Serial: ${it.serialNumber}", style = MaterialTheme.typography.bodyLarge)
                Text("Batch: ${it.batchNumber}", style = MaterialTheme.typography.bodyLarge)
            }

            val formattedDate = remember(timestamp) {
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    .format(Date(timestamp))
            }
            Text("Scanned at: $formattedDate", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DetailScreenPreview() {
    // Mock NavController for preview purposes
    val mockNavController = NavController(LocalContext.current)

    // Sample data for preview
    val sampleFullCode = "TYPE123;SUPP456;SERIAL789;BATCH000"
    val sampleTimestamp = System.currentTimeMillis()

    DetailScreen(
        navController = mockNavController,
        fullCode = sampleFullCode,
        timestamp = sampleTimestamp
    )
}
