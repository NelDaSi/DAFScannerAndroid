@file:OptIn(ExperimentalMaterial3Api::class)

package com.neldasi.jetpackcompose

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.core.content.edit

// SettingsScreen composable
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val context = LocalContext.current

        val allowedTypes = remember { mutableStateListOf<String>().apply { addAll(loadAllowedTypes(context)) } }
        val defaultAllowedTypes = setOf(
            "1615188", "1615597", "1656701", "1665585", "1669851",
            "1783137", "2187738", "2126628", "2266341", "2150000",
            "2265920", "2265921", "2002045", "2002046", "2002047",
            "2002048", "2002049", "2002050", "2002051", "2245293",
            "2245295", "2204980", "2261325", "2260980"
        )
        var showAddDialog by remember { mutableStateOf(false) }
        var newType by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Scanner Settings", style = MaterialTheme.typography.headlineSmall)
            var vibrateEnabled by remember { mutableStateOf(true) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrate on scan")
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = vibrateEnabled, onCheckedChange = { vibrateEnabled = it })
            }

            var screenAlwaysOn by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Screen always on")
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = screenAlwaysOn, onCheckedChange = { screenAlwaysOn = it })
            }

            Spacer(modifier = Modifier.height(24.dp))

            var expanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Allowed Types", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(40.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Type")
                }
            }

            if (expanded) {
                LazyColumn {
                    items(allowedTypes) { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(1.dp, Color.Gray)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(type, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.weight(1f))
                            if (type !in defaultAllowedTypes) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.Red,
                                    modifier = Modifier
                                        .clickable {
                                            allowedTypes.remove(type)
                                            saveAllowedTypes(context, allowedTypes)
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Allowed Type") },
                text = {
                    OutlinedTextField(
                        value = newType,
                        onValueChange = { newType = it },
                        label = { Text("Type Code") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newType.isNotBlank()) {
                            allowedTypes.add(newType)
                            saveAllowedTypes(context, allowedTypes)
                            newType = ""
                            showAddDialog = false
                        } else {
                            Toast.makeText(context, "Type cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    Button(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Removed old FAB at the bottom of the screen.
    }
}

fun saveAllowedTypes(context: Context, types: List<String>) {
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    prefs.edit { putStringSet("allowedTypes", types.toSet()) }
}
