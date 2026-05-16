package com.neldasi.dafscanner

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.neldasi.dafscanner.extras.SettingsRepository
import com.neldasi.dafscanner.navigation.AppNavigation
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SettingsRepository.shouldKeepScreenOn(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var theme by remember { mutableStateOf(SettingsRepository.getTheme(context)) }
            var fontSizeScale by remember { mutableFloatStateOf(SettingsRepository.getFontSizeScale(context)) }
            
            DisposableEffect(context) {
                val prefs = context.getSharedPreferences("prefs", MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                    when (key) {
                        "appTheme" -> theme = p.getString("appTheme", "SYSTEM") ?: "SYSTEM"
                        "fontSizeScale" -> fontSizeScale = p.getFloat("fontSizeScale", 1.0f)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                JetpackComposeTheme(theme = theme, fontSizeScale = fontSizeScale) {
                    AppNavigation()
                }
            }
        }
    }
}
