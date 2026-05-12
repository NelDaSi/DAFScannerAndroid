package com.neldasi.dafscanner.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neldasi.dafscanner.data.AppDatabase
import com.neldasi.dafscanner.data.ScannedPart
import com.neldasi.dafscanner.data.ScanRepository
import com.neldasi.dafscanner.extras.parseScannedCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScanRepository
    val allParts: StateFlow<List<ScannedPart>>

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredParts: StateFlow<List<ScannedPart>>

    init {
        val scanDao = AppDatabase.getDatabase(application).scanDao()
        repository = ScanRepository(scanDao)
        allParts = repository.allParts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Fix existing items if they all have ordinal 1 (from previous bug)
        viewModelScope.launch {
            val parts = repository.allParts.first()
            if (parts.size > 1 && parts.count { it.ordinal == 1 } > 1) {
                parts.sortedBy { it.timestamp }.forEachIndexed { index, part ->
                    repository.update(part.copy(ordinal = index + 1))
                }
            }
        }

        filteredParts = combine(allParts, _searchQuery) { parts, query ->
            if (query.isBlank()) {
                parts
            } else {
                val tokens = query.split(',', ';', ' ', '\n', '\t')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (tokens.isEmpty()) {
                    parts
                } else {
                    parts.filter { part ->
                        val serial = parseScannedCode(part.fullCode)?.serialNumber ?: ""
                        tokens.any { tok -> serial.contains(tok, ignoreCase = true) }
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun addPart(code: String) {
        viewModelScope.launch {
            if (code.isBlank()) return@launch
            val existing = repository.getPartByCode(code)
            if (existing == null) {
                // Get the current max ordinal to assign the next one
                val maxOrdinal = repository.getMaxOrdinal()
                val newPart = ScannedPart(
                    fullCode = code,
                    timestamp = System.currentTimeMillis(),
                    ordinal = maxOrdinal + 1
                )
                repository.insert(newPart)
            }
        }
    }

    fun deleteSelected(codes: List<String>) {
        viewModelScope.launch {
            repository.deleteParts(codes)
        }
    }

    fun deletePart(part: ScannedPart) {
        viewModelScope.launch {
            repository.delete(part)
        }
    }

    suspend fun exportToCsv(): File? = withContext(Dispatchers.IO) {
        val parts = allParts.value
        if (parts.isEmpty()) return@withContext null

        val file = File(getApplication<Application>().cacheDir, "scanned_parts.csv")
        file.bufferedWriter().use { out ->
            out.write("Ordinal,TypeCode,SupplierCode,SerialNumber,BatchNumber,Timestamp,Note,FullCode\n")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            parts.forEach { part ->
                val parsed = parseScannedCode(part.fullCode)
                val date = dateFormat.format(Date(part.timestamp))
                out.write("${part.ordinal},${parsed?.typeCode ?: ""},${parsed?.supplierCode ?: ""},${parsed?.serialNumber ?: ""},${parsed?.batchNumber ?: ""},$date,${part.note ?: ""},${part.fullCode}\n")
            }
        }
        file
    }
}
