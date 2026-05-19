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
    val completeTime: String? = null,
)

enum class SearchSortOption {
    DEFAULT,
    MACHINE,
    TYPE,
    FOUND_TIME,
    PRODUCTION_TIME,
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

    private val _machineFilter = MutableStateFlow<String?>(null)
    val machineFilter = _machineFilter.asStateFlow()

    private val _typeFilter = MutableStateFlow<String?>(null)
    val typeFilter = _typeFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(value = false)
    val isLoading = _isLoading.asStateFlow()

    private val _visibleCount = MutableStateFlow(50)

    val availableMachines: StateFlow<List<String>> = _searchItems
        .map { items -> 
            items.mapNotNull { it.machine }
                .map { it.split(" ").first() }
                .distinct()
                .sorted() 
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableTypes: StateFlow<List<String>> = _searchItems
        .map { items -> items.map { it.typeCode }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filteredAndSortedItems = combine(
        _searchItems,
        _searchQuery,
        _sortOption,
        _machineFilter,
        _typeFilter,
    ) { items, query, sort, mFilter, tFilter ->
        var filtered = items

        // Apply Machine Filter
        if (mFilter != null) {
            filtered = filtered.filter { it.machine?.split(" ")?.firstOrNull() == mFilter }
        }

        // Apply Type Filter
        if (tFilter != null) {
            filtered = filtered.filter { it.typeCode == tFilter }
        }

        // Apply Search Query
        if (query.isNotBlank()) {
            val q = query.lowercase()
            filtered = filtered.filter { item ->
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

    // Expose the full filtered list for sharing purposes
    val allFilteredItems: StateFlow<List<SearchItem>> = _filteredAndSortedItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredItems: StateFlow<List<SearchItem>> = combine(
        _filteredAndSortedItems,
        _visibleCount,
    ) { items, count ->
        items.take(count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasMoreItems: StateFlow<Boolean> = combine(
        _filteredAndSortedItems,
        _visibleCount,
    ) { items, count ->
        count < items.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue = false)

    val totalCount: StateFlow<Int> = _filteredAndSortedItems
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scannedCount: StateFlow<Int> = _filteredAndSortedItems
        .map { items -> items.count { it.scanTimestamp != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _lastScannedResult = MutableStateFlow<ScanMatchResult?>(null)

    private val gson = Gson()
    private val prefKey = "search_list_data"
    private val queryKey = "search_query"
    private val sortKey = "sort_option"
    private val machineKey = "machine_filter"
    private val typeKey = "type_filter"

    fun onSearchQueryChange(context: Context, query: String) {
        _searchQuery.value = query
        _visibleCount.value = 50 // Reset pagination on search
        saveFilters(context)
    }

    fun onSortOptionChange(context: Context, option: SearchSortOption) {
        _sortOption.value = option
        _visibleCount.value = 50 // Reset pagination on sort
        saveFilters(context)
    }

    fun onMachineFilterChange(context: Context, machine: String?) {
        _machineFilter.value = machine
        _visibleCount.value = 50
        saveFilters(context)
    }

    fun onTypeFilterChange(context: Context, type: String?) {
        _typeFilter.value = type
        _visibleCount.value = 50
        saveFilters(context)
    }

    private fun saveFilters(context: Context) {
        val query = _searchQuery.value
        val sort = _sortOption.value.name
        val machine = _machineFilter.value
        val type = _typeFilter.value

        viewModelScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            prefs.edit { 
                putString(queryKey, query)
                putString(sortKey, sort)
                putString(machineKey, machine)
                putString(typeKey, type)
            }
        }
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
            
            // Load items
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

            // Load filters
            _searchQuery.value = prefs.getString(queryKey, "") ?: ""
            val savedSort = prefs.getString(sortKey, SearchSortOption.DEFAULT.name)
            _sortOption.value = SearchSortOption.entries.find { it.name == savedSort } ?: SearchSortOption.DEFAULT
            _machineFilter.value = prefs.getString(machineKey, null)
            _typeFilter.value = prefs.getString(typeKey, null)

            _isLoading.value = false
        }
    }

    private fun saveToStorage(context: Context) {
        val currentItems = _searchItems.value
        val query = _searchQuery.value
        val sort = _sortOption.value.name
        val machine = _machineFilter.value
        val type = _typeFilter.value

        viewModelScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val json = gson.toJson(currentItems)
            prefs.edit { 
                putString(prefKey, json)
                putString(queryKey, query)
                putString(sortKey, sort)
                putString(machineKey, machine)
                putString(typeKey, type)
            }
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
                                if (((i + 1) < allText.length) && (allText[i+1] == '"')) {
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
                                    if ((c == '\r') && ((i + 1) < allText.length) && (allText[i+1] == '\n')) {
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
                        
                        // Look for Product ID or Transport Number
                        val pIdIdx = normalized.indexOfFirst { 
                            it.contains("product id") || it.contains("transport number") || it.contains("part id")
                        }
                        
                        if (pIdIdx != -1) {
                            headerRowIndex = rowIdx
                            productIdIndex = pIdIdx
                            machineIndex = normalized.indexOfFirst { (it == "machine") || (it == "workstation") || (it == "station") }
                            outputMatIndex = normalized.indexOfFirst { (it == "output material") || (it == "model") || (it == "material") }
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
                        val pIdIdx = productIdIndex
                        if ((pIdIdx != -1) && (row.size > pIdIdx)) {
                            val productId = row[pIdIdx]
                            if (productId.isNotBlank() && (productId.length >= 18)) {
                                val parsed = parseScannedCode(productId)
                                val hex = parsed?.serialHex ?: productId.takeLast(6)
                                
                                if (!processedSerials.contains(hex)) {
                                    val type = parsed?.typeCode ?: productId.take(7)
                                    
                                    itemsList.add(
                                        SearchItem(
                                            typeCode = type,
                                            serialNumber = hex,
                                            decSerial = parsed?.serialDecimal ?: hexToDec(hex),
                                            machine = getValue(row, machineIndex),
                                            outputMaterial = getValue(row, outputMatIndex),
                                            startDate = getValue(row, startDateIndex),
                                            startTime = getValue(row, startTimeIndex),
                                            completeDate = getValue(row, completeDateIndex),
                                            completeTime = getValue(row, completeTimeIndex),
                                        ),
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
        return if ((index != -1) && (row.size > index)) row[index].ifBlank { null } else null
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
                    scanOrder = nextOrder,
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
        _searchQuery.value = ""
        _machineFilter.value = null
        _typeFilter.value = null
        _sortOption.value = SearchSortOption.DEFAULT
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        prefs.edit { 
            remove(prefKey)
            remove(queryKey)
            remove(sortKey)
            remove(machineKey)
            remove(typeKey)
        }
    }
}
