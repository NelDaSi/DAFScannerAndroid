package com.neldasi.dafscanner.extras

import android.content.Context
import androidx.core.content.edit

object SettingsRepository {

    private val defaultAllowedTypes = setOf(
        "2245293", "2245295", "2261325", "2150001", "2342199", "2342201", "2012566",
    )

    fun loadAllowedTypes(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("allowedTypes", defaultAllowedTypes) ?: defaultAllowedTypes
    }
    fun shouldKeepScreenOn(context: Context): Boolean {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("screenAlwaysOn", false)
    }

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return prefs.getString("appTheme", "SYSTEM") ?: "SYSTEM"
    }

    fun setTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("appTheme", theme) }
    }
}
