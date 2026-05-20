package com.neldasi.dafscanner.extras

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson

object ScanStorage {

    object Keys {
        const val PREFS_NAME = "prefs"
        const val PENDING_SCANS = "pending_scans"
        const val APP_THEME = "appTheme"
        const val FONT_SIZE_SCALE = "fontSizeScale"
        const val SCREEN_ALWAYS_ON = "screenAlwaysOn"
        const val VIBRATE_ENABLED = "vibrateEnabled"
        const val CONTINUOUS_SCAN_ENABLED = "continuousScanEnabled"
        const val ALLOWED_TYPES = "allowedTypes"
        
        // Search List Keys
        const val SEARCH_LIST_DATA = "search_list_data"
        const val SEARCH_QUERY = "search_query"
        const val SEARCH_SORT_OPTION = "sort_option"
        const val SEARCH_MACHINE_FILTER = "machine_filter"
        const val SEARCH_TYPE_FILTER = "type_filter"
    }

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Factory reset: Clear all shared preferences.
     */
    fun clearAll(prefs: SharedPreferences) {
        prefs.edit {
            clear()
        }
    }

    data class PendingScan(val code: String, val timestamp: Long)

    /**
     * Consume and clear pending queue.
     * Returns a de-duped list of scans (code + timestamp) that were pending.
     */
    fun consumePendingQueue(prefs: SharedPreferences): List<PendingScan> {
        val pending = prefs.getString(Keys.PENDING_SCANS, null) ?: return emptyList()
        val result = mutableListOf<PendingScan>()
        try {
            val arr = Gson().fromJson(pending, Array<PendingScan>::class.java)
            val seen = mutableSetOf<String>()
            arr?.forEach { scan ->
                if (seen.add(scan.code)) {
                    result.add(scan)
                }
            }
        } catch (_: Exception) {
            // ignore malformed
        }
        // always clear after consumption
        prefs.edit { remove(Keys.PENDING_SCANS) }
        return result
    }
}
