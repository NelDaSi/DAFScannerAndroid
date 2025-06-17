
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.google.gson.Gson
import java.util.Date

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val validTypes = loadAllowedTypes(context)
    val sharedPreferences = remember {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    val scannedParts = remember { mutableStateListOf<ScannedPart>() }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Load data once on first composition
    LaunchedEffect(Unit) {
        val json = sharedPreferences.getString("items", null)
        if (json != null) {
            val loaded = Gson().fromJson(json, Array<ScannedPart>::class.java)
            scannedParts.addAll(loaded)
        }
    }

    // Collect scanned result from navigation
    LaunchedEffect(Unit) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            val scannedValue = backStackEntry.savedStateHandle.remove<String>(NavKeys.SCANNED_RESULT)
            if (!scannedValue.isNullOrBlank() && scannedParts.none { it.fullCode == scannedValue }) {
                val newPart = ScannedPart(scannedValue, System.currentTimeMillis())
                scannedParts.add(newPart)
                val jsonString = Gson().toJson(scannedParts.toTypedArray())
                sharedPreferences.edit { putString("items", jsonString) }
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
                            onClick = {
                                showMenu = false
                                navController.navigate(AppDestinations.SETTINGS_SCREEN)
                            }
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
            if (scannedParts.isEmpty()) {
                Text("No items scanned yet.", textAlign = TextAlign.Center)
                if (!cameraPermissionState.status.isGranted) {
                    Spacer(Modifier.height(8.dp))
                    Text("Camera permission is needed to scan items.", textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(scannedParts.toList().sortedByDescending { it.timestamp }) { part ->
                        val parsed = parseScannedCode(part.fullCode)
                        if (parsed != null && parsed.typeCode in validTypes) {
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text("Type: ${parsed.typeCode}")
                                Text("Serial: ${parsed.serialNumber}")
                                Text("Date: ${Date(part.timestamp)}")
                            }
                        } else {
                            Text("Invalid part: ${part.fullCode}")
                        }
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
                    scannedParts.clear()
                    sharedPreferences.edit { remove("items") }
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
fun loadAllowedTypes(context: Context): List<String> {
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    val defaultTypes = setOf(
        "1615188", "1615597", "1656701", "1665585", "1669851",
        "1783137", "2187738", "2126628", "2266341", "2150000",
        "2265920", "2265921", "2002045", "2002046", "2002047",
        "2002048", "2002049", "2002050", "2002051", "2245293",
        "2245295", "2204980", "2261325", "2260980"
    )
    val types = prefs.getStringSet("allowedTypes", defaultTypes)
    return types?.toList() ?: defaultTypes.toList()
}

// --- Data classes and helpers for scanned parts ---
data class ScannedPart(val fullCode: String, val timestamp: Long)
