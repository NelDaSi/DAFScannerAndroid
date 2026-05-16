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
import kotlinx.coroutines.flow.map
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

    val hasMoreItems: StateFlow<Boolean> = combine(
        _filteredAndSortedItems,
        _visibleCount
    ) { items, count ->
        count < items.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val totalCount: StateFlow<Int> = _filteredAndSortedItems
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scannedCount: StateFlow<Int> = _filteredAndSortedItems
        .map { items -> items.count { it.scanTimestamp != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
                    val allText = inputStream.bufferedReader().use { it.readText() }
                    if (allText.isBlank()) return@withContext emptyList()

                    val rows = mutableListOf<List<String>>()
                    val currentRow = mutableListOf<String>()
                    val currentField = StringBuilder()
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
                                    currentField.setLength(0)
                                }
                                '\n', '\r' -> {
                                    if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
                                        currentRow.add(currentField.toString().trim())
                                        rows.add(ArrayList(currentRow))
                                        currentRow.clear()
                                        currentField.setLength(0)
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
                        rows.add(currentRow)
                    }

                    if (rows.isEmpty()) return@withContext emptyList()

                    var productIdIndex = -1
                    var machineIndex = -1
                    var outputMatIndex = -1
                    var startDateIndex = -1
                    var startTimeIndex = -1
                    var completeDateIndex = -1
                    var completeTimeIndex = -1
                    var headerRowIndex = -1

                    fun String.normalize() = this.replace("\n", " ").replace("\r", " ").trim().lowercase()

                    for (rowIdx in 0 until minOf(rows.size, 100)) {
                        val row = rows[rowIdx]
                        val normalized = row.map { it.normalize() }
                        val pIdIdx = normalized.indexOfFirst { it.contains("product id") }
                        if (pIdIdx != -1) {
                            headerRowIndex = rowIdx
                            productIdIndex = pIdIdx
                            machineIndex = normalized.indexOfFirst { it == "machine" }
                            outputMatIndex = normalized.indexOfFirst { it == "output material" }
                            startDateIndex = normalized.indexOfFirst { it.contains("start date") }
                            startTimeIndex = normalized.indexOfFirst { it.contains("start time") }
                            completeDateIndex = normalized.indexOfFirst { it.contains("complete date") }
                            completeTimeIndex = normalized.indexOfFirst { it.contains("complete time") }
                            break
                        }
                    }

                    val itemsList = mutableListOf<SearchItem>()
                    val processedSerials = mutableSetOf<String>()
                    val startIdx = if (headerRowIndex != -1) headerRowIndex + 1 else 1

                    for (r in startIdx until rows.size) {
                        val row = rows[r]
                        val pIdIdx = if (productIdIndex != -1) productIdIndex else 0
                        if (row.size > pIdIdx) {
                            val productId = row[pIdIdx]
                            if (productId.isNotBlank()) {
                                val hex = if (productId.length >= 6) productId.takeLast(6) else productId
                                if (!processedSerials.contains(hex)) {
                                    val type = if (productId.length >= 7) productId.take(7) else "UNKNOWN"
                                    
                                    itemsList.add(
                                        SearchItem(
                                            typeCode = type,
                                            serialNumber = hex,
                                            decSerial = hexToDec(hex),
                                            machine = getValue(row, machineIndex),
                                            outputMaterial = getValue(row, outputMatIndex),
                                            startDate = getValue(row, startDateIndex),
                                            startTime = getValue(row, startTimeIndex),
                                            completeDate = getValue(row, completeDateIndex),
                                            completeTime = getValue(row, completeTimeIndex)
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
            } catch (e: Exception) {
                Log.e("SearchListVM", "Error loading CSV", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getValue(row: List<String>, index: Int): String? {
        return if (index != -1 && row.size > index) row[index].ifBlank { null } else null
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
