package com.neldasi.dafscanner.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.tooling.preview.Preview
import com.neldasi.dafscanner.navigation.CameraRoute
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import com.neldasi.dafscanner.viewmodels.SearchListViewModel

@Composable
fun SearchListScreen(
    navController: NavController,
    viewModel: SearchListViewModel = viewModel(),
) {
    val context = LocalContext.current
    val serialNumbers by viewModel.serialNumbers.collectAsStateWithLifecycle()
    val scannedSerials by viewModel.scannedSerials.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastScannedResult.collectAsStateWithLifecycle()

    Log.d("SearchListScreen", "UI Update: Scanned ${scannedSerials.size} / ${serialNumbers.size}")

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadCsv(context, it) }
    }

    LaunchedEffect(serialNumbers, scannedSerials) {
        // Keeping this for non-shared VM cases or as backup, but shared VM is primary
        navController.currentBackStackEntry?.savedStateHandle?.set("SERIAL_LIST", serialNumbers)
        navController.currentBackStackEntry?.savedStateHandle?.set("SCANNED_SERIALS", scannedSerials.toList())
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearResult()
        }
    }

    SearchListContent(
        serialNumbers = serialNumbers,
        scannedSerials = scannedSerials,
        onBackClick = { navController.popBackStack() },
        onClearListClick = { viewModel.clearList() },
        onScanClick = { navController.navigate(CameraRoute(isVerifyMode = true)) },
        onImportCsvClick = { csvPickerLauncher.launch("text/comma-separated-values") }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchListContent(
    serialNumbers: List<String>,
    scannedSerials: Set<String>,
    onBackClick: () -> Unit,
    onClearListClick: () -> Unit,
    onScanClick: () -> Unit,
    onImportCsvClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Verify Serie Numbers")
                        if (serialNumbers.isNotEmpty()) {
                            Text(
                                "Scanned ${scannedSerials.size} / ${serialNumbers.size}",
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
                    if (serialNumbers.isNotEmpty()) {
                        IconButton(onClick = onClearListClick) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Clear List")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (serialNumbers.isNotEmpty()) {
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
            if (serialNumbers.isEmpty()) {
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
                        items(serialNumbers) { serial ->
                            val isScanned = scannedSerials.contains(serial)
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
                                        tint = if (isScanned) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = serial,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isScanned) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isScanned) {
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            "FOUND",
                                            style = MaterialTheme.typography.labelSmall,
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
    }
}

@Preview(showBackground = true)
@Composable
fun SearchListEmptyPreview() {
    JetpackComposeTheme {
        SearchListContent(
            serialNumbers = emptyList(),
            scannedSerials = emptySet(),
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
            serialNumbers = listOf("123456", "234567", "345678", "456789"),
            scannedSerials = setOf("123456", "345678"),
            onBackClick = {},
            onClearListClick = {},
            onScanClick = {},
            onImportCsvClick = {}
        )
    }
}
