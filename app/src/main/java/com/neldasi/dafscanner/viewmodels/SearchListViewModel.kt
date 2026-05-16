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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _visibleCount = MutableStateFlow(50)

    private val _filteredAndSortedItems = combine(
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
    }

    val filteredItems: StateFlow<List<SearchItem>> = combine(
        _filteredAndSortedItems,
        _visibleCount
    ) { items, count ->
        items.take(count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastScannedResult = MutableStateFlow<ScanMatchResult?>(null)

    private val gson = Gson()
    private val prefKey = "search_list_data"

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _visibleCount.value = 50 // Reset pagination on search
    }

    fun onSortOptionChange(option: SearchSortOption) {
        _sortOption.value = option
        _visibleCount.value = 50 // Reset pagination on sort
    }

    fun loadMore() {
        if (_visibleCount.value < _searchItems.value.size) {
            _visibleCount.value += 50
        }
    }

    fun initStorage(context: Context) {
        if (_searchItems.value.isNotEmpty()) return
        _isLoading.value = true
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val json = prefs.getString(prefKey, null)
            if (json != null) {
                try {
                    val items = withContext(Dispatchers.Default) {
                        val type = object : TypeToken<List<SearchItem>>() {}.type
                        gson.fromJson<List<SearchItem>>(json, type)
                    }
                    _searchItems.value = items
                    Log.d("SearchListVM", "Loaded ${items.size} items from storage")
                } catch (e: Exception) {
                    Log.e("SearchListVM", "Error loading from storage", e)
                }
            }
            _isLoading.value = false
        }
    }

    private fun saveToStorage(context: Context) {
        val currentItems = _searchItems.value
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val json = gson.toJson(currentItems)
            prefs.edit { putString(prefKey, json) }
        }
    }

    private fun hexToDec(hex: String): String {
        return try {
            hex.toLong(16).toString()
        } catch (_: Exception) {
            "N/A"
        }
    }

    fun loadCsv(context: Context, uri: Uri) {
        if (_isLoading.value) return
        _isLoading.value = true
        Log.d("SearchListVM", "Loading CSV: $uri")
        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
                    val reader = inputStream.bufferedReader()
                    
                    val itemsList = mutableListOf<SearchItem>()
                    val processedSerials = mutableSetOf<String>()
                    
                    val allLines = mutableListOf<String>()
                    // Read lines into a list first because we need to peek for headers
                    // For truly massive files, we'd need a more streaming approach for headers too.
                    reader.useLines { lines ->
                        lines.forEach { allLines.add(it) }
                    }

                    if (allLines.isEmpty()) return@withContext emptyList()

                    var headerRowIndex = -1
                    var productIdIndex = -1
                    var machineIndex = -1
                    var outputMatIndex = -1
                    var startDateIndex = -1
                    var startTimeIndex = -1
                    var completeDateIndex = -1
                    var completeTimeIndex = -1

                    fun String.normalizeHeader() = this.replace("\n", " ").replace("\r", " ").trim().lowercase()

                    // Simple CSV line splitter that handles quotes
                    fun splitCsvLine(line: String): List<String> {
                        val result = mutableListOf<String>()
                        var inQuotes = false
                        val currentField = StringBuilder()
                        val delimiter = if (line.contains(";")) ';' else ','
                        
                        for (c in line) {
                            if (c == '"') {
                                inQuotes = !inQuotes
                            } else if (c == delimiter && !inQuotes) {
                                result.add(currentField.toString().trim())
                                currentField.setLength(0)
                            } else {
                                currentField.append(c)
                            }
                        }
                        result.add(currentField.toString().trim())
                        return result
                    }

                    // Search for headers in first 50 lines
                    for (i in 0 until minOf(allLines.size, 50)) {
                        val row = splitCsvLine(allLines[i])
                        val normalizedRow = row.map { it.normalizeHeader() }

                        val pIdIdx = normalizedRow.indexOfFirst { it.contains("product id") }
                        if (pIdIdx != -1) {
                            headerRowIndex = i
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

                    val startIndex = if (headerRowIndex != -1) headerRowIndex + 1 else 1
                    for (i in startIndex until allLines.size) {
                        val row = splitCsvLine(allLines[i])
                        val pIdIdx = if (productIdIndex != -1) productIdIndex else 0

                        if (row.size > pIdIdx) {
                            val productId = row[pIdIdx]
                            if (productId.isNotBlank()) {
                                val hex = productId.takeLast(6)
                                if (!processedSerials.contains(hex)) {
                                    val typeFromId = if (productId.length >= 7) productId.take(7) else "UNKNOWN"

                                    val machine = if (machineIndex != -1 && row.size > machineIndex) row[machineIndex] else null
                                    val outputMat = if (outputMatIndex != -1 && row.size > outputMatIndex) row[outputMatIndex] else null
                                    val startDate = if (startDateIndex != -1 && row.size > startDateIndex) row[startDateIndex] else null
                                    val startTime = if (startTimeIndex != -1 && row.size > startTimeIndex) row[startTimeIndex] else null
                                    val completeDate = if (completeDateIndex != -1 && row.size > completeDateIndex) row[completeDateIndex] else null
                                    val completeTime = if (completeTimeIndex != -1 && row.size > completeTimeIndex) row[completeTimeIndex] else null

                                    itemsList.add(
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
                                    processedSerials.add(hex)
                                }
                            }
                        }
                    }
                    itemsList
                }
                _searchItems.value = results
                _visibleCount.value = 50
                saveToStorage(context)
                Log.d("SearchListVM", "Loaded ${_searchItems.value.size} serial numbers")
            } catch (e: Exception) {
                Log.e("SearchListVM", "Error loading CSV", e)
            } finally {
                _isLoading.value = false
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
