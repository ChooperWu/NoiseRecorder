package com.joeywu.noiserecorder.data

enum class AudioFormatType(
    val extension: String,
    val mimeType: String,
    val displayName: String
) {
    M4A("m4a", "audio/mp4", "M4A (AAC 標準壓縮)"),
    WAV("wav", "audio/wav", "WAV (無壓縮 PCM)");

    companion object {
        fun fromString(value: String): AudioFormatType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: M4A
        }
    }
}
