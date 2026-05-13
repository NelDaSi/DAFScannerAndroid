package com.neldasi.dafscanner

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.neldasi.dafscanner.extras.SettingsRepository
import com.neldasi.dafscanner.navigation.AppNavigation
import com.neldasi.dafscanner.ui.theme.JetpackComposeTheme
import android.content.Context
import android.content.SharedPreferences

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
            
            DisposableEffect(context) {
                val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                    if (key == "appTheme") {
                        theme = p.getString("appTheme", "SYSTEM") ?: "SYSTEM"
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            JetpackComposeTheme(theme = theme) {
                AppNavigation()
            }
        }
    }
}
