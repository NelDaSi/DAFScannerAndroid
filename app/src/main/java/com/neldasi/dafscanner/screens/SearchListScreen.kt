package com.neldasi.dafscanner.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.tooling.preview.Preview
import com.neldasi.dafscanner.navigation.CameraRoute
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import com.neldasi.dafscanner.viewmodels.SearchItem
import com.neldasi.dafscanner.viewmodels.SearchListViewModel

@Composable
fun SearchListScreen(
    navController: NavController,
    viewModel: SearchListViewModel = viewModel(),
) {
    val context = LocalContext.current
    val searchItems by viewModel.searchItems.collectAsStateWithLifecycle()

    Log.d("SearchListScreen", "UI Update: ${searchItems.count { it.scanTimestamp != null }} / ${searchItems.size}")

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
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
        onBackClick = { navController.popBackStack() },
        onClearListClick = { viewModel.clearList() },
        onScanClick = { navController.navigate(CameraRoute(isVerifyMode = true)) },
        onImportCsvClick = { csvPickerLauncher.launch("text/comma-separated-values") }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchListContent(
    searchItems: List<SearchItem>,
    onBackClick: () -> Unit,
    onClearListClick: () -> Unit,
    onScanClick: () -> Unit,
    onImportCsvClick: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Clear List?") },
            text = { Text("This will remove all imported serial numbers and reset your progress. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearListClick()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear List")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Verify Serie Numbers")
                        if (searchItems.isNotEmpty()) {
                            Text(
                                "Scanned ${searchItems.count { it.scanTimestamp != null }} / ${searchItems.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (searchItems.isNotEmpty()) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Clear List")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (searchItems.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onScanClick,
                    containerColor = MaterialTheme.colorScheme.primary
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
                        "No list loaded",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Import a CSV file with \"Product ID\" column to start verifying serial numbers.",
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
                        Text("Import CSV")
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchItems) { item ->
                            val isScanned = item.scanTimestamp != null
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isScanned) Color(0xFFD32F2F).copy(alpha = 0.1f) 
                                                     else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = if (isScanned) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFD32F2F)) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isScanned) Icons.Rounded.CheckCircle else Icons.Rounded.Tag,
                                        contentDescription = null,
                                        tint = if (isScanned) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "HEX: ",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isScanned) Color(0xFFD32F2F).copy(alpha = 0.7f) else Color(0xFF1976D2)
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
                                                "DEC: ",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isScanned) Color(0xFFD32F2F).copy(alpha = 0.7f) else Color(0xFF388E3C)
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
                                            color = if (isScanned) Color(0xFFD32F2F).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = item.typeCode,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isScanned) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        
                                        if (isScanned) {
                                            Spacer(Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (item.scanOrder != null) {
                                                    Surface(
                                                        color = Color(0xFFD32F2F),
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(18.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = item.scanOrder.toString(),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(4.dp))
                                                }
                                                Text(
                                                    "FOUND",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFFD32F2F)
                                                )
                                            }
                                            if (item.scanTimestamp != null) {
                                                Text(
                                                    text = timeFormatter.format(Date(item.scanTimestamp)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFD32F2F).copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SearchListEmptyPreview() {
    JetpackComposeTheme {
        SearchListContent(
            searchItems = emptyList(),
            onBackClick = {},
            onClearListClick = {},
            onScanClick = {},
            onImportCsvClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SearchListWithDataPreview() {
    JetpackComposeTheme {
        SearchListContent(
            searchItems = listOf(
                SearchItem("TYPE123", "01C821", "116769", System.currentTimeMillis(), 1),
                SearchItem("TYPE456", "01C822", "116770"),
                SearchItem("TYPE789", "01C823", "116771", System.currentTimeMillis(), 2)
            ),
            onBackClick = {},
            onClearListClick = {},
            onScanClick = {},
            onImportCsvClick = {}
        )
    }
}
