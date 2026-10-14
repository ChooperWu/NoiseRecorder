package com.joeywu.noiserecorder.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileManager(private val context: Context) {

    fun getRecordingsDir(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS) ?: context.filesDir
        val recordingsDir = File(baseDir, "NoiseRecordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        return recordingsDir
    }

    fun loadRecordings(): List<RecordingItem> {
        val dir = getRecordingsDir()
        val files = dir.listFiles { file ->
            file.isFile && (file.extension.equals("m4a", ignoreCase = true) || file.extension.equals("wav", ignoreCase = true))
        } ?: emptyArray()

        return files.sortedByDescending { it.lastModified() }.map { file ->
            val duration = getDurationSafe(file)
            val format = if (file.extension.equals("wav", ignoreCase = true)) {
                AudioFormatType.WAV
            } else {
                AudioFormatType.M4A
            }
            RecordingItem(
                id = file.absolutePath,
                file = file,
                name = file.name,
                durationMs = duration,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                format = format
            )
        }
    }

    private fun getDurationSafe(file: File): Long {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
    }

    fun renameFile(item: RecordingItem, newNameWithoutExt: String): Result<File> {
        val sanitized = newNameWithoutExt.trim()
        if (sanitized.isEmpty()) {
            return Result.failure(IllegalArgumentException("檔名不可為空"))
        }
        val extension = item.file.extension
        val finalName = if (sanitized.endsWith(".$extension", ignoreCase = true)) {
            sanitized
        } else {
            "$sanitized.$extension"
        }
        val targetFile = File(item.file.parentFile, finalName)
        if (targetFile.exists() && targetFile.absolutePath != item.file.absolutePath) {
            return Result.failure(IllegalStateException("目標檔名已存在"))
        }
        val success = item.file.renameTo(targetFile)
        return if (success) {
            Result.success(targetFile)
        } else {
            Result.failure(Exception("改名失敗"))
        }
    }

    fun duplicateFile(item: RecordingItem): Result<File> {
        val baseName = item.file.nameWithoutExtension
        val ext = item.file.extension
        val parent = item.file.parentFile ?: getRecordingsDir()

        var copyIndex = 1
        var candidate = File(parent, "${baseName}_copy.$ext")
        while (candidate.exists()) {
            copyIndex++
            candidate = File(parent, "${baseName}_copy$copyIndex.$ext")
        }

        return try {
            FileInputStream(item.file).use { input ->
                FileOutputStream(candidate).use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(candidate)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteFiles(items: List<RecordingItem>): Int {
        var count = 0
        for (item in items) {
            if (item.file.exists() && item.file.delete()) {
                count++
            }
        }
        return count
    }
}
