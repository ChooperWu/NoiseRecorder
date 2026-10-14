package com.joeywu.noiserecorder

import com.joeywu.noiserecorder.audio.WavWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavWriterTest {

    @Test
    fun testWavHeaderAndDataIntegrity() {
        val tempFile = File.createTempFile("test_audio", ".wav")
        try {
            val writer = WavWriter(tempFile, sampleRate = 44100, channels = 1, bitsPerSample = 16)
            writer.start()

            // 寫入 1000 個 16-bit PCM 採樣
            val samples = ShortArray(1000) { (it % 100).toShort() }
            writer.write(samples, samples.size)
            writer.stop()

            assertTrue(tempFile.exists())
            // 標頭 44 位元組 + 1000 * 2 位元組 = 2044 位元組
            assertEquals(2044L, tempFile.length())

            val bytes = ByteArray(44)
            FileInputStream(tempFile).use { it.read(bytes) }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val riff = String(bytes, 0, 4, Charsets.US_ASCII)
            assertEquals("RIFF", riff)

            val totalDataLen = buf.getInt(4)
            assertEquals(2044 - 8, totalDataLen)

            val wave = String(bytes, 8, 4, Charsets.US_ASCII)
            assertEquals("WAVE", wave)

            val fmt = String(bytes, 12, 4, Charsets.US_ASCII)
            assertEquals("fmt ", fmt)

            val sampleRate = buf.getInt(24)
            assertEquals(44100, sampleRate)

            val dataMarker = String(bytes, 36, 4, Charsets.US_ASCII)
            assertEquals("data", dataMarker)

            val dataLen = buf.getInt(40)
            assertEquals(2000, dataLen)
        } finally {
            tempFile.delete()
        }
    }
}
