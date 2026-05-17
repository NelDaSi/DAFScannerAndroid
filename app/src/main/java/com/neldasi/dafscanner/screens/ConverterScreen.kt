package com.neldasi.dafscanner.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.neldasi.dafscanner.R
import com.neldasi.dafscanner.data.ConversionRecord
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import com.neldasi.dafscanner.viewmodels.ConverterViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConverterScreen(navController: NavController, viewModel: ConverterViewModel = viewModel()) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    
    ConverterScreenContent(
        history = history,
        onBackClick = { 
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            }
        },
        onAddToHistory = { h, d -> viewModel.addToHistory(h, d) },
        onDeleteRecord = { viewModel.deleteRecord(it) },
        onClearHistory = { viewModel.clearHistory() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreenContent(
    history: List<ConversionRecord>,
    onBackClick: () -> Unit,
    onAddToHistory: (String, String) -> Unit,
    onDeleteRecord: (ConversionRecord) -> Unit,
    onClearHistory: () -> Unit
) {
    var hexVal by remember { mutableStateOf("") }
    var decVal by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Auto-save on leave logic
    val currentHex by rememberUpdatedState(hexVal)
    val currentDec by rememberUpdatedState(decVal)
    val currentHistory by rememberUpdatedState(history)

    DisposableEffect(Unit) {
        onDispose {
            if (currentHex.length == 6 && currentDec != "Error") {
                val isAlreadyLastSaved = currentHistory.firstOrNull()?.hex == currentHex
                if (!isAlreadyLastSaved) {
                    onAddToHistory(currentHex, currentDec)
                }
            }
        }
    }

    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text("Already in History") },
            text = { Text("This value (${hexVal}) is already in your history. Do you want to add it again?") },
            confirmButton = {
                TextButton(onClick = {
                    onAddToHistory(hexVal, decVal)
                    showDuplicateDialog = false
                }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History?") },
            text = { Text("This will permanently delete all conversion records.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.converter_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear History")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // History List
            Box(modifier = Modifier.weight(1f)) {
                if (history.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No conversion history",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "Recent Conversions",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(history, key = { it.id }) { record ->
                            HistoryItem(
                                record = record,
                                onCopyDec = {
                                    clipboardManager.setText(AnnotatedString(record.dec))
                                },
                                onCopyHex = {
                                    clipboardManager.setText(AnnotatedString(record.hex))
                                },
                                onDelete = { onDeleteRecord(record) }
                            )
                        }
                    }
                }
            }

            // Floating Input Area
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val handleAdd: () -> Unit = {
                        if (hexVal.length == 6) {
                            val isDuplicate = history.any { it.hex == hexVal }
                            if (isDuplicate) {
                                showDuplicateDialog = true
                            } else {
                                onAddToHistory(hexVal, decVal)
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("HEX value must be 6 characters long")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // HEX Input
                        OutlinedTextField(
                            value = hexVal,
                            onValueChange = { input ->
                                val filtered = input.uppercase().filter { it in "0123456789ABCDEF" }
                                if (filtered.length <= 6) {
                                    hexVal = filtered
                                    decVal = try {
                                        if (filtered.isEmpty()) "" else filtered.toLong(16).toString()
                                    } catch (_: Exception) { "Error" }
                                }
                            },
                            label = { Text(stringResource(R.string.hex_label)) },
                            placeholder = { Text("6-digit HEX (e.g. 1A2B3C)") },
                            modifier = Modifier.weight(1f),
                            leadingIcon = { Text("0x", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)) },
                            trailingIcon = {
                                Row {
                                    if (hexVal.isNotEmpty()) {
                                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(hexVal)) }) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy HEX", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    IconButton(onClick = {
                                        clipboardManager.getText()?.let { text ->
                                            val pasted = text.text.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                                            hexVal = pasted
                                            decVal = try {
                                                if (pasted.isEmpty()) "" else pasted.toLong(16).toString()
                                            } catch (_: Exception) { "Error" }
                                        }
                                    }) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste HEX", modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            isError = hexVal.isNotEmpty() && hexVal.length != 6,
                            supportingText = {
                                Text(
                                    text = "${hexVal.length}/6 characters",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        IconButton(
                            onClick = handleAdd,
                            enabled = hexVal.length == 6 && decVal != "Error",
                            modifier = Modifier
                                .background(
                                    if (hexVal.length == 6 && decVal != "Error") MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                Icons.Rounded.PlaylistAdd, 
                                contentDescription = "Save to history",
                                tint = if (hexVal.length == 6 && decVal != "Error") MaterialTheme.colorScheme.onPrimaryContainer 
                                       else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // DEC Input
                    val maxDec = 16777215L
                    OutlinedTextField(
                        value = decVal,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }
                            val numeric = filtered.toLongOrNull() ?: 0L
                            
                            if (numeric <= maxDec) {
                                decVal = filtered
                                hexVal = try {
                                    if (filtered.isEmpty()) "" else filtered.toLong().toString(16).uppercase().padStart(6, '0')
                                } catch (_: Exception) { "Error" }
                            }
                        },
                        label = { Text(stringResource(R.string.dec_label)) },
                        placeholder = { Text("Max: 16,777,215") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Row {
                                if (decVal.isNotEmpty()) {
                                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(decVal)) }) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy DEC", modifier = Modifier.size(20.dp))
                                    }
                                }
                                IconButton(onClick = {
                                    clipboardManager.getText()?.let { text ->
                                        val pasted = text.text.filter { it.isDigit() }
                                        val numeric = pasted.toLongOrNull() ?: 0L
                                        val capped = if (numeric > maxDec) maxDec.toString() else pasted
                                        
                                        decVal = capped
                                        hexVal = try {
                                            if (capped.isEmpty()) "" else capped.toLong().toString(16).uppercase().padStart(6, '0')
                                        } catch (_: Exception) { "Error" }
                                    }
                                }) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste DEC", modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        singleLine = true,
                        isError = (decVal.toLongOrNull() ?: 0L) > maxDec,
                        supportingText = {
                            if ((decVal.toLongOrNull() ?: 0L) > 0) {
                                Text(
                                    text = if ((decVal.toLongOrNull() ?: 0L) > maxDec) "Value exceeds 6-digit HEX limit" else "Within range",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { handleAdd() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE31E24) // DAF Red
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    record: ConversionRecord,
    onCopyHex: () -> Unit,
    onCopyDec: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(record.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    }
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("HEX: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(record.hex, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DEC: ", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE31E24))
                    Text(record.dec, style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            Text(
                timeStr, 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete record",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy HEX (${record.hex})") },
                            onClick = {
                                onCopyHex()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Rounded.Numbers, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy DEC (${record.dec})") },
                            onClick = {
                                onCopyDec()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Rounded.Tag, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Preview(showBackground = true)
@Composable
fun ConverterScreenPreview() {
    JetpackComposeTheme {
        ConverterScreenContent(
            history = listOf(
                ConversionRecord(1, "ABC", "2748"),
                ConversionRecord(2, "FF", "255")
            ),
            onBackClick = {},
            onAddToHistory = { _, _ -> },
            onDeleteRecord = {},
            onClearHistory = {}
        )
    }
}
