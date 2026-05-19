package com.neldasi.dafscanner.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neldasi.dafscanner.data.AppDatabase
import com.neldasi.dafscanner.data.ScannedPart
import com.neldasi.dafscanner.data.ScanRepository
import com.neldasi.dafscanner.extras.UpdateManager
import com.neldasi.dafscanner.extras.parseScannedCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _updateInfo = MutableStateFlow<UpdateManager.ReleaseInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    init {
        val scanDao = AppDatabase.getDatabase(application).scanDao()
        repository = ScanRepository(scanDao)
        allParts = repository.allParts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

        filteredParts = combine(allParts, _searchQuery) { parts, query ->
            if (query.isBlank()) {
                parts
            } else {
                val tokens = query.split(',', ';', ' ', '\n', '\t')
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
                if (tokens.isEmpty()) {
                    parts
                } else {
                    parts.filter { part ->
                        val parsed = parseScannedCode(part.fullCode)
                        val serialHex = parsed?.serialHex ?: ""
                        val serialDec = parsed?.serialDecimal ?: ""
                        val typeCode = parsed?.typeCode ?: ""
                        
                        tokens.any { tok -> 
                            serialHex.contains(tok, ignoreCase = true) || 
                            serialDec.contains(tok, ignoreCase = true) ||
                            typeCode.contains(tok, ignoreCase = true)
                        }
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        checkForUpdatesOnStartup()
    }

    private fun checkForUpdatesOnStartup() {
        viewModelScope.launch {
            delay(2000) // Small delay to not interfere with startup
            val result = UpdateManager.checkForUpdates(getApplication())
            if (result is UpdateManager.UpdateResult.NewUpdate) {
                _updateInfo.value = result.info
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun addPart(code: String, timestamp: Long? = null) {
        viewModelScope.launch {
            if (code.isBlank()) return@launch
            val existing = repository.getPartByCode(code)
            if (existing == null) {
                val newPart = ScannedPart(
                    fullCode = code,
                    timestamp = timestamp ?: System.currentTimeMillis(),
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
            out.write("TypeCode;SupplierCode;EngineFormat;SerialHex;SerialDec;BatchNumber;Timestamp;Note;FullCode\n")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            parts.forEach { part ->
                val parsed = parseScannedCode(part.fullCode)
                val date = dateFormat.format(Date(part.timestamp))
                out.write("${parsed?.typeCode ?: ""};${parsed?.supplierCode ?: ""};${parsed?.format?.name ?: ""};${parsed?.serialHex ?: ""};${parsed?.serialDecimal ?: ""};${parsed?.batchNumber ?: ""};$date;${part.note ?: ""};${part.fullCode}\n")
            }
        }
        file
    }
}
