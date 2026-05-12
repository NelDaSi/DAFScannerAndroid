package com.neldasi.dafscanner.extras

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson

object ScanStorage {

    object Keys {
        const val PREFS_NAME = "prefs"
        const val PENDING_SCANS = "pending_scans"
    }

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Clear the pending queue and other settings.
     */
    fun clearAll(prefs: SharedPreferences) {
        prefs.edit {
            remove(Keys.PENDING_SCANS)
            remove("vibrateEnabled")
            remove("continuousScanEnabled")
            remove("screenAlwaysOn")
            remove("allowedTypes")
        }
    }

    /**
     * Consume and clear pending queue.
     * Returns a de-duped list of codes that were pending.
     */
    fun consumePendingQueue(prefs: SharedPreferences): List<String> {
        val pending = prefs.getString(Keys.PENDING_SCANS, null) ?: return emptyList()
        val result = mutableListOf<String>()
        try {
            val arr = Gson().fromJson(pending, Array<String>::class.java)
            val seen = mutableSetOf<String>()
            arr?.forEach { code ->
                if (seen.add(code)) {
                    result.add(code)
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
