package com.joeywu.noiserecorder.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joeywu.noiserecorder.data.AppPreferences
import com.joeywu.noiserecorder.data.AudioFormatType
import com.joeywu.noiserecorder.data.SettingsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appPreferences = AppPreferences(application)

    val settings: StateFlow<SettingsConfig> = appPreferences.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsConfig()
    )

    fun updateThreshold(threshold: Double) {
        viewModelScope.launch {
            appPreferences.updateThreshold(threshold)
        }
    }

    fun updateSilenceTimeout(minutes: Double) {
        viewModelScope.launch {
            appPreferences.updateSilenceTimeout(minutes)
        }
    }

    fun updateAudioFormat(format: AudioFormatType) {
        viewModelScope.launch {
            appPreferences.updateAudioFormat(format)
        }
    }

    suspend fun exportSettings(outputStream: OutputStream): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val current = settings.value
                val jsonString = current.toJsonString()
                outputStream.use { stream ->
                    stream.write(jsonString.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
            }
        }
    }

    suspend fun importSettings(inputStream: InputStream): Result<SettingsConfig> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val jsonString = inputStream.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                val configResult = SettingsConfig.fromJsonString(jsonString)
                val config = configResult.getOrThrow()
                appPreferences.applySettingsConfig(config)
                config
            }
        }
    }
}
