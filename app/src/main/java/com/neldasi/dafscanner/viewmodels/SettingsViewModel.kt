package com.neldasi.dafscanner.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neldasi.dafscanner.data.AppDatabase
import com.neldasi.dafscanner.data.ScanRepository
import com.neldasi.dafscanner.extras.ScanStorage
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScanRepository

    init {
        val scanDao = AppDatabase.getDatabase(application).scanDao()
        repository = ScanRepository(scanDao)
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.deleteAll()
            val prefs = ScanStorage.prefs(getApplication())
            ScanStorage.clearAll(prefs)
        }
    }
}
