package com.neldasi.dafscanner.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import com.neldasi.dafscanner.extras.isRunningOnEmulator
import com.neldasi.dafscanner.navigation.CameraRoute
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import com.neldasi.dafscanner.viewmodels.SearchItem
import com.neldasi.dafscanner.viewmodels.SearchListViewModel
import androidx.compose.ui.res.stringResource
import com.neldasi.dafscanner.R
import java.io.File

@Composable
fun SearchListScreen(
    navController: NavController,
    viewModel: SearchListViewModel = viewModel(),
) {
    val context = LocalContext.current
    val searchItems by viewModel.searchItems.collectAsStateWithLifecycle()
    var showDeleteConfirmation by remember { mutableStateOf(value = false) }
    var showShareOptions by remember { mutableStateOf(value = false) }

    LaunchedEffect(Unit) {
        viewModel.initStorage(context)
    }

    Log.d("SearchListScreen", "UI Update: ${searchItems.count { it.scanTimestamp != null }} / ${searchItems.size}")

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { viewModel.loadCsv(context, it) }
    }

    LaunchedEffect(searchItems) {
        // Keeping this for non-shared VM cases or as backup
        navController.currentBackStackEntry?.savedStateHandle?.set("SERIAL_LIST", searchItems.map { it.serialNumber })
        navController.currentBackStackEntry?.savedStateHandle?.set("SCANNED_SERIALS", searchItems.filter { it.scanTimestamp != null }.map { it.serialNumber })
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearResult()
        }
    }

    SearchListContent(
        searchItems = searchItems,
        showDeleteConfirmation = showDeleteConfirmation,
        onShowDeleteConfirmationChange = { showDeleteConfirmation = it },
        showShareOptions = showShareOptions,
        onShowShareOptionsChange = { showShareOptions = it },
        onBackClick = { navController.popBackStack() },
        onClearListClick = { viewModel.clearList(context) },
        onScanClick = { navController.navigate(CameraRoute(isVerifyMode = true)) },
        onImportCsvClick = {
            if (isRunningOnEmulator()) {
                viewModel.loadMockData(context)
            } else {
                csvPickerLauncher.launch("text/comma-separated-values")
            }
        },
        onShareCsv = {
            if (searchItems.isEmpty()) return@SearchListContent
            
            val sb = StringBuilder()
            sb.append("Order;Status;HEX;DEC;Type;Scan Time\n")
            
            val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            searchItems.forEach { item ->
                val isFound = item.scanTimestamp != null
                val status = if (isFound) context.getString(R.string.status_found) else context.getString(R.string.status_missing)
                val order = item.scanOrder?.toString() ?: "-"
                val time = if (item.scanTimestamp != null) timeFormatter.format(Date(item.scanTimestamp)) else "-"
                
                sb.append("$order;$status;${item.serialNumber};${item.decSerial};${item.typeCode};$time\n")
            }
            
            try {
                val cachePath = File(context.cacheDir, "exports")
                cachePath.mkdirs()
                val file = File(cachePath, "verification_results.csv")
                file.writeText(sb.toString())
                
                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_csv_chooser)))
            } catch (e: Exception) {
                Log.e("SearchListScreen", "Error sharing CSV file", e)
            }
        },
        onShareSummary = {
            if (searchItems.isEmpty()) return@SearchListContent
            
            val total = searchItems.size
            val foundItems = searchItems.filter { it.scanTimestamp != null }.sortedBy { it.scanOrder }
            val missingItems = searchItems.filter { it.scanTimestamp == null }
            
            val sb = StringBuilder()
            val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            sb.append(context.getString(R.string.verification_summary_title) + "\n")
            sb.append(context.getString(R.string.scanned_progress, foundItems.size, total) + "\n\n")
            
            if (foundItems.isNotEmpty()) {
                sb.append(context.getString(R.string.found_section, foundItems.size) + "\n")
                foundItems.forEach { item ->
                    val time = if (item.scanTimestamp != null) timeFormatter.format(Date(item.scanTimestamp)) else "-"
                    sb.append("${item.scanOrder}. HEX: ${item.serialNumber} (DEC: ${item.decSerial}) - ${item.typeCode} - $time\n")
                }
                sb.append("\n")
            }
            
            if (missingItems.isNotEmpty()) {
                sb.append(context.getString(R.string.missing_section, missingItems.size) + "\n")
                missingItems.forEach { item ->
                    sb.append("- HEX: ${item.serialNumber} (DEC: ${item.decSerial}) - ${item.typeCode}\n")
                }
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.verification_summary_subject, foundItems.size, total))
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_summary_chooser)))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchListContent(
    searchItems: List<SearchItem>,
    showDeleteConfirmation: Boolean,
    onShowDeleteConfirmationChange: (Boolean) -> Unit,
    showShareOptions: Boolean,
    onShowShareOptionsChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onClearListClick: () -> Unit,
    onScanClick: () -> Unit,
    onImportCsvClick: () -> Unit,
    onShareCsv: () -> Unit,
    onShareSummary: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { onShowDeleteConfirmationChange(false) },
            title = { Text(stringResource(R.string.clear_list_title)) },
            text = { Text(stringResource(R.string.clear_list_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearListClick()
                        onShowDeleteConfirmationChange(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowDeleteConfirmationChange(false) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showShareOptions) {
        AlertDialog(
            onDismissRequest = { onShowShareOptionsChange(false) },
            title = { Text(stringResource(R.string.share_results_title)) },
            text = { Text(stringResource(R.string.share_results_text)) },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onShareSummary()
                            onShowShareOptionsChange(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.summary_report))
                    }
                    Button(
                        onClick = {
                            onShareCsv()
                            onShowShareOptionsChange(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Rounded.TableChart, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.csv_excel_format))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { onShowShareOptionsChange(false) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.verify_serials_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (searchItems.isNotEmpty()) {
                        IconButton(onClick = { onShowShareOptionsChange(true) }) {
                            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share))
                        }
                        IconButton(onClick = { onShowDeleteConfirmationChange(true) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (searchItems.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onScanClick,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (searchItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_list_loaded),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.import_csv_instruction),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onImportCsvClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.import_csv))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchItems) { item ->
                            val isScanned = item.scanTimestamp != null
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isScanned) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                                                     else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isScanned) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) 
                                         else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if ((isScanned && item.scanOrder != null)) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondary,
                                            shape = CircleShape,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = item.scanOrder.toString(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    } else {
                                        Icon(
                                            Icons.Rounded.Tag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    
                                    Spacer(Modifier.width(12.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                stringResource(R.string.hex_prefix),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isScanned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = item.serialNumber,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isScanned) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                stringResource(R.string.dec_prefix),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isScanned) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f) else Color(0xFF388E3C)
                                            )
                                            Text(
                                                text = item.decSerial,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isScanned) Color(0xFFD32F2F).copy(alpha = 0.8f) else MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            color = if (isScanned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = item.typeCode,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isScanned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (isScanned) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = timeFormatter.format(Date(item.scanTimestamp)),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (searchItems.isNotEmpty()) {
                val foundCount = searchItems.count { it.scanTimestamp != null }
                val totalCount = searchItems.size
                val scannedColor = if (foundCount == totalCount) Color(0xFF388E3C) else MaterialTheme.colorScheme.secondary

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)) {
                                    append(stringResource(R.string.progress_label))
                                }
                                withStyle(style = SpanStyle(color = scannedColor, fontWeight = FontWeight.ExtraBold)) {
                                    append(foundCount.toString())
                                }
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)) {
                                    append(" / $totalCount")
                                }
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, apiLevel = 36)
@Composable
fun SearchListEmptyPreview() {
    JetpackComposeTheme {
        SearchListContent(
            searchItems = emptyList(),
            showDeleteConfirmation = false,
            onShowDeleteConfirmationChange = {},
            showShareOptions = false,
            onShowShareOptionsChange = {},
            onBackClick = {},
            onClearListClick = {},
            onScanClick = {},
            onImportCsvClick = {},
            onShareCsv = {},
            onShareSummary = {}
        )
    }
}

