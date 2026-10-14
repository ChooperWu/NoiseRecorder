package com.joeywu.noiserecorder.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavWriter(
    private val outputFile: File,
    private val sampleRate: Int = 44100,
    private val channels: Short = 1,
    private val bitsPerSample: Short = 16
) {
    private var fos: FileOutputStream? = null
    private var totalAudioLen: Long = 0

    fun start() {
        fos = FileOutputStream(outputFile)
        // 寫入 44 位元組的佔位標頭
        val placeholderHeader = ByteArray(44)
        fos?.write(placeholderHeader)
        totalAudioLen = 0
    }

    fun write(buffer: ShortArray, readSize: Int) {
        if (fos == null || readSize <= 0) return
        val byteBuf = ByteBuffer.allocate(readSize * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until readSize) {
            byteBuf.putShort(buffer[i])
        }
        val bytes = byteBuf.array()
        fos?.write(bytes)
        totalAudioLen += bytes.size
    }

    fun stop() {
        try {
            fos?.flush()
            fos?.close()
        } catch (_: Exception) {}
        fos = null

        // 回填正確的 RIFF 與 data 長度
        updateWavHeader()
    }

    private fun updateWavHeader() {
        if (!outputFile.exists() || totalAudioLen <= 0) return
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt((totalDataLen and 0xffffffffL).toInt())
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16) // Subchunk1Size for PCM
        buf.putShort(1.toShort()) // AudioFormat 1 = PCM
        buf.putShort(channels)
        buf.putInt(sampleRate)
        buf.putInt(byteRate.toInt())
        buf.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
        buf.putShort(bitsPerSample)

        // data chunk
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt((totalAudioLen and 0xffffffffL).toInt())

        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(0)
                raf.write(header)
            }
        } catch (_: Exception) {}
    }
}
