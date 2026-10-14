package com.joeywu.noiserecorder.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AacM4aWriter(
    private val outputFile: File,
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 1,
    private val bitRate: Int = 64000
) {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var audioTrackIndex: Int = -1
    private var muxerStarted = false
    private var presentationTimeUs: Long = 0
    private val bufferInfo = MediaCodec.BufferInfo()

    fun start() {
        presentationTimeUs = 0
        muxerStarted = false
        audioTrackIndex = -1

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        codec = encoder

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun write(buffer: ShortArray, readSize: Int) {
        val encoder = codec ?: return
        val currentMuxer = muxer ?: return
        if (readSize <= 0) return

        // 1. 將 PCM 寫入 Encoder
        val inputIndex = encoder.dequeueInputBuffer(10000)
        if (inputIndex >= 0) {
            val inputBuffer = encoder.getInputBuffer(inputIndex)
            inputBuffer?.clear()
            val byteCount = readSize * 2
            val bytes = ByteArray(byteCount)
            val byteBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until readSize) {
                byteBuf.putShort(buffer[i])
            }
            inputBuffer?.put(bytes)
            encoder.queueInputBuffer(inputIndex, 0, byteCount, presentationTimeUs, 0)
            presentationTimeUs += (readSize.toDouble() / sampleRate * 1_000_000).toLong()
        }

        // 2. 從 Encoder 取出編碼好的 AAC 資料寫入 Muxer
        drainEncoder(false)
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val encoder = codec ?: return
        val currentMuxer = muxer ?: return

        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) {
                    break
                }
                val newFormat = encoder.outputFormat
                audioTrackIndex = currentMuxer.addTrack(newFormat)
                currentMuxer.start()
                muxerStarted = true
            } else if (outIndex >= 0) {
                val encodedData = encoder.getOutputBuffer(outIndex)
                if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    currentMuxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                }
                encoder.releaseOutputBuffer(outIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    fun stop() {
        try {
            val encoder = codec
            if (encoder != null) {
                // 發送 EOS
                val inputIndex = encoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drainEncoder(true)
                encoder.stop()
                encoder.release()
            }
        } catch (_: Exception) {}
        codec = null

        try {
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
        } catch (_: Exception) {}
        muxer = null
        muxerStarted = false
    }
}
