
@file:OptIn(ExperimentalMaterial3Api::class)

package com.neldasi.dafscanner.screens

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.neldasi.dafscanner.R
import com.neldasi.dafscanner.extras.ScanStorage
import com.neldasi.dafscanner.extras.SettingsRepository
import com.neldasi.dafscanner.extras.UpdateManager
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import com.neldasi.dafscanner.viewmodels.SettingsViewModel


@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val prefs = remember { ScanStorage.prefs(context) }
    
    var vibrateEnabled by remember { mutableStateOf(value = false) }
    var screenAlwaysOn by remember { mutableStateOf(value = false) }
    var continuousScanEnabled by remember { mutableStateOf(value = false) }
    var currentTheme by remember { mutableStateOf(SettingsRepository.getTheme(context)) }

    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()

    LaunchedEffect(Unit) {
        vibrateEnabled = prefs.getBoolean("vibrateEnabled", false)
        screenAlwaysOn = prefs.getBoolean("screenAlwaysOn", false)
        continuousScanEnabled = prefs.getBoolean("continuousScanEnabled", false)
    }

    SettingsScreenContent(
        navController = navController,
        vibrateEnabled = vibrateEnabled,
        onVibrateChange = {
            vibrateEnabled = it
            prefs.edit { putBoolean("vibrateEnabled", it) }
        },
        screenAlwaysOn = screenAlwaysOn,
        onScreenAlwaysOnChange = {
            screenAlwaysOn = it
            prefs.edit { putBoolean("screenAlwaysOn", it) }
            val activity = (context as? Activity)
            if (it) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        },
        continuousScanEnabled = continuousScanEnabled,
        onContinuousScanChange = {
            continuousScanEnabled = it
            prefs.edit { putBoolean("continuousScanEnabled", it) }
        },
        currentTheme = currentTheme,
        onThemeChange = {
            currentTheme = it
            SettingsRepository.setTheme(context, it)
        },
        onClearAllData = { viewModel.clearAllData() },
        isCheckingUpdates = isCheckingUpdates,
        updateInfo = updateInfo,
        onCheckForUpdates = { viewModel.checkForUpdates() }
    )
}

@Composable
fun SettingsScreenContent(
    navController: NavController,
    vibrateEnabled: Boolean,
    onVibrateChange: (Boolean) -> Unit,
    screenAlwaysOn: Boolean,
    onScreenAlwaysOnChange: (Boolean) -> Unit,
    continuousScanEnabled: Boolean,
    onContinuousScanChange: (Boolean) -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onClearAllData: () -> Unit,
    isCheckingUpdates: Boolean,
    updateInfo: UpdateManager.ReleaseInfo?,
    onCheckForUpdates: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    val allowedTypes = remember { mutableStateListOf<String>().apply { addAll(SettingsRepository.loadAllowedTypes(context)) } }
    val defaultAllowedTypes = setOf("2245293", "2245295", "2261325", "2150001", "2342199", "2342201", "2012566")
    
    var showAddDialog by remember { mutableStateOf(value = false) }
    var newType by remember { mutableStateOf(value = "") }
    var showClearAllDialog by remember { mutableStateOf(value = false) }
    var showThemeDialog by remember { mutableStateOf(value = false) }
    var showConverterDialog by remember { mutableStateOf(value = false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.scanner_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        SettingsClickableItem(
                            icon = Icons.Rounded.Palette,
                            title = stringResource(R.string.theme_setting_title),
                            subtitle = when (currentTheme) {
                                "LIGHT" -> stringResource(R.string.theme_light)
                                "DARK" -> stringResource(R.string.theme_dark)
                                "DAF" -> "DAF Theme"
                                else -> stringResource(R.string.theme_system)
                            },
                            onClick = { showThemeDialog = true }
                        )
                        SettingsSwitchItem(
                            icon = Icons.Rounded.NotificationsActive,
                            title = stringResource(R.string.vibrate_on_scan_label),
                            checked = vibrateEnabled,
                            onCheckedChange = onVibrateChange
                        )
                        SettingsSwitchItem(
                            icon = Icons.Rounded.SystemUpdateAlt,
                            title = stringResource(R.string.multi_scan_mode_label),
                            checked = continuousScanEnabled,
                            onCheckedChange = onContinuousScanChange
                        )
                        SettingsSwitchItem(
                            icon = Icons.Rounded.ScreenLockRotation,
                            title = stringResource(R.string.screen_always_on_label),
                            checked = screenAlwaysOn,
                            onCheckedChange = onScreenAlwaysOnChange
                        )
                        SettingsClickableItem(
                            icon = Icons.Rounded.Calculate,
                            title = "HEX <-> DEC Converter",
                            subtitle = "Tool to convert serial numbers",
                            onClick = { showConverterDialog = true }
                        )
                        SettingsClickableItem(
                            icon = Icons.Rounded.SystemUpdateAlt,
                            title = if (isCheckingUpdates) "Checking for updates..." else "Check for updates",
                            subtitle = "Check GitHub for a new version",
                            onClick = onCheckForUpdates
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.allowed_types_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        FloatingActionButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.size(40.dp),
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                            shape = RoundedCornerShape(12.dp),
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_type))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allowedTypes.forEach { type ->
                            TypeItem(
                                type = type,
                                isDefault = type in defaultAllowedTypes,
                                onDelete = {
                                    allowedTypes.remove(type)
                                    saveAllowedTypes(context, allowedTypes)
                                }
                            )
                        }
                    }
                }
            }

