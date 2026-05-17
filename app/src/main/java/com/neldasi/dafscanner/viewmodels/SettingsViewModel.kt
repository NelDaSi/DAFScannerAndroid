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

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage = _updateMessage.asStateFlow()

    init {
        val scanDao = AppDatabase.getDatabase(application).scanDao()
        repository = ScanRepository(scanDao)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            when (val result = UpdateManager.checkForUpdates(getApplication())) {
                is UpdateManager.UpdateResult.NewUpdate -> {
                    _updateInfo.value = result.info
                }
                is UpdateManager.UpdateResult.UpToDate -> {
                    _updateMessage.value = "App is up to date"
                }
                is UpdateManager.UpdateResult.Error -> {
                    _updateMessage.value = result.message
                }
            }
            _isCheckingUpdates.value = false
        }
    }

    fun clearUpdateMessage() {
        _updateMessage.value = null
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    fun clearAllData() {
        viewModelScope.launch {
            // 1. Delete all database entries
            repository.deleteAll()
            
            // Delete conversion history
            AppDatabase.getDatabase(getApplication()).conversionDao().deleteAll()
            
            // 2. Clear all SharedPreferences
            val prefs = ScanStorage.prefs(getApplication())
            ScanStorage.clearAll(prefs)
            
            // 3. Delete all files in internal storage (images, etc.)
            val context = getApplication<Application>()
            context.filesDir.listFiles()?.forEach { it.delete() }
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}
