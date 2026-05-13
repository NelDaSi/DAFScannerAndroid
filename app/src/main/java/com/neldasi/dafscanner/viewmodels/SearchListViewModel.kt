package com.neldasi.dafscanner.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neldasi.dafscanner.extras.parseScannedCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScanMatchResult(
    val serial: String,
    val isMatch: Boolean,
)

class SearchListViewModel : ViewModel() {
    private val _serialNumbers = MutableStateFlow<List<String>>(emptyList())
    val serialNumbers = _serialNumbers.asStateFlow()

    private val _lastScannedResult = MutableStateFlow<ScanMatchResult?>(null)
    val lastScannedResult = _lastScannedResult.asStateFlow()

    fun loadCsv(context: Context, uri: Uri) {
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

                val results = mutableListOf<String>()
                if (headerRowIndex != -1) {
                    // Extract data from all rows after the header row
                    for (rowIdx in (headerRowIndex + 1) until rows.size) {
                        val row = rows[rowIdx]
                        if (row.size > productIdIndex) {
                            val productId = row[productIdIndex]
                            if (productId.isNotBlank()) {
                                results.add(productId.takeLast(6))
                            }
                        }
                    }
                } else {
                    // Fallback: use first column of each row starting from row 1
                    for (rowIdx in 1 until rows.size) {
                        val row = rows[rowIdx]
                        if (row.isNotEmpty()) {
                            val productId = row[0]
                            if (productId.isNotBlank()) {
                                results.add(productId.takeLast(6))
                            }
                        }
                    }
                }
                _serialNumbers.value = results.asSequence().filter { it.length >= 6 }.distinct().toList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun checkScannedCode(fullCode: String) {
        val parsed = parseScannedCode(fullCode)
        val serial = parsed?.serialNumber ?: (if (fullCode.length >= 6) fullCode.takeLast(6) else fullCode)
        val isMatch = _serialNumbers.value.contains(serial)
        _lastScannedResult.value = ScanMatchResult(serial, isMatch)
    }

    fun clearResult() {
        _lastScannedResult.value = null
    }

    fun clearList() {
        _serialNumbers.value = emptyList()
        _lastScannedResult.value = null
    }
}
