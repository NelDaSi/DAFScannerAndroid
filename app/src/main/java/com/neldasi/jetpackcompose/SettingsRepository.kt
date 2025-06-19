package com.neldasi.jetpackcompose

import android.content.Context

object SettingsRepository {

    private val defaultAllowedTypes = setOf(
        "2245293", "2245295", "2261325"
    )

    fun loadAllowedTypes(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("allowedTypes", defaultAllowedTypes) ?: defaultAllowedTypes
    }
    fun shouldKeepScreenOn(context: Context): Boolean {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("screenAlwaysOn", false)
    }
}
