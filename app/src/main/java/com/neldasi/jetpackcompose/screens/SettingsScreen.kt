@file:OptIn(ExperimentalMaterial3Api::class)

package com.neldasi.jetpackcompose.screens

import android.app.Activity
import android.content.Context
import android.view.WindowManager
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.core.content.edit
import androidx.compose.ui.res.stringResource
import com.neldasi.jetpackcompose.R
import com.neldasi.jetpackcompose.extras.SettingsRepository


// SettingsScreen composable
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(
                            R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        val context = LocalContext.current

        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        var vibrateEnabled by remember { mutableStateOf(false) }
        var screenAlwaysOn by remember { mutableStateOf(false) }

        // Load preferences once on first composition
        LaunchedEffect(Unit) {
            vibrateEnabled = prefs.getBoolean("vibrateEnabled", false)
            screenAlwaysOn = prefs.getBoolean("screenAlwaysOn", false)
        }

        val allowedTypes = remember { mutableStateListOf<String>().apply { addAll(SettingsRepository.loadAllowedTypes(context)) } }
        val defaultAllowedTypes = setOf(
            "2245293", "2245295", "2261325"
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
            Text(stringResource(R.string.scanner_settings_title), style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.vibrate_on_scan_label))
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = vibrateEnabled,
                    onCheckedChange = {
                        vibrateEnabled = it
                        prefs.edit { putBoolean("vibrateEnabled", it) }
                    }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.screen_always_on_label))
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = screenAlwaysOn,
                    onCheckedChange = {
                        screenAlwaysOn = it
                        prefs.edit { putBoolean("screenAlwaysOn", it) }

                        val activity = (context as? Activity)
                        if (it) {
                            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            var expanded by remember { mutableStateOf(true) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.allowed_types_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(
                        R.string.expand)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(40.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_type))
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
                                    contentDescription = stringResource(R.string.delete),
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
                title = { Text(stringResource(R.string.add_allowed_type_title)) },
                text = {
                    OutlinedTextField(
                        value = newType,
                        onValueChange = { newType = it },
                        label = { Text(stringResource(R.string.type_code_label)) }
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
                            Toast.makeText(context, context.getString(R.string.type_not_empty_message), Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text(stringResource(R.string.add))
                    }
                },
                dismissButton = {
                    Button(onClick = { showAddDialog = false }) {
                        Text(stringResource(R.string.cancel))
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

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SettingsScreen(navController = NavController(LocalContext.current))
}
