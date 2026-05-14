package com.neldasi.dafscanner.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neldasi.dafscanner.extras.parseScannedCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchItem(
    val typeCode: String,
    val serialNumber: String,
    val decSerial: String,
    val scanTimestamp: Long? = null,
    val scanOrder: Int? = null
)

data class ScanMatchResult(
    val serial: String,
    val isMatch: Boolean,
)

class SearchListViewModel : ViewModel() {
    private val _searchItems = MutableStateFlow<List<SearchItem>>(emptyList())
    val searchItems = _searchItems.asStateFlow()

    private val _lastScannedResult = MutableStateFlow<ScanMatchResult?>(null)
    val lastScannedResult = _lastScannedResult.asStateFlow()

    private val gson = Gson()
    private val prefKey = "search_list_data"

    fun initStorage(context: Context) {
        if (_searchItems.value.isNotEmpty()) return
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val json = prefs.getString(prefKey, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<SearchItem>>() {}.type
                val items: List<SearchItem> = gson.fromJson(json, type)
                _searchItems.value = items
                Log.d("SearchListVM", "Loaded ${items.size} items from storage")
            } catch (e: Exception) {
                Log.e("SearchListVM", "Error loading from storage", e)
            }
        }
    }

    private fun saveToStorage(context: Context) {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val json = gson.toJson(_searchItems.value)
        prefs.edit().putString(prefKey, json).apply()
    }

    private fun hexToDec(hex: String): String {
        return try {
            hex.toLong(16).toString()
        } catch (_: Exception) {
            "N/A"
        }
    }

    fun loadMockData(context: Context) {
        val results = mutableListOf<SearchItem>()
        for (i in 1..25) {
            val hex = String.format("%06X", (0x1000..0xFFFFFF).random())
            results.add(SearchItem(
                typeCode = "2245295",
                serialNumber = hex,
                decSerial = hexToDec(hex)
            ))
        }
        _searchItems.value = results
        
        // Randomly mark 5 items as scanned
        val toScan = results.shuffled().take(5)
        toScan.forEachIndexed { index, item ->
            val itemIndex = _searchItems.value.indexOfFirst { it.serialNumber == item.serialNumber }
            if (itemIndex != -1) {
                val updatedItems = _searchItems.value.toMutableList()
                updatedItems[itemIndex] = updatedItems[itemIndex].copy(
                    scanTimestamp = System.currentTimeMillis() - (index * 100000),
                    scanOrder = index + 1
                )
                _searchItems.value = updatedItems
            }
        }
        saveToStorage(context)
    }

    fun loadCsv(context: Context, uri: Uri) {
        Log.d("SearchListVM", "Loading CSV: $uri")
        viewModelScope.launch {
            try {
                val input = context.contentResolver.openInputStream(uri) ?: return@launch
                val allText = input.bufferedReader().use { it.readText() }
                if (allText.isBlank()) return@launch

                val rows = mutableListOf<List<String>>()
                val currentRow = mutableListOf<String>()
                var currentField = StringBuilder()
                var inQuotes = false
                
                val firstLine = allText.lineSequence().firstOrNull() ?: ""
                val delimiter = if (firstLine.contains(";")) ';' else ','

                var i = 0
                while (i < allText.length) {
                    val c = allText[i]
                    if (inQuotes) {
                        if (c == '"') {
                            if (i + 1 < allText.length && allText[i+1] == '"') {
                                currentField.append('"')
                                i++
                            } else {
                                inQuotes = false
                            }
                        } else {
                            currentField.append(c)
                        }
                    } else {
                        when (c) {
                            '"' -> inQuotes = true
                            delimiter -> {
                                currentRow.add(currentField.toString().trim())
                                currentField = StringBuilder()
                            }
                            '\n', '\r' -> {
                                if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
                                    currentRow.add(currentField.toString().trim())
                                    rows.add(currentRow.toList())
                                    currentRow.clear()
                                    currentField = StringBuilder()
                                }
                                if (c == '\r' && i + 1 < allText.length && allText[i+1] == '\n') {
                                    i++
                                }
                            }
                            else -> currentField.append(c)
                        }
                    }
                    i++
                }
                if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
                    currentRow.add(currentField.toString().trim())
                    rows.add(currentRow.toList())
                }

                if (rows.isEmpty()) return@launch

                var headerRowIndex = -1
                var productIdIndex = -1

                // Search for the "Product ID" header in any row (checking first 50 rows)
                for (rowIdx in 0 until minOf(rows.size, 50)) {
                    val row = rows[rowIdx]
                    val foundColIdx = row.indexOfFirst { 
                        it.replace("\n", " ").replace("\r", " ").trim()
                            .contains("Product ID", ignoreCase = true) 
                    }
                    if (foundColIdx != -1) {
                        headerRowIndex = rowIdx
                        productIdIndex = foundColIdx
                        break
                    }
                }

                val results = mutableListOf<SearchItem>()
                if (headerRowIndex != -1) {
                    // Extract data from all rows after the header row
                    for (rowIdx in (headerRowIndex + 1) until rows.size) {
                        val row = rows[rowIdx]
                        if (row.size > productIdIndex) {
                            val productId = row[productIdIndex]
                            if (productId.length >= 13) {
                                val hex = productId.takeLast(6)
                                results.add(
                                    SearchItem(
                                        typeCode = productId.take(7),
                                        serialNumber = hex,
                                        decSerial = hexToDec(hex)
                                    )
                                )
                            } else if (productId.isNotBlank()) {
                                val hex = productId.takeLast(6)
                                results.add(
                                    SearchItem(
                                        typeCode = "UNKNOWN",
                                        serialNumber = hex,
                                        decSerial = hexToDec(hex)
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Fallback: use first column of each row starting from row 1
                    for (rowIdx in 1 until rows.size) {
                        val row = rows[rowIdx]
                        if (row.isNotEmpty()) {
                            val productId = row[0]
                            if (productId.length >= 13) {
                                val hex = productId.takeLast(6)
                                results.add(
                                    SearchItem(
                                        typeCode = productId.take(7),
                                        serialNumber = hex,
                                        decSerial = hexToDec(hex)
                                    )
                                )
                            } else if (productId.isNotBlank()) {
                                val hex = productId.takeLast(6)
                                results.add(
                                    SearchItem(
                                        typeCode = "UNKNOWN",
                                        serialNumber = hex,
                                        decSerial = hexToDec(hex)
                                    )
                                )
                            }
                        }
                    }
                }
                _searchItems.value = results.distinctBy { it.serialNumber }
                saveToStorage(context)
                Log.d("SearchListVM", "Loaded ${_searchItems.value.size} serial numbers")
            } catch (e: Exception) {
                Log.e("SearchListVM", "Error loading CSV", e)
            }
        }
    }

    fun checkScannedCode(context: Context, fullCode: String) {
        val parsed = parseScannedCode(fullCode)
        val serial = parsed?.serialNumber ?: (if (fullCode.length >= 6) fullCode.takeLast(6) else fullCode)
        forceMarkAsScanned(context, serial)
    }

    fun forceMarkAsScanned(context: Context, serial: String) {
        Log.d("SearchListVM", "forceMarkAsScanned: $serial")
        val currentItems = _searchItems.value
        val itemIndex = currentItems.indexOfFirst { it.serialNumber == serial }
        
        val isMatch = itemIndex != -1
        if (isMatch) {
            val updatedItems = currentItems.toMutableList()
            val item = updatedItems[itemIndex]
            if (item.scanTimestamp == null) {
                val nextOrder = (currentItems.maxOfOrNull { it.scanOrder ?: 0 } ?: 0) + 1
                updatedItems[itemIndex] = item.copy(
                    scanTimestamp = System.currentTimeMillis(),
                    scanOrder = nextOrder
                )
                _searchItems.value = updatedItems
                saveToStorage(context)
                Log.d("SearchListVM", "Match found! Scan order: $nextOrder")
            } else {
                Log.d("SearchListVM", "Match found but already scanned at ${item.scanTimestamp}")
            }
        } else {
            Log.d("SearchListVM", "No match for $serial")
        }
        _lastScannedResult.value = ScanMatchResult(serial, isMatch)
    }

    fun clearResult() {
        _lastScannedResult.value = null
    }

    fun clearList(context: Context) {
        _searchItems.value = emptyList()
        _lastScannedResult.value = null
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        prefs.edit().remove(prefKey).apply()
    }
}
