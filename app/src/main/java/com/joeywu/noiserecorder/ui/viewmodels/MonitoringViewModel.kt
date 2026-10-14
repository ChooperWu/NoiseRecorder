package com.joeywu.noiserecorder.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joeywu.noiserecorder.data.AppPreferences
import com.joeywu.noiserecorder.data.SettingsConfig
import com.joeywu.noiserecorder.service.AudioMonitoringService
import com.joeywu.noiserecorder.service.MonitoringStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MonitoringViewModel(application: Application) : AndroidViewModel(application) {

    private val appPreferences = AppPreferences(application)

    val status: StateFlow<MonitoringStatus> = AudioMonitoringService.status
    val decibel: StateFlow<Double> = AudioMonitoringService.decibel
    val remainingSilenceSeconds: StateFlow<Int> = AudioMonitoringService.remainingSilenceSeconds

    val settings: StateFlow<SettingsConfig> = appPreferences.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsConfig()
    )

    fun toggleMonitoring() {
        val app = getApplication<Application>()
        if (AudioMonitoringService.isRunning()) {
            val stopIntent = Intent(app, AudioMonitoringService::class.java).apply {
                action = AudioMonitoringService.ACTION_STOP_MONITORING
            }
            app.startService(stopIntent)
        } else {
            val startIntent = Intent(app, AudioMonitoringService::class.java).apply {
                action = AudioMonitoringService.ACTION_START_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(startIntent)
            } else {
                app.startService(startIntent)
            }
        }
    }
}
