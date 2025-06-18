@file:OptIn(ExperimentalMaterial3Api::class)

package com.neldasi.jetpackcompose

import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun DetailScreen(
    navController: NavController,
    fullCode: String,
    timestamp: Long
) {
    val parsed = parseScannedCode(fullCode)

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
                    IconButton(onClick = {/* image options */}) {
                        Icon(Icons.Filled.Edit, contentDescription = "Share")
                    }
                }
            )
        }
    ) { innerPadding ->

        // Image state from camera or gallery
        var imageBitmap by rememberSaveable { mutableStateOf<Bitmap?>(null) }
        var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }

        // Launchers
        val cameraLauncher = rememberLauncherForActivityResult(
            TakePicturePreview()
        ) { bitmap -> bitmap?.let { imageBitmap = it } }

        val galleryLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri -> imageUri = uri }

        // Note field state
        var note by rememberSaveable { mutableStateOf("") }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1) Big image placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.DarkGray)
                    .clickable { /* no-op or open image */ },
                contentAlignment = Alignment.Center
            ) {
                when {
                    imageBitmap != null -> Image(
                        bitmap = imageBitmap!!.asImageBitmap(),
                        contentDescription = "Captured image",
                        modifier = Modifier.fillMaxSize()
                    )
                    imageUri != null -> Image(
                        painter = rememberAsyncImagePainter(imageUri),
                        contentDescription = "Selected image",
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> Text("Tap camera or gallery below", color = Color.LightGray)
                }
            }

            // 2) Action buttons: Share, Camera, Gallery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { shareLauncher.launch(Intent.createChooser(shareIntent, "Share via")) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share")
                }
                IconButton(onClick = { cameraLauncher.launch(null) }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Take photo")
                }
                IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Pick from gallery")
                }
            }

            // 3) Note field
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
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
    val mockNavController = NavController(androidx.compose.ui.platform.LocalContext.current)

    // Sample data for preview
    val sampleFullCode = "TYPE123;SUPP456;SERIAL789;BATCH000"
    val sampleTimestamp = System.currentTimeMillis()

    DetailScreen(
        navController = mockNavController,
        fullCode = sampleFullCode,
        timestamp = sampleTimestamp
    )
}
