package com.joeywu.noiserecorder.data

import java.io.File

data class RecordingItem(
    val id: String,
    val file: File,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val lastModified: Long,
    val format: AudioFormatType
) {
    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format("%.2f MB", mb)
            } else {
                String.format("%.1f KB", kb)
            }
        }

    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}
