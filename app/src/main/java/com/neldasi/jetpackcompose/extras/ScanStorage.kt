package com.neldasi.jetpackcompose.extras

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.edit
import com.google.gson.Gson
import com.neldasi.jetpackcompose.screens.ScannedPart
import com.neldasi.jetpackcompose.screens.SelectablePart

object ScanStorage {

    object Keys {
        const val PREFS_NAME = "prefs"
        const val ITEMS = "items"
        const val LAST_ORDINAL = "lastOrdinal"
        const val PENDING_SCANS = "pending_scans"
        const val SUFFIX_IMAGE = "_imageUri"
        const val SUFFIX_NOTE = "_note"
    }

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(Keys.PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Load all previously saved parts into the provided state list.
     * This merges base saved data with each part's image/note extras.
     */
    fun loadSavedParts(
        prefs: SharedPreferences,
        into: SnapshotStateList<SelectablePart>
    ) {
        val json = prefs.getString(Keys.ITEMS, null) ?: return
        val loaded = Gson().fromJson(json, Array<ScannedPart>::class.java)

        into.addAll(
            loaded.map { scanned ->
                val uri = prefs.getString("${scanned.fullCode}${Keys.SUFFIX_IMAGE}", null)
                val note = prefs.getString("${scanned.fullCode}${Keys.SUFFIX_NOTE}", null)
                SelectablePart(scanned.copy(imageUri = uri, note = note))
            }
        )
    }

    /**
     * Persist the whole list to SharedPreferences (base fields only).
     */
    fun saveParts(prefs: SharedPreferences, parts: List<ScannedPart>) {
        val json = Gson().toJson(parts.toTypedArray())
        prefs.edit {
            putString(Keys.ITEMS, json)
        }
    }

    /**
     * Generates the next ordinal, persists it, and returns it.
     */
    fun nextOrdinal(prefs: SharedPreferences): Int {
        val lastOrdinal = prefs.getInt(Keys.LAST_ORDINAL, 0)
        val newOrdinal = lastOrdinal + 1
        prefs.edit { putInt(Keys.LAST_ORDINAL, newOrdinal) }
        return newOrdinal
    }

    /**
     * Remove a single part's persisted extras, and also
     * ensure it is not left in the pending queue.
     */
    fun removePartAndCleanup(
        prefs: SharedPreferences,
        fullCode: String
    ) {
        prefs.edit {
            remove("${fullCode}${Keys.SUFFIX_IMAGE}")
            remove("${fullCode}${Keys.SUFFIX_NOTE}")

            val pendingJson = prefs.getString(Keys.PENDING_SCANS, null)
            if (!pendingJson.isNullOrBlank()) {
                try {
                    val arr = org.json.JSONArray(pendingJson)
                    val kept = org.json.JSONArray()
                    for (i in 0 until arr.length()) {
                        val v = arr.optString(i)
                        if (v != fullCode) kept.put(v)
                    }
                    putString(
                        Keys.PENDING_SCANS,
                        if (kept.length() == 0) null else kept.toString()
                    )
                } catch (_: Exception) {
                    // ignore malformed queue
                }
            }
        }
    }

    /**
     * Remove multiple parts at once (used for 'delete selected').
     */
    fun removePartsAndCleanup(
        prefs: SharedPreferences,
        codes: List<String>
    ) {
        prefs.edit {
            codes.forEach { code ->
                remove("${code}${Keys.SUFFIX_IMAGE}")
                remove("${code}${Keys.SUFFIX_NOTE}")
            }

            val pendingJson = prefs.getString(Keys.PENDING_SCANS, null)
            if (!pendingJson.isNullOrBlank()) {
                try {
                    val arr = org.json.JSONArray(pendingJson)
                    val kept = org.json.JSONArray()
                    for (i in 0 until arr.length()) {
                        val v = arr.optString(i)
                        if (!codes.contains(v)) kept.put(v)
                    }
                    putString(
                        Keys.PENDING_SCANS,
                        if (kept.length() == 0) null else kept.toString()
                    )
                } catch (_: Exception) {
                    // ignore malformed queue
                }
            }
        }
    }

    /**
     * Clear everything: list, ordinals, pending queue, and all extras like _imageUri/_note.
     */
    fun clearAll(prefs: SharedPreferences) {
        val allKeys = prefs.all.keys
        prefs.edit {
            remove(Keys.ITEMS)
            putInt(Keys.LAST_ORDINAL, 0)
            remove(Keys.PENDING_SCANS)

            allKeys.forEach { key ->
                if (key.endsWith(Keys.SUFFIX_IMAGE) || key.endsWith(Keys.SUFFIX_NOTE)) {
                    remove(key)
                }
            }
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