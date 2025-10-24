@file:OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
package com.neldasi.jetpackcompose.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.gson.Gson
import com.neldasi.jetpackcompose.R
import com.neldasi.jetpackcompose.extras.parseScannedCode
import com.neldasi.jetpackcompose.navigation.AppDestinations
import com.neldasi.jetpackcompose.navigation.NavKeys
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SelectablePart(val part: ScannedPart, var isSelected: Boolean = false)

@Composable
fun MainScreen(navController: NavController, initialItems: List<SelectablePart>? = null) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    val scannedParts = remember {
        mutableStateListOf<SelectablePart>().also {
            if (initialItems != null) it.addAll(initialItems)
        }
    }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var itemToDelete by remember { mutableStateOf<ScannedPart?>(null) }

    var selectionMode by remember { mutableStateOf(false) }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val duplicateCodes = remember { mutableStateListOf<String>() }
    var showDuplicateDialog by remember { mutableStateOf(false) }

    // Load data once on first composition
    if (initialItems == null) {
        LaunchedEffect(Unit) {
            val json = sharedPreferences.getString("items", null)
            if (json != null) {
                val loaded = Gson().fromJson(json, Array<ScannedPart>::class.java)
                scannedParts.addAll(
                    loaded.map {
                        val uri = sharedPreferences.getString("${it.fullCode}_imageUri", null)
                        val note = sharedPreferences.getString("${it.fullCode}_note", null)
                        SelectablePart(it.copy(imageUri = uri, note = note))
                    }
                )
            }
        }
    }

    // Collect scanned results (single or batched) and also consume any pending queue from prefs
    LaunchedEffect(Unit) {
        // Helper to add a code into the list and persist it
        fun addCodeIfNew(code: String) {
            if (code.isBlank()) return
            if (scannedParts.none { it.part.fullCode == code }) {
                val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                val lastOrdinal = prefs.getInt("lastOrdinal", 0)
                val nextOrdinal = lastOrdinal + 1

                val newPart = ScannedPart(code, System.currentTimeMillis(), ordinal = nextOrdinal)
                scannedParts.add(SelectablePart(newPart))
                saveParts(context, scannedParts.map { it.part })
                prefs.edit { putInt("lastOrdinal", nextOrdinal) }
            } else {
                // Instead of showing many Toasts, collect duplicates and show once in a dialog
                if (!duplicateCodes.contains(code)) {
                    duplicateCodes.add(code)
                }
                showDuplicateDialog = true
            }
        }

        // Consume pending_scans queue, skipping any codes in `exclude` and ignoring duplicates in the queue
        fun consumePendingFromPrefs(exclude: Set<String>) {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("pending_scans", null)
            if (!json.isNullOrBlank()) {
                try {
                    val arr = Gson().fromJson(json, Array<String>::class.java)
                    val seen = mutableSetOf<String>()
                    arr?.forEach { code ->
                        // skip anything we already processed via savedStateHandle
                        if (exclude.contains(code)) return@forEach
                        // skip duplicates inside the queue itself
                        if (!seen.add(code)) return@forEach
                        try { addCodeIfNew(code) } catch (_: Exception) {}
                    }
                } catch (_: Exception) { /* ignore malformed */ }
                // Clear the queue once consumed to prevent replays
                prefs.edit { remove("pending_scans") }
            }
        }

        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            val consumed = mutableSetOf<String>()
            // Single item
            backStackEntry.savedStateHandle.remove<String>(NavKeys.SCANNED_RESULT)?.let { code ->
                addCodeIfNew(code)
                consumed.add(code)
            }
            // Batch (if ever used)
            backStackEntry.savedStateHandle.remove<List<String>>("SCANNED_RESULTS")?.let { list ->
                list.forEach { code ->
                    addCodeIfNew(code)
                    consumed.add(code)
                }
            }
            // Now consume any queued items, skipping ones we just handled
            consumePendingFromPrefs(consumed)
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
                title = { Text(stringResource(R.string.title_scanned_items)) },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                            prefs.edit {
                                val removedCodes =
                                    scannedParts.filter { it.isSelected }.map { it.part.fullCode }
                                // Remove per-item data for the selected items
                                removedCodes.forEach { code ->
                                    remove("${code}_imageUri")
                                    remove("${code}_note")
                                }
                                // Also remove these codes from the pending_scans queue
                                val pendingJson = prefs.getString("pending_scans", null)
                                if (!pendingJson.isNullOrBlank()) {
                                    try {
                                        val arr = org.json.JSONArray(pendingJson)
                                        val kept = org.json.JSONArray()
                                        for (i in 0 until arr.length()) {
                                            val v = arr.optString(i)
                                            if (!removedCodes.contains(v)) kept.put(v)
                                        }
                                        putString(
                                            "pending_scans",
                                            if (kept.length() == 0) null else kept.toString()
                                        )
                                    } catch (_: Exception) { /* ignore */
                                    }
                                }
                            }
                            val updatedList = scannedParts.filterNot { it.isSelected }.toMutableList()
                            scannedParts.clear()
                            scannedParts.addAll(updatedList)
                            saveParts(context, scannedParts.map { it.part })
                            selectionMode = false
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
                        }
                        IconButton(onClick = {
                            // Deselect all items and exit selection mode
                            val updatedList = scannedParts.map { it.copy(isSelected = false) }
                            scannedParts.clear()
                            scannedParts.addAll(updatedList)
                            selectionMode = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_selection))
                        }
                    } else {
                        // 3-dot menu
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_settings)) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(AppDestinations.SETTINGS_SCREEN)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_clear_all)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showClearDialog = true
                                    selectionMode = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_about)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = { showMenu = false; showInfoDialog = true }
                            )
                        }
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
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.scan))
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
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.search_serials_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )
            if (scannedParts.isEmpty()) {
                Text(stringResource(R.string.no_items_scanned), textAlign = TextAlign.Center)
                if (!cameraPermissionState.status.isGranted) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.camera_permission_required), textAlign = TextAlign.Center)
                }
            } else {
                val visibleParts by remember(scannedParts, searchQuery) {
                    derivedStateOf {
                        val tokens = searchQuery
                            .split(',', ';', ' ', '\n', '\t')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        if (tokens.isNotEmpty()) {
                            scannedParts.filter {
                                val serial = parseScannedCode(it.part.fullCode)?.serialNumber ?: ""
                                tokens.any { tok -> serial.contains(tok, ignoreCase = true) }
                            }
                        } else scannedParts
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visibleParts.sortedByDescending { it.part.timestamp }) { selectablePart ->
                        val part = selectablePart.part
                        val parsed = parseScannedCode(part.fullCode)
                        val formattedDate = remember(part.timestamp) {
                            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                .format(Date(part.timestamp))
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionMode) {
                                                selectablePart.isSelected = !selectablePart.isSelected
                                            } else {
                                                navController.navigate("${AppDestinations.DETAIL_SCREEN}/${part.fullCode}/${part.timestamp}")
                                            }
                                        },
                                        onLongClick = {
                                            selectionMode = true
                                            selectablePart.isSelected = true
                                        }
                                    )
                                    .padding(12.dp)
                                    .drawBehind {
                                        if (selectablePart.isSelected) {
                                            drawRect(
                                                color = Color(0xFFBBDEFB)
                                            )
                                            drawRect(
                                                color = Color(0xFF1976D2),
                                                style = Stroke(width = 4f)
                                            )
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (part.imageUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(part.imageUri.toUri()),
                                        contentDescription = stringResource(R.string.scanned_item),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.scanned_item),
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "${stringResource(R.string.type_label)}: ${parsed?.typeCode ?: stringResource(
                                            R.string.unknown)}"
                                    )
                                    Text(
                                        text = "${stringResource(R.string.serial_number_label)}: ${parsed?.serialNumber ?: stringResource(
                                            R.string.unknown)}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${stringResource(R.string.date_label)}: $formattedDate"
                                    )
                                    if (!part.note.isNullOrEmpty()) {
                                        Text(
                                            text = "${stringResource(R.string.note_label)}: ${part.note}",
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = selectablePart.part.ordinal.toString(),
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .background(
                                            color = Color.DarkGray,
                                            shape = CircleShape
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
            title = { Text(stringResource(R.string.clear_confirm_title)) },
            text = { Text(stringResource(R.string.clear_confirm_text)) },
            confirmButton = {
                Button(onClick = {
                    showClearDialog = false
                    scannedParts.clear()
                    val allKeys = sharedPreferences.all.keys
                    sharedPreferences.edit {
                        remove("items")
                        putInt("lastOrdinal", 0)
                        remove("pending_scans")
                        allKeys.forEach { key ->
                            if (key.endsWith("_imageUri") || key.endsWith("_note")) {
                                remove(key)
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                Button(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Info dialog
    if (showInfoDialog) {
        val appVersion = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?: "N/A"
        } catch (_: PackageManager.NameNotFoundException) {
            "N/A"
        }
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.about_title)) },
            text = { Text(stringResource(R.string.about_text, appVersion)) },
            confirmButton = {
                Button(onClick = { showInfoDialog = false }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // Delete item confirmation dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_item_title)) },
            text = { Text(stringResource(R.string.delete_item_text)) },
            confirmButton = {
                Button(onClick = {
                    scannedParts.removeAll { it.part == itemToDelete }
                    saveParts(context, scannedParts.map { it.part })
                    val part = itemToDelete
                    if (part != null) {
                        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                        prefs.edit {
                            remove("${part.fullCode}_imageUri")
                            remove("${part.fullCode}_note")
                            val pendingJson = prefs.getString("pending_scans", null)
                            if (!pendingJson.isNullOrBlank()) {
                                try {
                                    val arr = org.json.JSONArray(pendingJson)
                                    val kept = org.json.JSONArray()
                                    for (i in 0 until arr.length()) {
                                        val v = arr.optString(i)
                                        if (v != part.fullCode) kept.put(v)
                                    }
                                    putString(
                                        "pending_scans",
                                        if (kept.length() == 0) null else kept.toString()
                                    )
                                } catch (_: Exception) { /* ignore */
                                }
                            }
                        }
                    }
                    itemToDelete = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                Button(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Duplicates found dialog (for already scanned codes)
    if (showDuplicateDialog) {
        val codesForDisplay = duplicateCodes.joinToString(separator = "\n") { code ->
            val serial = parseScannedCode(code)?.serialNumber
            if (serial.isNullOrBlank()) code else serial
        }

        AlertDialog(
            onDismissRequest = {
                showDuplicateDialog = false
                duplicateCodes.clear()
            },
            title = { Text("Already scanned") },
            text = { Text(codesForDisplay) },
            confirmButton = {
                Button(onClick = {
                    showDuplicateDialog = false
                    duplicateCodes.clear()
                }) {
                    Text("OK")
                }
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
            title = { Text(stringResource(R.string.camera_permission_title)) },
            text = { Text(stringResource(R.string.camera_permission_text)) },
            confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.allow)) } },
            dismissButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun PermissionSettingsDialog(show: Boolean, onDismiss: () -> Unit, context: Context) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.permission_required_title)) },
            text = { Text(stringResource(R.string.permission_required_text)) },
            confirmButton = {
                Button(onClick = {
                    onDismiss()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        )
    }
}


// Helper to save parts to SharedPreferences as JSON
private fun saveParts(context: Context, parts: List<ScannedPart>) {
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    val json = Gson().toJson(parts.toTypedArray())
    prefs.edit { putString("items", json) }
}

// --- Data classes and helpers for scanned parts ---
data class ScannedPart(
    val fullCode: String,
    val timestamp: Long,
    val imageUri: String? = null,
    val note: String? = null,
    val ordinal: Int = 0
)

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val mockNavController = rememberNavController()
    val mockItems = remember {
        mutableStateListOf(
            SelectablePart(ScannedPart("TYPEA12345678", System.currentTimeMillis() - 100000, note = "This is a note for item 1.")),
            SelectablePart(ScannedPart("TYPEB87654321", System.currentTimeMillis() - 200000, imageUri = "https://via.placeholder.com/150")),
            SelectablePart(ScannedPart("TYPEC55555555", System.currentTimeMillis() - 300000, note = "Another note here.", imageUri = "https://via.placeholder.com/150"))
        )
    }
    MainScreen(navController = mockNavController, initialItems = mockItems)
}
