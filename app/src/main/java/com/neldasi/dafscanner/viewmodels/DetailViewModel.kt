package com.neldasi.dafscanner.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neldasi.dafscanner.data.AppDatabase
import com.neldasi.dafscanner.data.ScannedPart
import com.neldasi.dafscanner.data.ScanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScanRepository
    private val _part = MutableStateFlow<ScannedPart?>(null)
    val part: StateFlow<ScannedPart?> = _part

    init {
        val scanDao = AppDatabase.getDatabase(application).scanDao()
        repository = ScanRepository(scanDao)
    }

    fun loadPart(fullCode: String) {
        viewModelScope.launch {
            _part.value = repository.getPartByCode(fullCode)
        }
    }

    fun updateNote(note: String) {
        val currentPart = _part.value ?: return
        viewModelScope.launch {
            val updated = currentPart.copy(note = note)
            repository.update(updated)
            _part.value = updated
        }
    }

    fun updateImage(uri: String?) {
        val currentPart = _part.value ?: return
        viewModelScope.launch {
            val updated = currentPart.copy(imageUri = uri)
            repository.update(updated)
            _part.value = updated
        }
    }
}
