package com.joeywu.noiserecorder

import com.joeywu.noiserecorder.data.AudioFormatType
import com.joeywu.noiserecorder.data.SettingsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsConfigTest {

    @Test
    fun testSerializationAndDeserialization() {
        val original = SettingsConfig(
            version = 1,
            thresholdDb = 72.5,
            silenceTimeoutMinutes = 3.5,
            audioFormat = AudioFormatType.WAV,
            exportTimestamp = 1700000000000L
        )

        val json = original.toJsonString()
        assertTrue(json.contains("\"thresholdDb\": 72.5"))
        assertTrue(json.contains("\"audioFormat\": \"WAV\""))

        val parsed = SettingsConfig.fromJsonString(json).getOrThrow()
        assertEquals(original.version, parsed.version)
        assertEquals(72.5, parsed.thresholdDb, 0.001)
        assertEquals(3.5, parsed.silenceTimeoutMinutes, 0.001)
        assertEquals(AudioFormatType.WAV, parsed.audioFormat)
        assertEquals(1700000000000L, parsed.exportTimestamp)
    }

    @Test
    fun testClampingInvalidValues() {
        val invalidJson = """
            {
                "version": 1,
                "thresholdDb": 10.0,
                "silenceTimeoutMinutes": 120.0,
                "audioFormat": "UNKNOWN_FORMAT"
            }
        """.trimIndent()

        val parsed = SettingsConfig.fromJsonString(invalidJson).getOrThrow()
        // 閾值限制在 30.0 ~ 120.0
        assertEquals(30.0, parsed.thresholdDb, 0.001)
        // 停止持續時間限制在 0.1 ~ 60.0
        assertEquals(60.0, parsed.silenceTimeoutMinutes, 0.001)
        // 未知格式回退至 M4A
        assertEquals(AudioFormatType.M4A, parsed.audioFormat)
    }
}
