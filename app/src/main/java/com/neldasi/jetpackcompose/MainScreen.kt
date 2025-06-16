@file:OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)

package com.neldasi.jetpackcompose

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    val itemsSet = remember { mutableStateListOf<String>() }
    // var scannedText by remember { mutableStateOf("") } // Only if actively used in UI

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        val saved = sharedPreferences.getStringSet("items", emptySet()) ?: emptySet()
        itemsSet.clear()
        itemsSet.addAll(saved)
    }

    fun saveItems(items: Set<String>) {
        sharedPreferences.edit { putStringSet("items", items) }
    }

    LaunchedEffect(Unit) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            val scannedValue = backStackEntry.savedStateHandle.remove<String>(NavKeys.SCANNED_RESULT)

            if (!scannedValue.isNullOrBlank() && scannedValue !in itemsSet) {
                itemsSet.add(scannedValue)
                saveItems(itemsSet.toSet())
            }
        }
    }


    // --- Permission Dialogs ---
    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text("Camera Permission Required") },
            text = { Text("This app needs camera access to scan QR codes. Please grant the permission.") },
            confirmButton = {
                Button(onClick = {
                    showPermissionRationaleDialog = false
                    cameraPermissionState.launchPermissionRequest()
                }) {
                    Text("Grant")
                }
            },
            dismissButton = {
                Button(onClick = { showPermissionRationaleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Permission Required") },
            text = { Text("Camera permission has been permanently denied. Please enable it in app settings to use the scanner.") },
            confirmButton = {
                Button(onClick = {
                    showSettingsDialog = false
                    // Open app settings
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                Button(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }
    // --- End Permission Dialogs ---

    Scaffold(
        modifier = Modifier.systemBarsPadding(), // Apply padding for edge-to-edge
        topBar = {
            TopAppBar(
                title = { Text("Scanned Items") },
                actions = {
                    IconButton(onClick = { /* TODO: Implement actual settings */ }) {
                        Icon(Icons.Filled.Settings, contentDescription = "App Settings")
                    }
                    IconButton(onClick = { /* TODO: Implement About screen/dialog */ }) {
                        Icon(Icons.Filled.Info, contentDescription = "About")
                    }
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // Use paddingValues from Scaffold
                    .padding(16.dp), // Additional screen padding
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (itemsSet.isEmpty()) {
                    Text("No items scanned yet.", textAlign = TextAlign.Center)
                    if (!cameraPermissionState.status.isGranted) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Camera permission is needed to scan items.",
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(itemsSet.toList().reversed()) { item -> // Show newest first
                            Text(
                                text = item,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (cameraPermissionState.status) {
                        PermissionStatus.Granted -> {
                            navController.navigate(AppDestinations.CAMERA_SCREEN)
                        }
                        is PermissionStatus.Denied -> {
                            if (cameraPermissionState.status.shouldShowRationale) {
                                showPermissionRationaleDialog = true
                            } else {
                                // First time asking or denied without "Don't ask again"
                                cameraPermissionState.launchPermissionRequest()
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Scan Barcode")
            }
        }
    )
}