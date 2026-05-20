package com.neldasi.dafscanner

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.neldasi.dafscanner.extras.LockScreenOrientation
import com.neldasi.dafscanner.extras.ScanStorage
import com.neldasi.dafscanner.extras.SettingsRepository
import com.neldasi.dafscanner.extras.findActivity
import com.neldasi.dafscanner.navigation.AppNavigation
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            
            // Lock to portrait mode
            LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

            var theme by remember { mutableStateOf(SettingsRepository.getTheme(context)) }
            var fontSizeScale by remember { mutableFloatStateOf(SettingsRepository.getFontSizeScale(context)) }
            var screenAlwaysOn by remember { mutableStateOf(SettingsRepository.shouldKeepScreenOn(context)) }

            // Reactive Screen On Management
            LaunchedEffect(screenAlwaysOn) {
                val activity = context.findActivity()
                if (screenAlwaysOn) {
                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            val listener = remember {
                SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                    when (key) {
                        ScanStorage.Keys.APP_THEME -> theme = p.getString(key, "SYSTEM") ?: "SYSTEM"
                        ScanStorage.Keys.FONT_SIZE_SCALE -> fontSizeScale = p.getFloat(key, 1.0f)
                        ScanStorage.Keys.SCREEN_ALWAYS_ON -> screenAlwaysOn = p.getBoolean(key, false)
                    }
                }
            }

            DisposableEffect(context) {
                val prefs = ScanStorage.prefs(context)
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            JetpackComposeTheme(theme = theme, fontSizeScale = fontSizeScale) {
                AppNavigation()
            }
        }
    }
}
