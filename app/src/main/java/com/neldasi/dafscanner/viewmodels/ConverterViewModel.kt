package com.neldasi.dafscanner.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neldasi.dafscanner.data.AppDatabase
import com.neldasi.dafscanner.data.ConversionRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConverterViewModel(application: Application) : AndroidViewModel(application) {
    private val conversionDao = AppDatabase.getDatabase(application).conversionDao()

    val history: StateFlow<List<ConversionRecord>> = conversionDao.getAllRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addToHistory(hex: String, dec: String) {
        if (hex.isBlank() || dec.isBlank() || dec == "Error") return
        viewModelScope.launch {
            conversionDao.insertRecord(ConversionRecord(hex = hex, dec = dec))
        }
    }

    fun deleteRecord(record: ConversionRecord) {
        viewModelScope.launch {
            conversionDao.deleteRecord(record)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            conversionDao.deleteAll()
        }
    }
}
