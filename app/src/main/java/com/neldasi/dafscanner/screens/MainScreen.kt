
@file:OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
package com.neldasi.dafscanner.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.*
import com.neldasi.dafscanner.R
import com.neldasi.dafscanner.data.ScannedPart
import com.neldasi.dafscanner.extras.ScanStorage
import com.neldasi.dafscanner.extras.parseScannedCode
import com.neldasi.dafscanner.navigation.CameraRoute
import com.neldasi.dafscanner.navigation.DetailRoute
import com.neldasi.dafscanner.navigation.NavKeys
import com.neldasi.dafscanner.navigation.SettingsRoute
import com.neldasi.dafscanner.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel()
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
        onExportToCsv = { viewModel.exportToCsv() }
    )
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
    onExportToCsv: suspend () -> File?
) {
    val context = LocalContext.current
    val sharedPreferences = remember { ScanStorage.prefs(context) }
    val selectedCodes = remember { mutableStateListOf<String>() }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ScannedPart?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    val duplicateCodes = remember { mutableStateListOf<String>() }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        fun addCodeIfNew(code: String) {
            if (code.isBlank()) return
            if (scannedParts.any { it.fullCode == code }) {
                if (!duplicateCodes.contains(code)) duplicateCodes.add(code)
                showDuplicateDialog = true
                return
            }
            onAddPart(code)
        }

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
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    AnimatedVisibility(
                        visible = selectionMode,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row {
                            IconButton(onClick = {
                                if (selectedCodes.size == scannedParts.size) selectedCodes.clear()
                                else {
                                    selectedCodes.clear()
                                    selectedCodes.addAll(scannedParts.map { it.fullCode })
                                }
                            }) {
                                Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
                            }
                            IconButton(onClick = {
                                onDeleteSelected(selectedCodes.toList())
                                selectedCodes.clear()
                                selectionMode = false
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_selected), tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = {
                                selectedCodes.clear()
                                selectionMode = false
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel_selection))
                            }
                        }
                    }
                    if (!selectionMode) {
                        IconButton(onClick = {
                            scope.launch {
                                onExportToCsv()?.let { file ->
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooser = Intent.createChooser(intent, "Export CSV")
                                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(chooser)
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.IosShare, contentDescription = "Export CSV")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            BottomActionBar(
                hasItems = scannedParts.isNotEmpty(),
                selectionMode = selectionMode,
                onTrashClick = {
                    if (!selectionMode && scannedParts.isNotEmpty()) {
                        selectionMode = true
                        selectedCodes.clear()
                    }
                },
                onScanClick = {
                    if (!selectionMode) {
                        when (cameraPermissionState.status) {
                            PermissionStatus.Granted -> navController.navigate(CameraRoute)
                            is PermissionStatus.Denied -> {
                                if (cameraPermissionState.status.shouldShowRationale) showPermissionRationaleDialog = true
                                else cameraPermissionState.launchPermissionRequest()
                            }
                        }
                    }
                },
                onSettingsClick = { if (!selectionMode) navController.navigate(SettingsRoute) }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text(stringResource(R.string.search_serials_hint)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }

                if (scannedParts.isEmpty()) {
                    item {
                        EmptyState(isPermissionGranted = cameraPermissionState.status.isGranted)
                    }
                } else {
                    items(scannedParts, key = { it.fullCode }) { part ->
                        PartItem(
                            part = part,
                            isSelected = selectedCodes.contains(part.fullCode),
                            selectionMode = selectionMode,
                            onItemClick = {
                                if (selectionMode) {
                                    if (selectedCodes.contains(part.fullCode)) selectedCodes.remove(part.fullCode)
                                    else selectedCodes.add(part.fullCode)
                                } else {
                                    navController.navigate(DetailRoute(part.fullCode, part.timestamp))
                                }
                            },
                            onItemLongClick = {
                                selectionMode = true
                                selectedCodes.add(part.fullCode)
                            }
                        )
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
    selectionMode: Boolean,
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
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) strokeBorder(1.dp, MaterialTheme.colorScheme.primary) else null
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
                        tint = MaterialTheme.colorScheme.primary,
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
                Text(
                    text = parsed?.serialNumber ?: "Unknown Serial",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    // Allowing wrapping for accessibility (large fonts)
                    softWrap = true
                )
                Text(
                    text = "${parsed?.typeCode ?: "Unknown"} • $formattedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true
                )
                if (!part.note.isNullOrEmpty()) {
                    Text(
                        text = part.note,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                        softWrap = true
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                Text(
                    text = "#${part.ordinal}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun strokeBorder(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)

@Composable
private fun BottomActionBar(
    hasItems: Boolean,
    selectionMode: Boolean,
    onTrashClick: () -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).height(72.dp),
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onTrashClick,
                    enabled = hasItems && !selectionMode
                ) {
                    Icon(
                        if (selectionMode) Icons.Rounded.Delete else Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = when {
                            selectionMode -> MaterialTheme.colorScheme.primary
                            hasItems -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }

                FloatingActionButton(
                    onClick = onScanClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(28.dp))
                }

                IconButton(onClick = onSettingsClick, enabled = !selectionMode) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = if (selectionMode) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val mockNavController = rememberNavController()
    val mockItems = listOf(
        ScannedPart("TYPEA12345678", System.currentTimeMillis() - 100000, note = "This is a note for item 1.", ordinal = 1),
        ScannedPart("TYPEB87654321", System.currentTimeMillis() - 200000, imageUri = "https://via.placeholder.com/150", ordinal = 2),
        ScannedPart("TYPEC55555555", System.currentTimeMillis() - 300000, note = "Another note here.", imageUri = "https://via.placeholder.com/150", ordinal = 3)
    )
    MaterialTheme {
        MainScreenContent(
            navController = mockNavController,
            scannedParts = mockItems,
            searchQuery = "",
            onSearchQueryChange = {},
            onAddPart = {},
            onDeleteSelected = {},
            onDeletePart = {},
            onExportToCsv = { null }
        )
    }
}
