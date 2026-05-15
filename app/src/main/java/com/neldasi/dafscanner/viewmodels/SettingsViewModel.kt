package com.neldasi.dafscanner.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neldasi.dafscanner.data.AppDatabase
import com.neldasi.dafscanner.data.ScanRepository
import com.neldasi.dafscanner.extras.ScanStorage
import com.neldasi.dafscanner.extras.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScanRepository

    private val _updateInfo = MutableStateFlow<UpdateManager.ReleaseInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates = _isCheckingUpdates.asStateFlow()

    init {
        val scanDao = AppDatabase.getDatabase(application).scanDao()
        repository = ScanRepository(scanDao)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            _updateInfo.value = UpdateManager.checkForUpdates(getApplication())
            _isCheckingUpdates.value = false
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.deleteAll()
            val prefs = ScanStorage.prefs(getApplication())
            ScanStorage.clearAll(prefs)
        }
    }
}
