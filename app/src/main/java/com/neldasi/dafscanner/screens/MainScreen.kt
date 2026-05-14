
@file:OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
package com.neldasi.dafscanner.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.neldasi.dafscanner.R
import com.neldasi.dafscanner.data.ScannedPart
import com.neldasi.dafscanner.extras.ScanStorage
import com.neldasi.dafscanner.extras.isRunningOnEmulator
import com.neldasi.dafscanner.extras.parseScannedCode
import com.neldasi.dafscanner.navigation.CameraRoute
import com.neldasi.dafscanner.navigation.DetailRoute
import com.neldasi.dafscanner.navigation.NavKeys
import com.neldasi.dafscanner.navigation.SearchListRoute
import com.neldasi.dafscanner.navigation.SettingsRoute
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import com.neldasi.dafscanner.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel(),
) {
    val scannedParts by viewModel.filteredParts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    MainScreenContent(
        navController = navController,
        scannedParts = scannedParts,
        searchQuery = searchQuery,
        onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
        onAddPart = { viewModel.addPart(it) },
        onDeleteSelected = { viewModel.deleteSelected(it) },
        onDeletePart = { viewModel.deletePart(it) },
    ) { viewModel.exportToCsv() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreenContent(
    navController: NavController,
    scannedParts: List<ScannedPart>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddPart: (String) -> Unit,
    onDeleteSelected: (List<String>) -> Unit,
    onDeletePart: (ScannedPart) -> Unit,
    onExportToCsv: suspend () -> File?,
) {
    val context = LocalContext.current
    val sharedPreferences = remember { ScanStorage.prefs(context) }
    val selectedCodes = remember { mutableStateListOf<String>() }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showPermissionRationaleDialog by remember { mutableStateOf(value = false) }
    var showSettingsDialog by remember { mutableStateOf(value = false) }
    var itemToDelete by remember { mutableStateOf<ScannedPart?>(value = null) }
    var selectionMode by remember { mutableStateOf(value = false) }
    var showMenu by remember { mutableStateOf(value = false) }
    val duplicateCodes = remember { mutableStateListOf<String>() }
    var showDuplicateDialog by remember { mutableStateOf(value = false) }
    val scope = rememberCoroutineScope()

    fun addCodeIfNew(code: String) {
        if (code.isBlank()) return
        if (scannedParts.any { it.fullCode == code }) {
            if (!duplicateCodes.contains(code)) duplicateCodes.add(code)
            showDuplicateDialog = true
            return
        }
        onAddPart(code)
    }

    LaunchedEffect(Unit) {
        fun consumePendingFromPrefs(exclude: Set<String>) {
            ScanStorage.consumePendingQueue(sharedPreferences).forEach { code ->
                if (!exclude.contains(code)) {
                    try { addCodeIfNew(code) } catch (_: Exception) {}
                }
            }
        }

        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            val consumed = mutableSetOf<String>()
            backStackEntry.savedStateHandle.remove<String>(NavKeys.SCANNED_RESULT)?.let {
                addCodeIfNew(it)
                consumed.add(it)
            }
            backStackEntry.savedStateHandle.remove<List<String>>("SCANNED_RESULTS")?.let { list ->
                list.forEach {
                    addCodeIfNew(it)
                    consumed.add(it)
                }
            }
            consumePendingFromPrefs(consumed)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.title_scanned_items),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    AnimatedVisibility(
                        visible = selectionMode,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row {
                            IconButton(
                            onClick = {
                                if (selectedCodes.size == scannedParts.size) selectedCodes.clear()
                                else {
                                    selectedCodes.clear()
                                    selectedCodes.addAll(scannedParts.map { it.fullCode })
                                }
                            },
                        ) {
                                Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
                            }
                            IconButton(
                                onClick = {
                                    onDeleteSelected(selectedCodes.toList())
                                    selectedCodes.clear()
                                    selectionMode = false
                                },
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_selected), tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(
                                onClick = {
                                    selectedCodes.clear()
                                    selectionMode = false
                                },
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel_selection))
                            }
                        }
                    }
                    if (!selectionMode) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_selected)) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    if (scannedParts.isNotEmpty()) {
                                        selectionMode = true
                                        selectedCodes.clear()
                                    }
                                },
                                enabled = scannedParts.isNotEmpty()
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.verify_serials_title)) },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(SearchListRoute)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_screen_title)) },
                                leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(SettingsRoute)
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (scannedParts.isEmpty()) {
                    item {
                        EmptyState(isPermissionGranted = cameraPermissionState.status.isGranted)
                    }
                } else {
                    items(scannedParts, key = { it.fullCode }) { part ->
                        PartItem(
                            part = part,
                            isSelected = selectedCodes.contains(part.fullCode),
                            onItemClick = {
                                if (selectionMode) {
                                    if (selectedCodes.contains(part.fullCode)) selectedCodes.remove(part.fullCode)
                                    else selectedCodes.add(part.fullCode)
                                } else {
                                    navController.navigate(DetailRoute(part.fullCode, part.timestamp))
                                }
                            }
                        ) {
                            selectionMode = true
                            selectedCodes.add(part.fullCode)
                        }
                    }
                }
            }

            // Floating Search and Scan Bar
            if (!selectionMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { 
                                Text(
                                    stringResource(R.string.search_serials_hint),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                ) 
                            },
                            modifier = Modifier
                                .weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        FloatingActionButton(
                            onClick = {
                                if (isRunningOnEmulator()) {
                                    // Simulate a valid code (Type: 2150001, Supplier: 88429, random Serial: 6 digits)
                                    val simulatedCode = "215000188429${(100000..999999).random()}"
                                    addCodeIfNew(simulatedCode)
                                } else {
                                    when (cameraPermissionState.status) {
                                        PermissionStatus.Granted -> navController.navigate(CameraRoute)
                                        is PermissionStatus.Denied -> {
                                            if (cameraPermissionState.status.shouldShowRationale) showPermissionRationaleDialog = true
                                            else cameraPermissionState.launchPermissionRequest()
                                        }
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan")
                        }
                    }
                }
            }
        }
    }

    // Dialogs...
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_item_title)) },
            text = { Text(stringResource(R.string.delete_item_text)) },
            confirmButton = {
                TextButton(onClick = { onDeletePart(itemToDelete!!); itemToDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    DuplicateDialog(
        show = showDuplicateDialog,
        duplicateCodes = duplicateCodes,
        onDismiss = { showDuplicateDialog = false; duplicateCodes.clear() },
        scope = scope,
        context = context
    )

    PermissionRationaleDialog(
        show = showPermissionRationaleDialog,
        onDismiss = { showPermissionRationaleDialog = false },
        onConfirm = { showPermissionRationaleDialog = false; cameraPermissionState.launchPermissionRequest() }
    )

    PermissionSettingsDialog(show = showSettingsDialog, onDismiss = { showSettingsDialog = false }, context = context)
}

@Composable
private fun EmptyState(isPermissionGranted: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        if (!isPermissionGranted) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.camera_permission_required),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PartItem(
    part: ScannedPart,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    val parsed = remember(part.fullCode) { parseScannedCode(part.fullCode) }
    val formattedDate = remember(part.timestamp) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(part.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) strokeBorder(2.dp, MaterialTheme.colorScheme.primary) 
                 else strokeBorder(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onItemClick, onLongClick = onItemLongClick)
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (part.imageUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(part.imageUri.toUri()),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.Center).size(32.dp)
                    )
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "HEX: ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = parsed?.serialNumber ?: "Unknown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "DEC: ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF388E3C)
                    )
                    Text(
                        text = parsed?.decSerial ?: "Unknown",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = parsed?.typeCode ?: "Unknown",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
                if (!part.note.isNullOrEmpty()) {
                    Text(
                        text = "Has Note",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun strokeBorder(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)

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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val mockNavController = rememberNavController()
    val mockItems = listOf(
        ScannedPart("TYPEA12345678", System.currentTimeMillis() - 100000, note = "This is a note for item 1."),
        ScannedPart("TYPEB87654321", System.currentTimeMillis() - 200000, imageUri = "https://via.placeholder.com/150"),
        ScannedPart("TYPEC55555555", System.currentTimeMillis() - 300000, note = "Another note here.", imageUri = "https://via.placeholder.com/150")
    )
    JetpackComposeTheme {
        MainScreenContent(
            navController = mockNavController,
            scannedParts = mockItems,
            searchQuery = "",
            onSearchQueryChange = {},
            onAddPart = {},
            onDeleteSelected = {},
            onDeletePart = {},
        ) { null }
    }
}
