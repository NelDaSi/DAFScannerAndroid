package com.neldasi.dafscanner.viewmodels

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neldasi.dafscanner.data.AppDatabase
import com.neldasi.dafscanner.data.ScanRepository
import com.neldasi.dafscanner.extras.ScanStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ScanRepository

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    var isCheckingUpdates by mutableStateOf(false)
        private set

    data class UpdateInfo(
        val latestVersionCode: Int,
        val latestVersionName: String,
        val apkUrl: String,
        val releaseNotes: String? = null
    )

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

    fun checkForUpdates(currentVersionCode: Int) {
        if (isCheckingUpdates) return
        isCheckingUpdates = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Point this to your actual GitHub raw URL for version.json
                // Example: https://raw.githubusercontent.com/username/repo/main/version.json
                val response = URL("https://yourserver.com/scanner/version.json").readText()
                val json = JSONObject(response)
                val latestVersionCode = json.getInt("versionCode")
                val latestVersionName = json.getString("versionName")
                val apkUrl = json.getString("apkUrl")
                val releaseNotes = json.optString("releaseNotes")

                if (latestVersionCode > currentVersionCode) {
                    _updateInfo.value = UpdateInfo(
                        latestVersionCode = latestVersionCode,
                        latestVersionName = latestVersionName,
                        apkUrl = apkUrl,
                        releaseNotes = releaseNotes
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isCheckingUpdates = false
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun downloadAndInstallApk(context: Context, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apkFile = File(context.cacheDir, "update.apk")
                URL(url).openStream().use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Log or show error
                }
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
