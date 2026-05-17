package com.neldasi.dafscanner.screens

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
import androidx.navigation.NavController
import com.neldasi.dafscanner.R
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import java.text.SimpleDateFormat
import java.util.*

data class ConversionRecord(
    val hex: String,
    val dec: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun ConverterScreen(navController: NavController) {
    ConverterScreenContent(onBackClick = { navController.popBackStack() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreenContent(onBackClick: () -> Unit) {
    var hexVal by remember { mutableStateOf("") }
    var decVal by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<ConversionRecord>() }
    val clipboardManager = LocalClipboardManager.current

    fun addToHistory(h: String, d: String) {
        if (h.isBlank() || d.isBlank() || d == "Error") return
        if (history.any { it.hex == h }) return // Don't add duplicates
        history.add(0, ConversionRecord(h, d))
    }

    Scaffold(
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
                        IconButton(onClick = { history.clear() }) {
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
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No conversion history",
                            color = MaterialTheme.colorScheme.outline
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
                        items(history) { record ->
                            HistoryItem(record, onCopyDec = {
                                clipboardManager.setText(AnnotatedString(record.dec))
                            }, onCopyHex = {
                                clipboardManager.setText(AnnotatedString(record.hex))
                            })
                        }
                    }
                }
            }

            // Input Area
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                hexVal = filtered
                                decVal = try {
                                    if (filtered.isEmpty()) "" else filtered.toLong(16).toString()
                                } catch (_: Exception) { "Error" }
                            },
                            label = { Text(stringResource(R.string.hex_label)) },
                            modifier = Modifier.weight(1f),
                            leadingIcon = { Text("0x", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        IconButton(
                            onClick = { addToHistory(hexVal, decVal) },
                            enabled = hexVal.isNotEmpty() && decVal != "Error",
                            modifier = Modifier
                                .background(
                                    if (hexVal.isNotEmpty() && decVal != "Error") MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                Icons.Rounded.PlaylistAdd, 
                                contentDescription = "Save to history",
                                tint = if (hexVal.isNotEmpty() && decVal != "Error") MaterialTheme.colorScheme.onPrimaryContainer 
                                       else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // DEC Input
                    OutlinedTextField(
                        value = decVal,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }
                            decVal = filtered
                            hexVal = try {
                                if (filtered.isEmpty()) "" else filtered.toLong().toString(16).uppercase()
                            } catch (_: Exception) { "Error" }
                        },
                        label = { Text(stringResource(R.string.dec_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { addToHistory(hexVal, decVal) }
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
fun HistoryItem(record: ConversionRecord, onCopyHex: () -> Unit, onCopyDec: () -> Unit) {
    val timeStr = remember(record.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    }
    
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
            
            IconButton(onClick = onCopyDec, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Dec", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConverterScreenPreview() {
    JetpackComposeTheme {
        ConverterScreenContent(onBackClick = {})
    }
}
