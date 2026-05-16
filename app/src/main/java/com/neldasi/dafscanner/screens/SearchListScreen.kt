package com.neldasi.dafscanner.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import com.neldasi.dafscanner.R
import com.neldasi.dafscanner.navigation.CameraRoute
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import com.neldasi.dafscanner.viewmodels.SearchItem
import com.neldasi.dafscanner.viewmodels.SearchListViewModel
import com.neldasi.dafscanner.viewmodels.SearchSortOption
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchListScreen(
    navController: NavController,
    viewModel: SearchListViewModel = viewModel(),
) {
    val context = LocalContext.current
    val searchItems by viewModel.filteredItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasMoreItems by viewModel.hasMoreItems.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val scannedCount by viewModel.scannedCount.collectAsStateWithLifecycle()
    
    var showDeleteConfirmation by remember { mutableStateOf(value = false) }
    var showShareOptions by remember { mutableStateOf(value = false) }

    val statusFound = stringResource(R.string.status_found)
    val statusMissing = stringResource(R.string.status_missing)
    val shareChooserTitle = stringResource(R.string.share_csv_chooser)
    val summaryTitle = stringResource(R.string.verification_summary_title)
    val progressLabel = stringResource(R.string.scanned_progress)
    val foundSectionLabel = stringResource(R.string.found_section)
    val missingSectionLabel = stringResource(R.string.missing_section)
    val summarySubject = stringResource(R.string.verification_summary_subject)
    val summaryChooserTitle = stringResource(R.string.share_summary_chooser)

    LaunchedEffect(Unit) {
        viewModel.initStorage(context)
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { viewModel.loadCsv(context, it) }
    }

    LaunchedEffect(searchItems) {
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
        searchQuery = searchQuery,
        onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
        sortOption = sortOption,
        onSortOptionChange = { viewModel.onSortOptionChange(it) },
        isLoading = isLoading,
        onLoadMore = { viewModel.loadMore() },
        showDeleteConfirmation = showDeleteConfirmation,
        onShowDeleteConfirmationChange = { showDeleteConfirmation = it },
        showShareOptions = showShareOptions,
        onShowShareOptionsChange = { showShareOptions = it },
        onBackClick = { navController.popBackStack() },
        onClearListClick = { viewModel.clearList(context) },
        onScanClick = { navController.navigate(CameraRoute(isVerifyMode = true)) },
        onImportCsvClick = {
            if (!isLoading) {
                csvPickerLauncher.launch("text/comma-separated-values")
            }
        },
        hasMoreItems = hasMoreItems,
        totalCount = totalCount,
        scannedCount = scannedCount,
        onShareCsv = {
            if (searchItems.isEmpty()) return@SearchListContent
            
            val sb = StringBuilder()
            sb.append("Order;Status;HEX;DEC;Type;Scan Time\n")
            
            val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            searchItems.forEach { item ->
                val isFound = item.scanTimestamp != null
                val status = if (isFound) statusFound else statusMissing
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
                context.startActivity(Intent.createChooser(shareIntent, shareChooserTitle))
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
            sb.append(summaryTitle + "\n")
            sb.append(progressLabel.format(foundItems.size, total) + "\n\n")
            
            if (foundItems.isNotEmpty()) {
                sb.append(foundSectionLabel.format(foundItems.size) + "\n")
                foundItems.forEach { item ->
                    val time = if (item.scanTimestamp != null) timeFormatter.format(Date(item.scanTimestamp)) else "-"
                    sb.append("${item.scanOrder}. HEX: ${item.serialNumber} (DEC: ${item.decSerial}) - ${item.typeCode} - $time\n")
                }
                sb.append("\n")
            }
            
            if (missingItems.isNotEmpty()) {
                sb.append(missingSectionLabel.format(missingItems.size) + "\n")
                missingItems.forEach { item ->
                    sb.append("- HEX: ${item.serialNumber} (DEC: ${item.decSerial}) - ${item.typeCode}\n")
                }
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, summarySubject.format(foundItems.size, total))
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }
            context.startActivity(Intent.createChooser(shareIntent, summaryChooserTitle))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchListContent(
    searchItems: List<SearchItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOption: SearchSortOption,
    onSortOptionChange: (SearchSortOption) -> Unit,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    showDeleteConfirmation: Boolean,
    onShowDeleteConfirmationChange: (Boolean) -> Unit,
    showShareOptions: Boolean,
    onShowShareOptionsChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onClearListClick: () -> Unit,
    onScanClick: () -> Unit,
    onImportCsvClick: () -> Unit,
    hasMoreItems: Boolean,
    totalCount: Int,
    scannedCount: Int,
    onShareCsv: () -> Unit,
    onShareSummary: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var showSortMenu by remember { mutableStateOf(false) }

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
                    if (searchItems.isNotEmpty() || searchQuery.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortMenuItem(SearchSortOption.DEFAULT, R.string.sort_default, sortOption, onSortOptionChange) { showSortMenu = false }
                                SortMenuItem(SearchSortOption.MACHINE, R.string.sort_machine, sortOption, onSortOptionChange) { showSortMenu = false }
                                SortMenuItem(SearchSortOption.TYPE, R.string.sort_type, sortOption, onSortOptionChange) { showSortMenu = false }
                                SortMenuItem(SearchSortOption.FOUND_TIME, R.string.sort_found_time, sortOption, onSortOptionChange) { showSortMenu = false }
                                SortMenuItem(SearchSortOption.PRODUCTION_TIME, R.string.sort_production_time, sortOption, onSortOptionChange) { showSortMenu = false }
                            }
                        }
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
            if (searchItems.isNotEmpty() || searchQuery.isNotEmpty()) {
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (searchItems.isEmpty() && searchQuery.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
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
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading
                            ) {
                                Icon(Icons.Rounded.FileOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.import_csv))
                            }
                        }
                    }
                } else {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_items)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchItems, key = { it.serialNumber }) { item ->
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
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                stringResource(R.string.hex_prefix),
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isScanned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                                            )
                                                            Text(
                                                                text = item.serialNumber,
                                                                style = MaterialTheme.typography.headlineMedium,
                                                                fontWeight = FontWeight.Black,
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
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isScanned) Color(0xFFD32F2F).copy(alpha = 0.8f) else MaterialTheme.colorScheme.secondary
                                                            )
                                                        }
                                                    }

                                                    Column(
                                                        horizontalAlignment = Alignment.End,
                                                        verticalArrangement = Arrangement.Center
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

                                                        if (isScanned) {
                                                            Spacer(Modifier.height(4.dp))
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = stringResource(R.string.found_at_label),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                                                )
                                                                Spacer(Modifier.width(4.dp))
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

                                                HorizontalDivider(
                                                    modifier = Modifier.padding(vertical = 8.dp),
                                                    thickness = 0.5.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )

                                                if (!item.machine.isNullOrBlank()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Rounded.PrecisionManufacturing,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = item.machine,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }

                                                if (!item.outputMaterial.isNullOrBlank()) {
                                                    Row(
                                                        modifier = Modifier.padding(top = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.Inventory2,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = item.outputMaterial,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1
                                                        )
                                                    }
                                                } else if (item.typeCode != "UNKNOWN") {
                                                    Row(
                                                        modifier = Modifier.padding(top = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.Inventory2,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = item.typeCode,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                val dateTimeText = remember(item) {
                                                    when {
                                                        item.startDate != null && item.startTime != null -> {
                                                            val start = "${item.startDate} ${item.startTime}"
                                                            if (item.completeTime != null) {
                                                                if (item.startDate == item.completeDate || item.completeDate == null) {
                                                                    "$start - ${item.completeTime}"
                                                                } else {
                                                                    "$start - ${item.completeDate} ${item.completeTime}"
                                                                }
                                                            } else {
                                                                start
                                                            }
                                                        }
                                                        else -> null
                                                    }
                                                }

                                                if (dateTimeText != null) {
                                                    Spacer(Modifier.height(6.dp))
                                                    Surface(
                                                        color = Color(0xFFD32F2F).copy(alpha = 0.08f),
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFD32F2F).copy(alpha = 0.2f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.Schedule,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(14.dp),
                                                                tint = Color(0xFFD32F2F)
                                                            )
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(
                                                                text = stringResource(R.string.production_label),
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color(0xFFD32F2F)
                                                            )
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(
                                                                text = dateTimeText,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFFD32F2F)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            if (hasMoreItems) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                    LaunchedEffect(Unit) {
                                        onLoadMore()
                                    }
                                }
                            }
                        }

                        if (searchItems.isNotEmpty() || searchQuery.isNotEmpty()) {
                            val scannedColor = if (scannedCount == totalCount && totalCount > 0) Color(0xFF388E3C) else MaterialTheme.colorScheme.secondary

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
                                                append(scannedCount.toString())
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

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.loading))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    option: SearchSortOption,
    labelRes: Int,
    currentOption: SearchSortOption,
    onOptionChange: (SearchSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = {
            onOptionChange(option)
            onDismiss()
        },
        trailingIcon = {
            if (currentOption == option) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Preview(showBackground = true, apiLevel = 36)
@Composable
fun SearchListEmptyPreview() {
    JetpackComposeTheme {
        SearchListContent(
            searchItems = emptyList(),
            searchQuery = "",
            onSearchQueryChange = {},
            sortOption = SearchSortOption.DEFAULT,
            onSortOptionChange = {},
            isLoading = false,
            onLoadMore = {},
            showDeleteConfirmation = false,
            onShowDeleteConfirmationChange = {},
            showShareOptions = false,
            onShowShareOptionsChange = {},
            onBackClick = {},
            onClearListClick = {},
            onScanClick = {},
            onImportCsvClick = {},
            hasMoreItems = false,
            totalCount = 0,
            scannedCount = 0,
            onShareCsv = {},
            onShareSummary = {}
        )
    }
}

@Preview(showBackground = true, apiLevel = 36)
@Composable
fun SearchListWithDataPreview() {
    val mockItems = listOf(
        SearchItem(
            typeCode = "2261325", 
            serialNumber = "01C821", 
            decSerial = "116769", 
            scanTimestamp = System.currentTimeMillis(), 
            scanOrder = 1,
            machine = "19602 (JUNKER CNC)",
            outputMaterial = "2261325 (MX13 MY21)",
            startDate = "11-5-2023",
            startTime = "00:02:09",
            completeTime = "00:17:07"
        ),
        SearchItem(
            typeCode = "2002046", 
            serialNumber = "01C822", 
            decSerial = "116770",
            machine = "19602 (JUNKER CNC)",
            outputMaterial = "2002046 (NOKKENAS)",
            startDate = "11-5-2023",
            startTime = "00:17:20",
            completeTime = "00:28:56"
        ),
        SearchItem(
            typeCode = "TYPE789", 
            serialNumber = "01C823", 
            decSerial = "116771", 
            scanTimestamp = System.currentTimeMillis(), 
            scanOrder = 2,
            machine = "19602 (JUNKER CNC)",
            outputMaterial = "TYPE789 (MATERIAL)",
            startDate = "10-5-2023",
            startTime = "23:33:17",
            completeDate = "11-5-2023",
            completeTime = "00:01:55"
        )
    )
    JetpackComposeTheme {
        SearchListContent(
            searchItems = mockItems,
            searchQuery = "",
            onSearchQueryChange = {},
            sortOption = SearchSortOption.DEFAULT,
            onSortOptionChange = {},
            isLoading = false,
            onLoadMore = {},
            showDeleteConfirmation = false,
            onShowDeleteConfirmationChange = {},
            showShareOptions = false,
            onShowShareOptionsChange = {},
            onBackClick = {},
            onClearListClick = {},
            onScanClick = {},
            onImportCsvClick = {},
            hasMoreItems = false,
            totalCount = mockItems.size,
            scannedCount = mockItems.count { it.scanTimestamp != null },
            onShareCsv = {},
            onShareSummary = {}
        )
    }
}
