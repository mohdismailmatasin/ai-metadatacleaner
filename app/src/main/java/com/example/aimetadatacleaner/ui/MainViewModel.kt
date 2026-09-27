package com.example.aimetadatacleaner.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aimetadatacleaner.MetaCleanApplication
import com.example.aimetadatacleaner.data.database.CleanedRecordEntity
import com.example.aimetadatacleaner.data.model.CleanExecutionResult
import com.example.aimetadatacleaner.data.model.CleaningOptions
import com.example.aimetadatacleaner.data.model.ImageInspectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppThemeMode(val title: String, val subtitle: String) {
    SYSTEM("System Default", "Follows device dark/light setting"),
    LIGHT("Light Mode", "Crisp daylight high-contrast theme"),
    DARK("Dark Mode", "Deep slate OLED privacy dark theme")
}

data class BatchProgressState(
    val isRunning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val completedResults: List<CleanExecutionResult> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MetaCleanApplication).repository
    private val prefs = application.getSharedPreferences("metaclean_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        try {
            AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name)
        } catch (_: Exception) {
            AppThemeMode.DARK
        }
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
        showToast("Theme changed to ${mode.title}")
    }

    val historyRecords: StateFlow<List<CleanedRecordEntity>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalRecordsCount: StateFlow<Int> = repository.totalRecordsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalTagsRemoved: StateFlow<Int> = repository.totalTagsRemoved
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri: StateFlow<Uri?> = _selectedUri.asStateFlow()

    private val _inspectionResult = MutableStateFlow<ImageInspectionResult?>(null)
    val inspectionResult: StateFlow<ImageInspectionResult?> = _inspectionResult.asStateFlow()

    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _cleaningOptions = MutableStateFlow(CleaningOptions())
    val cleaningOptions: StateFlow<CleaningOptions> = _cleaningOptions.asStateFlow()

    private val _isCleaning = MutableStateFlow(false)
    val isCleaning: StateFlow<Boolean> = _isCleaning.asStateFlow()

    private val _cleanResult = MutableStateFlow<CleanExecutionResult?>(null)
    val cleanResult: StateFlow<CleanExecutionResult?> = _cleanResult.asStateFlow()

    private val _batchUris = MutableStateFlow<List<Uri>>(emptyList())
    val batchUris: StateFlow<List<Uri>> = _batchUris.asStateFlow()

    private val _batchState = MutableStateFlow(BatchProgressState())
    val batchState: StateFlow<BatchProgressState> = _batchState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun selectImage(uri: Uri) {
        _selectedUri.value = uri
        _cleanResult.value = null
        inspectCurrentUri(uri)
    }

    private fun inspectCurrentUri(uri: Uri) {
        viewModelScope.launch {
            _isInspecting.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.inspectImage(uri)
                }
                _inspectionResult.value = result
            } catch (e: Exception) {
                _toastMessage.value = "Failed to read image metadata: ${e.message}"
            } finally {
                _isInspecting.value = false
            }
        }
    }

    fun updateOptions(transform: (CleaningOptions) -> CleaningOptions) {
        _cleaningOptions.value = transform(_cleaningOptions.value)
    }

    fun cleanCurrentImage() {
        val uri = _selectedUri.value ?: return
        viewModelScope.launch {
            _isCleaning.value = true
            try {
                val result = repository.cleanSingleImage(uri, _cleaningOptions.value)
                _cleanResult.value = result
                if (result.success) {
                    _toastMessage.value = "Image cleaned! ${result.tagsRemovedCount} metadata tags stripped."
                } else {
                    _toastMessage.value = "Cleaning failed: ${result.errorMessage}"
                }
            } catch (e: Exception) {
                _toastMessage.value = "Error: ${e.message}"
            } finally {
                _isCleaning.value = false
            }
        }
    }

    fun saveCleanedToGallery(filePath: String?) {
        if (filePath == null) return
        viewModelScope.launch {
            val savedUri = repository.saveToGallery(filePath)
            if (savedUri != null) {
                _toastMessage.value = "Saved to Pictures/MetaClean in Gallery!"
            } else {
                _toastMessage.value = "Could not save to gallery."
            }
        }
    }

    fun shareImage(uri: Uri?, fileName: String) {
        if (uri == null) return
        repository.shareImage(uri, fileName)
    }

    fun setBatchUris(uris: List<Uri>) {
        _batchUris.value = uris
        _batchState.value = BatchProgressState(total = uris.size)
    }

    fun startBatchCleaning() {
        val uris = _batchUris.value
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _batchState.value = BatchProgressState(isRunning = true, current = 0, total = uris.size)
            val results = mutableListOf<CleanExecutionResult>()

            for ((index, uri) in uris.withIndex()) {
                val res = repository.cleanSingleImage(uri, _cleaningOptions.value)
                results.add(res)
                _batchState.value = BatchProgressState(
                    isRunning = true,
                    current = index + 1,
                    total = uris.size,
                    completedResults = results.toList()
                )
            }

            _batchState.value = BatchProgressState(
                isRunning = false,
                current = uris.size,
                total = uris.size,
                completedResults = results.toList()
            )
            _toastMessage.value = "Batch completed! Cleaned ${results.count { it.success }} images."
        }
    }

    fun deleteHistoryRecord(id: Long, filePath: String?) {
        viewModelScope.launch {
            repository.deleteRecord(id, filePath)
            _toastMessage.value = "Record deleted."
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _toastMessage.value = "History cleared."
        }
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun dismissToast() {
        _toastMessage.value = null
    }

    fun dismissCleanResult() {
        _cleanResult.value = null
    }
}