@Preview(showBackground = true, apiLevel = 36)
@Composable
fun SearchListWithDataPreview() {
    JetpackComposeTheme {
        SearchListContent(
            searchItems = listOf(
                SearchItem("TYPE123", "01C821", "116769", System.currentTimeMillis(), 1),
                SearchItem("TYPE456", "01C822", "116770"),
                SearchItem("TYPE789", "01C823", "116771", System.currentTimeMillis(), 2)
            ),
            showDeleteConfirmation = false,
            onShowDeleteConfirmationChange = {},
            showShareOptions = false,
            onShowShareOptionsChange = {},
            onBackClick = {},
            onClearListClick = {},
            onScanClick = {},
            onImportCsvClick = {},
            onShareCsv = {},
            onShareSummary = {}
        )
    }
}

@Preview(showBackground = true, apiLevel = 36)
@Composable
fun SearchListDeleteConfirmationPreview() {
    JetpackComposeTheme {
        SearchListContent(
            searchItems = listOf(
                SearchItem("TYPE123", "01C821", "116769", System.currentTimeMillis(), 1),
                SearchItem("TYPE456", "01C822", "116770")
            ),
            showDeleteConfirmation = true,
            onShowDeleteConfirmationChange = {},
            showShareOptions = false,
            onShowShareOptionsChange = {},
            onBackClick = {},
            onClearListClick = {},
            onScanClick = {},
            onImportCsvClick = {},
            onShareCsv = {},
            onShareSummary = {}
        )
    }
}

@Preview(showBackground = true, apiLevel = 36)
@Composable
fun SearchListShareOptionsPreview() {
    JetpackComposeTheme {
        SearchListContent(
            searchItems = listOf(
                SearchItem("TYPE123", "01C821", "116769", System.currentTimeMillis(), 1),
                SearchItem("TYPE456", "01C822", "116770")
            ),
            showDeleteConfirmation = false,
            onShowDeleteConfirmationChange = {},
            showShareOptions = true,
            onShowShareOptionsChange = {},
            onBackClick = {},
            onClearListClick = {},
            onScanClick = {},
            onImportCsvClick = {},
            onShareCsv = {},
            onShareSummary = {}
        )
    }
}
