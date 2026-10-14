package com.joeywu.noiserecorder.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noise_recorder_settings")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_THRESHOLD_DB = doublePreferencesKey("threshold_db")
        val KEY_SILENCE_TIMEOUT_MINUTES = doublePreferencesKey("silence_timeout_minutes")
        val KEY_AUDIO_FORMAT = stringPreferencesKey("audio_format")

        const val DEFAULT_THRESHOLD_DB = 65.0
        const val DEFAULT_SILENCE_TIMEOUT_MINUTES = 2.0
        val DEFAULT_AUDIO_FORMAT = AudioFormatType.M4A
    }

    val settingsFlow: Flow<SettingsConfig> = context.dataStore.data.map { preferences ->
        val threshold = preferences[KEY_THRESHOLD_DB] ?: DEFAULT_THRESHOLD_DB
        val timeout = preferences[KEY_SILENCE_TIMEOUT_MINUTES] ?: DEFAULT_SILENCE_TIMEOUT_MINUTES
        val formatStr = preferences[KEY_AUDIO_FORMAT] ?: DEFAULT_AUDIO_FORMAT.name

        SettingsConfig(
            thresholdDb = threshold,
            silenceTimeoutMinutes = timeout,
            audioFormat = AudioFormatType.fromString(formatStr)
        )
    }

    suspend fun updateThreshold(threshold: Double) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THRESHOLD_DB] = threshold.coerceIn(30.0, 120.0)
        }
    }

    suspend fun updateSilenceTimeout(minutes: Double) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SILENCE_TIMEOUT_MINUTES] = minutes.coerceIn(0.1, 60.0)
        }
    }

    suspend fun updateAudioFormat(format: AudioFormatType) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUDIO_FORMAT] = format.name
        }
    }

    suspend fun applySettingsConfig(config: SettingsConfig) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THRESHOLD_DB] = config.thresholdDb
            preferences[KEY_SILENCE_TIMEOUT_MINUTES] = config.silenceTimeoutMinutes
            preferences[KEY_AUDIO_FORMAT] = config.audioFormat.name
        }
    }
}
