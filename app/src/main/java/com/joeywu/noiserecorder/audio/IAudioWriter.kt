package com.joeywu.noiserecorder.audio

import com.joeywu.noiserecorder.data.AudioFormatType
import java.io.File

interface IAudioWriter {
    fun start()
    fun write(buffer: ShortArray, readSize: Int)
    fun stop()

    companion object {
        fun create(file: File, format: AudioFormatType, sampleRate: Int = 44100): IAudioWriter {
            return when (format) {
                AudioFormatType.M4A -> object : IAudioWriter {
                    private val writer = AacM4aWriter(file, sampleRate)
                    override fun start() = writer.start()
                    override fun write(buffer: ShortArray, readSize: Int) = writer.write(buffer, readSize)
                    override fun stop() = writer.stop()
                }
                AudioFormatType.WAV -> object : IAudioWriter {
                    private val writer = WavWriter(file, sampleRate)
                    override fun start() = writer.start()
                    override fun write(buffer: ShortArray, readSize: Int) = writer.write(buffer, readSize)
                    override fun stop() = writer.stop()
                }
            }
        }
    }
}
