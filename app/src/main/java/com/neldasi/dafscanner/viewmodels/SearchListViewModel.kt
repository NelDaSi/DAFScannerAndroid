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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.content.edit

data class SearchItem(
    val typeCode: String,
    val serialNumber: String,
    val decSerial: String,
    val scanTimestamp: Long? = null,
    val scanOrder: Int? = null,
    val machine: String? = null,
    val outputMaterial: String? = null,
    val startDate: String? = null,
    val startTime: String? = null,
    val completeDate: String? = null,
    val completeTime: String? = null
)

enum class SearchSortOption {
    DEFAULT,
    MACHINE,
    TYPE,
    FOUND_TIME,
    PRODUCTION_TIME
}

data class ScanMatchResult(
    val serial: String,
    val isMatch: Boolean,
)

class SearchListViewModel : ViewModel() {
    private val _searchItems = MutableStateFlow<List<SearchItem>>(emptyList())
    val searchItems = _searchItems.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SearchSortOption.DEFAULT)
    val sortOption = _sortOption.asStateFlow()

    val filteredItems: StateFlow<List<SearchItem>> = combine(
        _searchItems,
        _searchQuery,
        _sortOption
    ) { items, query, sort ->
        val filtered = if (query.isBlank()) {
            items
        } else {
            val q = query.lowercase()
            items.filter { item ->
                item.serialNumber.lowercase().contains(q) ||
                item.decSerial.lowercase().contains(q) ||
                item.typeCode.lowercase().contains(q) ||
                (item.machine?.lowercase()?.contains(q) ?: false) ||
                (item.outputMaterial?.lowercase()?.contains(q) ?: false) ||
                (item.startDate?.lowercase()?.contains(q) ?: false) ||
                (item.startTime?.lowercase()?.contains(q) ?: false)
            }
        }

        when (sort) {
            SearchSortOption.DEFAULT -> filtered
            SearchSortOption.MACHINE -> filtered.sortedBy { it.machine ?: "" }
            SearchSortOption.TYPE -> filtered.sortedBy { it.typeCode }
            SearchSortOption.FOUND_TIME -> filtered.sortedByDescending { it.scanTimestamp ?: 0L }
            SearchSortOption.PRODUCTION_TIME -> filtered.sortedByDescending { 
                "${it.startDate ?: ""} ${it.startTime ?: ""}" 
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastScannedResult = MutableStateFlow<ScanMatchResult?>(null)

    private val gson = Gson()
    private val prefKey = "search_list_data"

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortOptionChange(option: SearchSortOption) {
        _sortOption.value = option
    }

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
        prefs.edit { putString(prefKey, json) }
    }

    private fun hexToDec(hex: String): String {
        return try {
            hex.toLong(16).toString()
        } catch (_: Exception) {
            "N/A"
        }
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
                var machineIndex = -1
                var outputMatIndex = -1
                var startDateIndex = -1
                var startTimeIndex = -1
                var completeDateIndex = -1
                var completeTimeIndex = -1

                // Helper to normalize header names
                fun String.normalizeHeader() = this.replace("\n", " ").replace("\r", " ").trim().lowercase()

                // Search for headers
                for (rowIdx in 0 until minOf(rows.size, 50)) {
                    val row = rows[rowIdx]
                    val normalizedRow = row.map { it.normalizeHeader() }
                    
                    val pIdIdx = normalizedRow.indexOfFirst { it.contains("product id") }
                    if (pIdIdx != -1) {
                        headerRowIndex = rowIdx
                        productIdIndex = pIdIdx
                        machineIndex = normalizedRow.indexOfFirst { it == "machine" }
                        outputMatIndex = normalizedRow.indexOfFirst { it == "output material" }
                        startDateIndex = normalizedRow.indexOfFirst { it == "start date" }
                        startTimeIndex = normalizedRow.indexOfFirst { it == "start time" }
                        completeDateIndex = normalizedRow.indexOfFirst { it == "complete date" }
                        completeTimeIndex = normalizedRow.indexOfFirst { it == "complete time" }
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
                            if (productId.isNotBlank()) {
                                val hex = productId.takeLast(6)
                                val typeFromId = if (productId.length >= 7) productId.take(7) else "UNKNOWN"
                                
                                val machine = if (machineIndex != -1 && row.size > machineIndex) row[machineIndex] else null
                                val outputMat = if (outputMatIndex != -1 && row.size > outputMatIndex) row[outputMatIndex] else null
                                val startDate = if (startDateIndex != -1 && row.size > startDateIndex) row[startDateIndex] else null
                                val startTime = if (startTimeIndex != -1 && row.size > startTimeIndex) row[startTimeIndex] else null
                                val completeDate = if (completeDateIndex != -1 && row.size > completeDateIndex) row[completeDateIndex] else null
                                val completeTime = if (completeTimeIndex != -1 && row.size > completeTimeIndex) row[completeTimeIndex] else null

                                results.add(
                                    SearchItem(
                                        typeCode = typeFromId,
                                        serialNumber = hex,
                                        decSerial = hexToDec(hex),
                                        machine = machine,
                                        outputMaterial = outputMat,
                                        startDate = startDate,
                                        startTime = startTime,
                                        completeDate = completeDate,
                                        completeTime = completeTime
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
        prefs.edit { remove(prefKey) }
    }
}
