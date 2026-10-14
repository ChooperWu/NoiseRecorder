package com.joeywu.noiserecorder.data

import org.json.JSONObject

data class SettingsConfig(
    val version: Int = 1,
    val thresholdDb: Double = 65.0,
    val silenceTimeoutMinutes: Double = 2.0,
    val audioFormat: AudioFormatType = AudioFormatType.M4A,
    val exportTimestamp: Long = System.currentTimeMillis()
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("version", version)
        obj.put("thresholdDb", thresholdDb)
        obj.put("silenceTimeoutMinutes", silenceTimeoutMinutes)
        obj.put("audioFormat", audioFormat.name)
        obj.put("exportTimestamp", exportTimestamp)
        return obj.toString(2)
    }

    companion object {
        fun fromJsonString(jsonString: String): Result<SettingsConfig> {
            return runCatching {
                val obj = JSONObject(jsonString)
                val version = obj.optInt("version", 1)
                val threshold = obj.getDouble("thresholdDb")
                val timeout = obj.getDouble("silenceTimeoutMinutes")
                val formatStr = obj.optString("audioFormat", AudioFormatType.M4A.name)
                val timestamp = obj.optLong("exportTimestamp", System.currentTimeMillis())

                // 數值防呆與範圍檢核
                val clampedThreshold = threshold.coerceIn(30.0, 120.0)
                val clampedTimeout = timeout.coerceIn(0.1, 60.0)
                val format = AudioFormatType.fromString(formatStr)

                SettingsConfig(
                    version = version,
                    thresholdDb = clampedThreshold,
                    silenceTimeoutMinutes = clampedTimeout,
                    audioFormat = format,
                    exportTimestamp = timestamp
                )
            }
        }
    }
}
