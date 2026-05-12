package com.neldasi.dafscanner.screens

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.ClipEntry
import com.neldasi.dafscanner.extras.parseScannedCode

@Composable
fun DuplicateDialog(
    show: Boolean,
    duplicateCodes: SnapshotStateList<String>,
    onDismiss: () -> Unit,
    scope: CoroutineScope,
    context: Context
) {
    if (!show) return

    val clipboard = LocalClipboard.current

    // Convert raw codes to user-readable serials (fallback to fullCode if parse fails)
    val serialEntries = duplicateCodes.map { code ->
        parseScannedCode(code)?.serialNumber ?: code
    }

    val duplicateCountText = "Already scanned (${serialEntries.size})"
    val copyAllText = serialEntries.joinToString(", ")

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(duplicateCountText) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(serialEntries) { serial ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = serial,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val clipData = ClipData.newPlainText("serial", serial)
                                        clipboard.setClipEntry(ClipEntry(clipData))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy this serial"
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val clipData = ClipData.newPlainText("serials", copyAllText)
                            clipboard.setClipEntry(ClipEntry(clipData))
                            Toast.makeText(context, "Copied all", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Copy all")
                }
                Button(
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text("OK")
                }
            }
        }
    )
}