package com.joeywu.noiserecorder.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joeywu.noiserecorder.audio.AudioPlayerManager
import com.joeywu.noiserecorder.data.FileManager
import com.joeywu.noiserecorder.data.RecordingItem
import com.joeywu.noiserecorder.service.AudioMonitoringService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RecordingsViewModel(application: Application) : AndroidViewModel(application) {

    private val fileManager = FileManager(application)
    val playerManager = AudioPlayerManager(application)

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    val currentPlayingItem = playerManager.currentPlayingItem
    val isPlaying = playerManager.isPlaying
    val currentPositionMs = playerManager.currentPositionMs
    val durationMs = playerManager.durationMs

    init {
        loadRecordings()
        // 當有新檔案存檔時自動重新載入
        viewModelScope.launch {
            AudioMonitoringService.fileSavedEvent.collect {
                loadRecordings()
            }
        }
    }

    fun loadRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = fileManager.loadRecordings()
            _recordings.value = list
            // 清理已不存在於清單中的選取項目
            val existingIds = list.map { it.id }.toSet()
            _selectedIds.value = _selectedIds.value.filter { it in existingIds }.toSet()
            if (_selectedIds.value.isEmpty()) {
                _isMultiSelectMode.value = false
            }
        }
    }

    fun toggleSelectionMode(enable: Boolean) {
        _isMultiSelectMode.value = enable
        if (!enable) {
            _selectedIds.value = emptySet()
        }
    }

    fun toggleItemSelection(id: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedIds.value = current
        if (current.isEmpty()) {
            _isMultiSelectMode.value = false
        } else {
            _isMultiSelectMode.value = true
        }
    }

    fun selectAll() {
        _selectedIds.value = _recordings.value.map { it.id }.toSet()
        _isMultiSelectMode.value = true
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun deleteSelected(onComplete: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val selected = _recordings.value.filter { it.id in _selectedIds.value }
            // 若正在播放被刪除的檔案，先停止播放
            val playing = currentPlayingItem.value
            if (playing != null && _selectedIds.value.contains(playing.id)) {
                withContext(Dispatchers.Main) {
                    playerManager.stop()
                }
            }
            val count = fileManager.deleteFiles(selected)
            withContext(Dispatchers.Main) {
                clearSelection()
                loadRecordings()
                onComplete(count)
            }
        }
    }

    fun deleteSingle(item: RecordingItem, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (currentPlayingItem.value?.id == item.id) {
                withContext(Dispatchers.Main) {
                    playerManager.stop()
                }
            }
            fileManager.deleteFiles(listOf(item))
            withContext(Dispatchers.Main) {
                loadRecordings()
                onComplete()
            }
        }
    }

    suspend fun rename(item: RecordingItem, newName: String): Result<File> {
        return withContext(Dispatchers.IO) {
            val result = fileManager.renameFile(item, newName)
            if (result.isSuccess) {
                if (currentPlayingItem.value?.id == item.id) {
                    withContext(Dispatchers.Main) {
                        playerManager.stop()
                    }
                }
                loadRecordings()
            }
            result
        }
    }

    suspend fun duplicate(item: RecordingItem): Result<File> {
        return withContext(Dispatchers.IO) {
            val result = fileManager.duplicateFile(item)
            if (result.isSuccess) {
                loadRecordings()
            }
            result
        }
    }

    fun play(item: RecordingItem) {
        playerManager.play(item)
    }

    fun pause() {
        playerManager.pause()
    }

    fun resume() {
        playerManager.resume()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    override fun onCleared() {
        playerManager.release()
        super.onCleared()
    }
}
