@file:OptIn(ExperimentalMaterial3Api::class)

package com.neldasi.jetpackcompose

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun DetailScreen(
    navController: NavController,
    fullCode: String,
    timestamp: Long
) {
    val parsed = parseScannedCode(fullCode)
//    val context = LocalContext.current

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

    // Image picker (gallery)
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> imageUri = uri }

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
                    IconButton(onClick = { shareLauncher.launch(Intent.createChooser(shareIntent, "Share via")) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

            // Image preview / picker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.LightGray)
                    .clickable { galleryLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri == null) {
                    Text("Tap to add an image", color = Color.DarkGray)
                } else {
                    // To display the image, add Coil or other image-loading library:
                    Image(
                        painter = rememberAsyncImagePainter(imageUri),
                        contentDescription = "Selected image",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
