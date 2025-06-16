@file:OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)

package com.neldasi.jetpackcompose

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController
import com.google.accompanist.permissions.*

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    val itemsSet = remember { mutableStateListOf<String>() }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Load data once on first composition
    LaunchedEffect(Unit) {
        val saved = sharedPreferences.getStringSet("items", emptySet()) ?: emptySet()
        itemsSet.clear()
        itemsSet.addAll(saved)
    }

    // Collect scanned result from navigation
    LaunchedEffect(Unit) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            val scannedValue = backStackEntry.savedStateHandle.remove<String>(NavKeys.SCANNED_RESULT)
            if (!scannedValue.isNullOrBlank() && scannedValue !in itemsSet) {
                itemsSet.add(scannedValue)
                sharedPreferences.edit { putStringSet("items", itemsSet.toSet()) }
            }
        }
    }

    // State for menu and dialogs
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("Scanned Items") },
                actions = {
                    // 3-dot menu
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = { showMenu = false; /* TODO: Implement actual settings */ }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear All") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { showMenu = false; showClearDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = { showMenu = false; showInfoDialog = true }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when (cameraPermissionState.status) {
                    PermissionStatus.Granted -> {
                        navController.navigate(AppDestinations.CAMERA_SCREEN)
                    }
                    is PermissionStatus.Denied -> {
                        if (cameraPermissionState.status.shouldShowRationale) {
                            showPermissionRationaleDialog = true
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }
                }
            }) {
                Icon(Icons.Filled.Search, contentDescription = "Scan")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (itemsSet.isEmpty()) {
                Text("No items scanned yet.", textAlign = TextAlign.Center)
                if (!cameraPermissionState.status.isGranted) {
                    Spacer(Modifier.height(8.dp))
                    Text("Camera permission is needed to scan items.", textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(itemsSet.toList().reversed()) { item ->
                        Text(text = item, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }

    // Confirmation dialog for "Clear All"
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Confirm Clear") },
            text = { Text("Are you sure you want to clear all scanned items?") },
            confirmButton = {
                Button(onClick = {
                    showClearDialog = false
                    itemsSet.clear()
                    sharedPreferences.edit { putStringSet("items", emptySet()) }
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Info dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("About") },
            text = { Text("This is a QR scanning app.\n\n(More info to be added here later.)") },
            confirmButton = {
                Button(onClick = { showInfoDialog = false }) { Text("OK") }
            }
        )
    }

    PermissionRationaleDialog(
        show = showPermissionRationaleDialog,
        onDismiss = { showPermissionRationaleDialog = false },
        onConfirm = {
            showPermissionRationaleDialog = false
            cameraPermissionState.launchPermissionRequest()
        }
    )

    PermissionSettingsDialog(
        show = showSettingsDialog,
        onDismiss = { showSettingsDialog = false },
        context = context
    )
}

@Composable
private fun PermissionRationaleDialog(show: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Camera Permission Required") },
            text = { Text("This app needs camera access to scan QR codes.") },
            confirmButton = { Button(onClick = onConfirm) { Text("Grant") } },
            dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PermissionSettingsDialog(show: Boolean, onDismiss: () -> Unit, context: Context) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Permission Required") },
            text = { Text("Camera permission has been permanently denied. Please enable it in settings.") },
            confirmButton = {
                Button(onClick = {
                    onDismiss()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                }) { Text("Open Settings") }
            },
            dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}