//            item {
//                Spacer(modifier = Modifier.height(24.dp))
//                Card(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { showClearAllDialog = true },
//                    shape = RoundedCornerShape(24.dp),
//                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
//                ) {
//                    Row(
//                        modifier = Modifier.padding(20.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.spacedBy(16.dp)
//                    ) {
//                        Icon(
//                            Icons.Rounded.DeleteOutline,
//                            contentDescription = null,
//                            tint = MaterialTheme.colorScheme.error,
//                            modifier = Modifier.size(28.dp)
//                        )
//                        Column(modifier = Modifier.weight(1f)) {
//                            Text(
//                                text = stringResource(R.string.settings_clear_all),
//                                color = MaterialTheme.colorScheme.error,
//                                fontWeight = FontWeight.Bold,
//                                style = MaterialTheme.typography.titleMedium
//                            )
//                            Text(
//                                text = stringResource(R.string.settings_clear_all_desc),
//                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
//                                style = MaterialTheme.typography.bodySmall
//                            )
//                        }
//                    }
//                }
//                Spacer(modifier = Modifier.height(40.dp))
//            }
            item {
                val packageInfo = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (e: Exception) {
                        null
                    }
                }
                val versionText = packageInfo?.let {
                    "v${it.versionName} (${it.versionCode})"
                } ?: "v1.0 (1)"

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = versionText,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(stringResource(R.string.add_allowed_type_title), fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newType,
                        onValueChange = { newType = it },
                        label = { Text(stringResource(R.string.type_code_label)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newType.isNotBlank()) {
                                allowedTypes.add(newType)
                                saveAllowedTypes(context, allowedTypes)
                                newType = ""
                                showAddDialog = false
                            } else {
                                Toast.makeText(context, R.string.type_not_empty_message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.add))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                title = { Text(stringResource(R.string.clear_confirm_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.clear_confirm_text)) },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearAllData()
                            Toast.makeText(context, R.string.yes, Toast.LENGTH_SHORT).show()
                            showClearAllDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text(stringResource(R.string.theme_setting_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeOption(
                            title = "DAF Theme",
                            selected = currentTheme == "DAF",
                            onClick = { onThemeChange("DAF"); showThemeDialog = false }
                        )
                        ThemeOption(
                            title = stringResource(R.string.theme_system),
                            selected = currentTheme == "SYSTEM",
                            onClick = { onThemeChange("SYSTEM"); showThemeDialog = false }
                        )
                        ThemeOption(
                            title = stringResource(R.string.theme_light),
                            selected = currentTheme == "LIGHT",
                            onClick = { onThemeChange("LIGHT"); showThemeDialog = false }
                        )
                        ThemeOption(
                            title = stringResource(R.string.theme_dark),
                            selected = currentTheme == "DARK",
                            onClick = { onThemeChange("DARK"); showThemeDialog = false }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showConverterDialog) {
            var hexVal by remember { mutableStateOf("") }
            var decVal by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showConverterDialog = false },
                title = { Text("HEX <-> DEC Converter", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = hexVal,
                            onValueChange = { input ->
                                hexVal = input.uppercase().filter { it in "0123456789ABCDEF" }
                                decVal = try {
                                    if (hexVal.isEmpty()) "" else hexVal.toLong(16).toString()
                                } catch (_: Exception) {
                                    "Error"
                                }
                            },
                            label = { Text("HEX Value") },
                            placeholder = { Text("e.g. 01C821") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Icon(
                            Icons.Rounded.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = decVal,
                            onValueChange = { input ->
                                decVal = input.filter { it.isDigit() }
                                hexVal = try {
                                    if (decVal.isEmpty()) "" else decVal.toLong().toString(16).uppercase()
                                } catch (_: Exception) {
                                    "Error"
                                }
                            },
                            label = { Text("DEC Value") },
                            placeholder = { Text("e.g. 116769") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showConverterDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        updateInfo?.let { info ->
            AlertDialog(
                onDismissRequest = { /* No-op to force action */ },
                title = { Text("New Update Available!", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Version ${info.tagName} is available.", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(info.body)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            UpdateManager.downloadAndInstall(
                                context,
                                info.downloadUrl,
                                "dafscanner-${info.tagName}.apk"
                            )
                        }
                    ) {
                        Text("Update Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { /* Could add a way to dismiss */ }) {
                        Text("Later")
                    }
                }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(8.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun TypeItem(
    type: String,
    isDefault: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                type,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (!isDefault) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape
                ) {
                    Text(
                        "Default",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

fun saveAllowedTypes(context: Context, types: List<String>) {
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    prefs.edit { putStringSet("allowedTypes", types.toSet()) }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    JetpackComposeTheme {
        SettingsScreenContent(
            navController = rememberNavController(),
            vibrateEnabled = true,
            onVibrateChange = {},
            screenAlwaysOn = false,
            onScreenAlwaysOnChange = {},
            continuousScanEnabled = true,
            onContinuousScanChange = {},
            currentTheme = "SYSTEM",
            onThemeChange = {},
            onClearAllData = {},
            isCheckingUpdates = false,
            updateInfo = null,
            onCheckForUpdates = {}
        )
    }
}
